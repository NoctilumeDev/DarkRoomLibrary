import fs from "node:fs/promises";
import { chromium } from "playwright-core";
import { getAccount } from "./test-accounts.mjs";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const apiBaseUrl =
  process.env.E2E_API_BASE_URL ||
  "http://localhost:20606/api/dark-room-library/v1";
const apiOrigin = new URL(apiBaseUrl).origin;
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const outputDir = "test-results/release-edge-regressions";
const root = getAccount("root");
const uploadedFileNames = [];

const report = {
  baseUrl,
  apiBaseUrl,
  startedAt: new Date().toISOString(),
  scenarios: [],
};

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function solveCaptcha(expression) {
  const match = expression.match(/(-?\d+)\s*([+\-×*xX])\s*(-?\d+)/);
  if (!match) throw new Error(`Unsupported captcha expression: ${expression}`);
  const left = Number(match[1]);
  const right = Number(match[3]);
  if (match[2] === "+") return left + right;
  if (match[2] === "-") return left - right;
  return left * right;
}

async function jsonRequest(path, { token, method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15_000),
  });
  const text = await response.text();
  return { response, payload: text ? JSON.parse(text) : null };
}

async function login(identity) {
  const captcha = await jsonRequest("/captcha/generate");
  assert(captcha.response.ok && captcha.payload?.code === 200, "Captcha failed");
  const result = await jsonRequest("/user/login", {
    method: "POST",
    body: {
      userAccount: identity.account,
      userPwd: identity.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
  assert(
    result.response.ok && result.payload?.code === 200,
    `Login failed: ${result.payload?.msg}`
  );
  return result.payload.data.token;
}

async function upload(token, name, type, bytes) {
  const form = new FormData();
  form.append("file", new Blob([bytes], { type }), name);
  const response = await fetch(`${apiBaseUrl}/file/upload`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form,
    signal: AbortSignal.timeout(15_000),
  });
  const payload = await response.json();
  assert(response.ok && payload?.code === 200, `Upload failed: ${payload?.msg}`);
  const fileName = new URL(payload.data, apiOrigin).searchParams.get("fileName");
  assert(fileName, "Upload response did not contain a file name");
  uploadedFileNames.push(fileName);
  return { path: payload.data, fileName };
}

async function cleanup(token) {
  for (const fileName of uploadedFileNames) {
    await jsonRequest(`/file/unbound?fileName=${encodeURIComponent(fileName)}`, {
      token,
      method: "DELETE",
    });
  }
}

let browser;
let token;
try {
  token = await login(root);
  const profile = await jsonRequest("/user/auth", { token });
  assert(profile.response.ok && profile.payload?.code === 200, "Profile failed");

  const video = await upload(
    token,
    "temporary-preview.mp4",
    "video/mp4",
    new Uint8Array([0, 0, 0, 8, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d])
  );
  assert(video.path.includes("/file/download"), "Temporary video was made public");
  const anonymousVideo = await fetch(
    `${apiBaseUrl}/file/public?fileName=${encodeURIComponent(video.fileName)}`
  );
  assert(anonymousVideo.status === 403, "Temporary video public access was not rejected");
  const ownerVideo = await fetch(`${apiOrigin}${video.path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert(ownerVideo.ok, "Uploader could not download the temporary video");
  report.scenarios.push({ name: "temporary-video-private", status: "passed" });

  const image = await upload(
    token,
    "temporary-preview.png",
    "image/png",
    new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  );
  assert(image.path.includes("/file/public"), "Temporary image lost public preview");
  const publicImageUrl = `${apiOrigin}${image.path}`;
  const imageResponses = [];
  for (let index = 0; index < 65; index += 1) {
    imageResponses.push((await fetch(publicImageUrl)).status);
  }
  assert(
    imageResponses.every((status) => status === 200),
    `Public image requests returned ${JSON.stringify([...new Set(imageResponses)])}`
  );
  report.scenarios.push({
    name: "temporary-image-public-with-dedicated-limit",
    status: "passed",
    requests: imageResponses.length,
  });

  browser = await chromium.launch({
    executablePath: edgePath,
    headless: true,
  });
  const page = await browser.newPage();
  await page.goto(`${baseUrl}/#/login`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(800);
  await page.evaluate(
    ({ currentToken, currentProfile }) => {
      sessionStorage.setItem("token", currentToken);
      sessionStorage.setItem(
        "userInfo",
        JSON.stringify({
          id: currentProfile.id,
          name: currentProfile.userName,
          email: currentProfile.userEmail,
          url: currentProfile.userAvatar,
          role: Number(currentProfile.userRole),
          isCoordinatorAdmin: Boolean(currentProfile.isCoordinatorAdmin),
        })
      );
    },
    { currentToken: token, currentProfile: profile.payload.data }
  );

  let authRequestAborted = false;
  await page.route("**/user/auth", async (route) => {
    if (!authRequestAborted) {
      authRequestAborted = true;
      await route.abort("connectionfailed");
      return;
    }
    await route.continue();
  });
  await page.goto(`${baseUrl}/#/dashboard`, { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(1500);
  assert(authRequestAborted, "The transient auth failure was not exercised");
  const finalUrl = page.url();
  const retainedToken = await page.evaluate(() => sessionStorage.getItem("token"));
  assert(
    !finalUrl.includes("/login"),
    `Transient auth failure redirected to login: url=${finalUrl}, ` +
      `tokenRetained=${retainedToken === token}`
  );
  assert(retainedToken === token, "Transient auth failure cleared the session token");
  report.scenarios.push({
    name: "transient-auth-failure-retains-session",
    status: "passed",
    finalUrl,
  });
} finally {
  if (browser) await browser.close();
  if (token) await cleanup(token);
  report.finishedAt = new Date().toISOString();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
}

console.log(JSON.stringify(report, null, 2));
