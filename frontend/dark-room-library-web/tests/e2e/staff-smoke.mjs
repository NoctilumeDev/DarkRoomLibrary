import fs from "node:fs/promises";
import { chromium } from "playwright-core";

const token = process.env.E2E_TOKEN;
const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

if (!token) throw new Error("E2E_TOKEN is required");

const outputDir = "test-results/staff";
await fs.mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ executablePath: edgePath, headless: true });

async function openWorkbench(viewport) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript((value) => sessionStorage.setItem("token", value), token);
  const page = await context.newPage();
  await page.goto(`${baseUrl}/#/procurementWorkbench`, { waitUntil: "networkidle" });
  await page.locator(".staff-shell .procurement-page").waitFor();
  return { context, page };
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth
  );
  if (overflow > 1) throw new Error(`${label} has ${overflow}px horizontal overflow`);
}

async function assertOperationAlignment(page) {
  const operationCells = page.locator("td.operation-column");
  const cellCount = await operationCells.count();
  for (let cellIndex = 0; cellIndex < cellCount; cellIndex += 1) {
    const operationCell = operationCells.nth(cellIndex);
    const backgroundColor = await operationCell.evaluate(
      (element) => getComputedStyle(element).backgroundColor
    );
    if (backgroundColor === "rgba(0, 0, 0, 0)" || backgroundColor === "transparent") {
      throw new Error(`staff operation column row ${cellIndex + 1} is transparent`);
    }

    const buttons = operationCell.locator(
      ".cell > .el-button, .cell > .el-badge .el-button"
    );
    const count = await buttons.count();
    if (count < 2) continue;

    const centers = [];
    for (let buttonIndex = 0; buttonIndex < count; buttonIndex += 1) {
      const box = await buttons.nth(buttonIndex).boundingBox();
      if (box) centers.push(box.y + box.height / 2);
    }
    if (Math.max(...centers) - Math.min(...centers) > 2) {
      throw new Error(
        `staff operation labels in row ${cellIndex + 1} are not aligned: ${centers.join(", ")}`
      );
    }
  }
}

try {
  const desktop = await openWorkbench({ width: 1440, height: 1000 });
  await assertNoHorizontalOverflow(desktop.page, "staff workbench desktop");
  await assertOperationAlignment(desktop.page);
  if ((await desktop.page.locator(".summary-strip > div").count()) !== 4) {
    throw new Error("staff workbench summary should contain four metrics");
  }
  await desktop.page.screenshot({
    path: `${outputDir}/workbench-desktop.png`,
    fullPage: true,
  });
  await desktop.context.close();

  const mobile = await openWorkbench({ width: 430, height: 932 });
  await assertNoHorizontalOverflow(mobile.page, "staff workbench mobile");
  await mobile.page.locator(".mobile-order-list").waitFor();
  if (await mobile.page.locator(".order-table").isVisible()) {
    throw new Error("desktop order table should be hidden on mobile");
  }
  await mobile.page.screenshot({
    path: `${outputDir}/workbench-mobile.png`,
    fullPage: true,
  });
  await mobile.context.close();
} finally {
  await browser.close();
}

console.log("Staff E2E smoke test passed.");
