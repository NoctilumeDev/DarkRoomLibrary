import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const baselinePath = path.join(repositoryRoot, ".github", "verification-baseline.json");
const reportPath = path.join(
  repositoryRoot,
  "backend",
  "dark-room-library-api",
  "target",
  "site",
  "jacoco",
  "jacoco.csv",
);

const baseline = JSON.parse(await fs.readFile(baselinePath, "utf8"));
const report = await fs.readFile(reportPath, "utf8");
const [headerLine, ...dataLines] = report.trim().split(/\r?\n/u);
const headers = headerLine.split(",");
const missedIndex = headers.indexOf("LINE_MISSED");
const coveredIndex = headers.indexOf("LINE_COVERED");

if (missedIndex < 0 || coveredIndex < 0) {
  throw new Error(`JaCoCo CSV is missing line counters: ${reportPath}`);
}

let missed = 0;
let covered = 0;
for (const line of dataLines) {
  const columns = line.split(",");
  missed += Number.parseInt(columns[missedIndex], 10);
  covered += Number.parseInt(columns[coveredIndex], 10);
}

const total = missed + covered;
const displayedRate = Math.floor((covered / total) * 10_000) / 100;
const expected = baseline.backend;
const violations = [];

if (covered !== expected.lineCoverageCovered) {
  violations.push(`Backend covered lines must be ${expected.lineCoverageCovered}, found ${covered}`);
}
if (total !== expected.lineCoverageTotal) {
  violations.push(`Backend total lines must be ${expected.lineCoverageTotal}, found ${total}`);
}
if (displayedRate !== expected.lineCoverage) {
  violations.push(`Backend displayed line coverage must be ${expected.lineCoverage}%, found ${displayedRate}%`);
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Backend coverage matches the public baseline: ${covered}/${total} (${displayedRate}%).`);
}
