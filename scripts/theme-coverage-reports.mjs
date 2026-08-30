import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const baselinePath = path.join(repositoryRoot, ".github", "verification-baseline.json");
const distRoot = path.resolve(
  process.cwd(),
  process.argv[2] ?? path.join("frontend", "dark-room-library-web", "dist"),
);
const coverageRoot = path.join(distRoot, "coverage");
const themePath = path.join(coverageRoot, "coverage-theme.css");
const baseline = JSON.parse(await fs.readFile(baselinePath, "utf8"));

const themeCss = `
:root {
  color-scheme: light;
  --drl-stage: #d8dcde;
  --drl-stage-deep: #e8e6e0;
  --drl-paper: #f1eee7;
  --drl-paper-light: #f7f4ed;
  --drl-paper-wash: #e4e5e1;
  --drl-ink: #252b2c;
  --drl-ink-soft: #465251;
  --drl-ink-muted: #5d6867;
  --drl-line: rgba(48, 60, 60, 0.14);
  --drl-line-strong: rgba(48, 60, 60, 0.24);
  --drl-seal: #873b34;
  --drl-jade: #42645d;
  --drl-gold: #71562e;
  --drl-night: #17191b;
  --drl-coverage-high: #4f786f;
  --drl-coverage-medium: #8a6b3f;
  --drl-coverage-low: #9a5046;
}

html {
  min-width: 320px;
  background: var(--drl-stage-deep);
}

body {
  min-height: 100vh;
  margin: 0 !important;
  color: var(--drl-ink) !important;
  font-family: "Noto Sans SC", "Microsoft YaHei", "PingFang SC", Arial, sans-serif !important;
  background:
    radial-gradient(ellipse at 12% 2%, rgba(255, 255, 255, 0.7), transparent 32%),
    radial-gradient(ellipse at 88% 14%, rgba(96, 121, 130, 0.13), transparent 34%),
    linear-gradient(180deg, var(--drl-stage) 0%, var(--drl-stage-deep) 100%) !important;
}

.drl-evidence-bar {
  color: #eee5d5;
  border-bottom: 1px solid rgba(238, 229, 213, 0.13);
  background:
    radial-gradient(circle at 9% 0%, rgba(145, 70, 62, 0.24), transparent 28%),
    linear-gradient(115deg, #151716, #1c2221 64%, #17191b);
}

.drl-evidence-inner {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto auto;
  align-items: center;
  width: min(1180px, calc(100% - 40px));
  min-height: 76px;
  margin: 0 auto;
  gap: 24px;
}

.drl-brand {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  color: inherit !important;
  text-decoration: none !important;
}

.drl-brand img {
  width: 42px;
  height: 42px;
  margin-right: 12px;
  border: 1px solid rgba(238, 229, 213, 0.2);
  border-radius: 8px;
}

.drl-brand strong,
.drl-brand small {
  display: block;
}

.drl-brand strong {
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.drl-brand small {
  margin-top: 3px;
  color: rgba(238, 229, 213, 0.64);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 10px;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.drl-report-meta {
  display: flex;
  align-items: center;
  gap: 9px;
  white-space: nowrap;
}

.drl-report-kind {
  color: #e0c899;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
  font-size: 14px;
}

.drl-report-version {
  padding: 4px 7px;
  color: #f5eee0;
  border: 1px solid rgba(212, 124, 108, 0.42);
  border-radius: 3px;
  background: rgba(145, 70, 62, 0.18);
  font-size: 11px;
  font-weight: 700;
}

.drl-evidence-nav {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.drl-evidence-nav a {
  display: inline-flex;
  align-items: center;
  box-sizing: border-box;
  height: 34px;
  padding: 0 10px;
  color: rgba(238, 229, 213, 0.78) !important;
  border-radius: 3px;
  font-size: 12px;
  line-height: 1;
  text-decoration: none !important;
}

.drl-evidence-nav a:hover,
.drl-evidence-nav a[aria-current="page"] {
  color: #fffaf0 !important;
  background: rgba(238, 229, 213, 0.08);
}

.drl-evidence-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1180px, calc(100% - 40px));
  margin: 24px auto 0;
  padding: 12px 15px;
  gap: 18px;
  color: var(--drl-ink-soft);
  border: 1px solid var(--drl-line);
  border-left: 3px solid var(--drl-seal);
  border-radius: 3px;
  background: rgba(247, 244, 237, 0.72);
  font-size: 12px;
  line-height: 1.6;
}

.drl-evidence-context strong {
  margin-right: 8px;
  color: var(--drl-ink);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
  font-size: 13px;
}

.drl-evidence-stat {
  flex: 0 0 auto;
  color: var(--drl-jade);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-weight: 700;
  white-space: nowrap;
}

.drl-report-body {
  width: min(1180px, calc(100% - 40px));
  min-height: 360px;
  margin: 14px auto 36px;
  padding: clamp(22px, 3vw, 38px);
  overflow-x: auto;
  border: 1px solid var(--drl-line);
  border-radius: 4px;
  background:
    linear-gradient(100deg, rgba(112, 90, 55, 0.025), transparent 10%, transparent 90%, rgba(112, 90, 55, 0.025)),
    var(--drl-paper) !important;
  box-shadow: 0 20px 55px rgba(38, 44, 45, 0.13);
}

.drl-report-body h1,
.drl-report-body h2,
.drl-report-body h3 {
  color: var(--drl-ink) !important;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif !important;
  font-weight: 600 !important;
}

.drl-report-body h1 {
  margin: 0 0 24px !important;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--drl-line-strong);
  font-size: clamp(25px, 3vw, 34px) !important;
}

.drl-report-body a {
  color: var(--drl-jade) !important;
  text-decoration-color: rgba(66, 100, 93, 0.36) !important;
  text-underline-offset: 2px;
}

.drl-report-body a:hover {
  color: var(--drl-seal) !important;
}

.drl-report-body table,
.drl-report-body table.coverage {
  width: 100% !important;
  min-width: 720px;
  color: var(--drl-ink) !important;
  border-collapse: collapse !important;
  background: transparent !important;
  font-size: 12px !important;
}

.drl-report-body table th,
.drl-report-body table td,
.drl-report-body table.coverage td {
  border-color: var(--drl-line) !important;
}

.drl-report-body table thead td,
.drl-report-body table thead th,
.drl-report-body table.coverage thead td {
  color: var(--drl-ink) !important;
  background-color: var(--drl-paper-wash) !important;
  font-weight: 700;
}

.drl-report-body table tbody tr:hover td,
.drl-report-body table.coverage tbody tr:hover td {
  background-color: rgba(66, 100, 93, 0.065) !important;
}

.drl-report-body table tfoot td,
.drl-report-body table.coverage tfoot td {
  color: var(--drl-ink) !important;
  background-color: rgba(113, 86, 46, 0.08) !important;
  font-weight: 700;
}

/* Keep coverage meaning, but replace the generators' saturated traffic-light palette. */
.drl-jacoco-track {
  position: relative;
  display: inline-block;
  width: 122px;
  height: 12px;
  overflow: hidden;
  vertical-align: middle;
  border: 1px solid rgba(48, 60, 60, 0.24);
  border-radius: 2px;
  background: var(--drl-coverage-high);
}

.drl-jacoco-segment--missed {
  position: absolute;
  inset: 0 0 0 auto;
  background: var(--drl-coverage-low);
}

.drl-jacoco-track--empty {
  opacity: 0.58;
  background: rgba(37, 43, 44, 0.055);
}

.drl-report-body .status-line.high,
.drl-report-body .high .cover-fill {
  background: var(--drl-coverage-high) !important;
}

.drl-report-body .high .chart {
  border-color: var(--drl-coverage-high) !important;
}

.drl-report-body .high,
.drl-report-body .cline-yes,
.drl-report-body .cstat-yes {
  background: rgba(88, 119, 109, 0.1) !important;
}

.drl-report-body .status-line.medium,
.drl-report-body .medium .cover-fill {
  background: var(--drl-coverage-medium) !important;
}

.drl-report-body .medium .chart {
  border-color: var(--drl-coverage-medium) !important;
}

.drl-report-body .medium,
.drl-report-body .cbranch-no {
  background: rgba(138, 107, 63, 0.13) !important;
}

.drl-report-body .red.solid,
.drl-report-body .status-line.low,
.drl-report-body .low .cover-fill,
.drl-report-body .highlighted,
.drl-report-body .highlighted .cstat-no,
.drl-report-body .highlighted .fstat-no,
.drl-report-body .highlighted .cbranch-no {
  background: var(--drl-coverage-low) !important;
}

.drl-report-body .low .chart {
  border-color: var(--drl-coverage-low) !important;
}

.drl-report-body .low,
.drl-report-body .cline-no,
.drl-report-body .cstat-no,
.drl-report-body .fstat-no {
  background: rgba(145, 72, 62, 0.105) !important;
}

.drl-report-body .cover-empty {
  background: rgba(37, 43, 44, 0.08) !important;
}

.drl-report-body pre.source span.fc {
  background-color: rgba(88, 119, 109, 0.13) !important;
}

.drl-report-body pre.source span.nc {
  background-color: rgba(145, 72, 62, 0.14) !important;
}

.drl-report-body pre.source span.pc {
  background-color: rgba(138, 107, 63, 0.14) !important;
}

.drl-report-body pre.source span.bfc,
.drl-report-body pre.source span.bnc,
.drl-report-body pre.source span.bpc {
  background-image: none !important;
}

.drl-report-body .el_report,
.drl-report-body .el_group,
.drl-report-body .el_bundle,
.drl-report-body .el_package,
.drl-report-body .el_class,
.drl-report-body .el_source,
.drl-report-body .el_method,
.drl-report-body .el_session {
  padding-left: 0 !important;
  background-image: none !important;
}

.drl-report-body table.coverage thead td.sortable,
.drl-report-body .coverage-summary .sorter {
  background-image: none !important;
}

.drl-report-body table.coverage thead td.sortable,
.drl-report-body .coverage-summary th {
  cursor: pointer;
}

.drl-report-body .breadcrumb {
  margin: -8px 0 22px;
  padding: 9px 11px;
  color: var(--drl-ink-muted) !important;
  border: 1px solid var(--drl-line);
  border-radius: 3px;
  background: rgba(228, 229, 225, 0.64) !important;
  font-size: 11px;
}

.drl-report-body .footer {
  margin-top: 28px;
  padding-top: 14px;
  color: var(--drl-ink-muted) !important;
  border-top: 1px solid var(--drl-line);
}

.drl-report-body .wrapper {
  min-height: auto;
}

.drl-report-body .coverage-summary {
  color: var(--drl-ink) !important;
}

.drl-report-body .coverage-summary th {
  background: var(--drl-paper-wash) !important;
}

.drl-report-body .coverage-summary td {
  background: rgba(247, 244, 237, 0.46) !important;
}

.drl-report-body .quiet,
.drl-report-body .fraction,
.drl-report-body .footer.quiet {
  color: var(--drl-ink-muted) !important;
}

.drl-report-body .strong {
  color: var(--drl-ink) !important;
}

.drl-report-body input[type="search"] {
  min-height: 38px;
  padding: 7px 10px;
  color: var(--drl-ink);
  border: 1px solid var(--drl-line-strong);
  border-radius: 3px;
  background: var(--drl-paper-light);
}

.drl-report-body input[type="search"]::placeholder {
  color: var(--drl-ink-muted);
}

a:focus-visible,
[tabindex]:focus-visible {
  outline: 3px solid rgba(135, 59, 52, 0.48) !important;
  outline-offset: 3px !important;
}

.drl-report-body input[type="search"]:focus-visible {
  border-color: rgba(79, 120, 111, 0.62);
  outline: 0 !important;
  box-shadow: 0 0 0 2px rgba(79, 120, 111, 0.14);
}

@media (max-width: 820px) {
  .drl-evidence-inner {
    grid-template-columns: 1fr auto;
    padding: 14px 0;
    gap: 12px 16px;
  }

  .drl-evidence-nav {
    grid-column: 1 / -1;
    justify-content: flex-start;
    overflow-x: auto;
  }

  .drl-evidence-context {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
}

@media (max-width: 560px) {
  .drl-evidence-inner,
  .drl-evidence-context,
  .drl-report-body {
    width: calc(100% - 20px);
  }

  .drl-evidence-inner {
    grid-template-columns: 1fr;
  }

  .drl-report-meta,
  .drl-evidence-nav {
    grid-column: 1;
  }

  .drl-evidence-context {
    margin-top: 14px;
  }

  .drl-report-body {
    margin-top: 10px;
    padding: 18px 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
`;

async function listHtmlFiles(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? listHtmlFiles(entryPath) : entryPath.endsWith(".html") ? [entryPath] : [];
  }));
  return nested.flat();
}

function toHref(fromDirectory, targetPath) {
  return path.relative(fromDirectory, targetPath).split(path.sep).join("/") || ".";
}

function reportDetails(filePath) {
  const relativePath = path.relative(coverageRoot, filePath);
  const isBackend = relativePath === "backend" || relativePath.startsWith(`backend${path.sep}`);
  const backend = baseline.backend;
  const frontend = baseline.frontend;
  return isBackend
    ? {
        label: "后端 · JaCoCo",
        scopeTitle: "后端验证范围",
        scopeText: "Java 模块的 JaCoCo 报告；公开数字采用 XML 根级唯一源码行计数，CI 门禁不低于 70%。",
        stat: `${backend.lineCoverageCovered}/${backend.lineCoverageTotal} · ${backend.lineCoverage}%`,
        current: "backend",
      }
    : {
        label: "前端 · Vitest V8",
        scopeTitle: "前端验证范围",
        scopeText: "关键逻辑的 Vitest V8 报告；Vue 组件、路由守卫与真实页面行为另由组件测试和浏览器 E2E 验证。",
        stat: `${frontend.criticalLogicTests} tests · Lines ${frontend.lineCoverage}%`,
        current: "frontend",
      };
}

function buildJacocoTrack(coverageText) {
  if (coverageText === "n/a") {
    return '<span class="drl-jacoco-track drl-jacoco-track--empty" aria-label="无可用覆盖率"></span>';
  }

  const covered = Number.parseInt(coverageText, 10);
  if (!Number.isInteger(covered) || covered < 0 || covered > 100) {
    throw new Error(`Invalid JaCoCo coverage percentage: ${coverageText}`);
  }

  const missed = 100 - covered;
  const overlay = missed > 0
    ? `<span class="drl-jacoco-segment--missed" style="width:${missed}%" aria-hidden="true" title="未覆盖 ${missed}%"></span>`
    : "";

  return `<span class="drl-jacoco-track" role="img" aria-label="已覆盖 ${covered}%，未覆盖 ${missed}%">${overlay}</span>`;
}

function collectNativeJacocoCoverage(html, filePath) {
  const barCellCount = (html.match(/<td class="bar"[^>]*>(?:<img[^>]+\/>)+<\/td>|<td class="bar"[^>]*\/>/gu) ?? []).length;
  const rows = [...html.matchAll(
    /<td class="bar"([^>]*)>(?:<img[^>]+\/>)+<\/td><td class="ctr2"([^>]*)>(\d+%|n\/a)<\/td>|<td class="bar"([^>]*)\/><td class="ctr2"([^>]*)>(\d+%|n\/a)<\/td>/gu,
  )];
  if (rows.length !== barCellCount) {
    throw new Error(`Could not map every native JaCoCo bar to its percentage in ${filePath}: ${rows.length}/${barCellCount}`);
  }
  return rows.map((match) => match[3] ?? match[6]);
}

function validateThemedJacocoCoverage(html, filePath, expectedCoverage = null) {
  const barCellCount = (html.match(/class="drl-jacoco-track(?:\s|")/gu) ?? []).length;
  const rows = [...html.matchAll(
    /<td class="bar"([^>]*)>(<span class="drl-jacoco-track(?: drl-jacoco-track--empty)?"[^>]*>(?:<span class="drl-jacoco-segment--missed"[^>]*><\/span>)?<\/span>)<\/td><td class="ctr2"([^>]*)>(\d+%|n\/a)<\/td>/gu,
  )];
  if (rows.length !== barCellCount) {
    throw new Error(`Could not validate every themed JaCoCo bar in ${filePath}: ${rows.length}/${barCellCount}`);
  }
  if (/<td class="bar"[^>]*>(?:<img[^>]+\/>)+<\/td>|<td class="bar"[^>]*\/>/gu.test(html)) {
    throw new Error(`Native JaCoCo bars remain after theming ${filePath}`);
  }

  const actualCoverage = rows.map((match) => match[4]);
  if (expectedCoverage && (actualCoverage.length !== expectedCoverage.length
    || actualCoverage.some((value, index) => value !== expectedCoverage[index]))) {
    throw new Error(`JaCoCo coverage order changed while theming ${filePath}`);
  }

  rows.forEach((match, index) => {
    const coverageText = match[4];
    if (match[2] !== buildJacocoTrack(coverageText)) {
      throw new Error(`JaCoCo bar does not match ${coverageText} in ${filePath} at row ${index + 1}`);
    }
  });
}

function buildChrome(filePath, details) {
  const directory = path.dirname(filePath);
  const homeHref = `${toHref(directory, distRoot)}/`;
  const logoHref = toHref(directory, path.join(distRoot, "logo.png"));
  const cssHref = toHref(directory, themePath);
  const backendHref = `${toHref(directory, path.join(coverageRoot, "backend"))}/`;
  const frontendHref = `${toHref(directory, path.join(coverageRoot, "frontend"))}/`;
  const currentAttribute = (name) => details.current === name ? ' aria-current="page"' : "";

  return {
    cssHref,
    header: `<div class="drl-evidence-bar" role="banner" data-drl-coverage-theme="1"><div class="drl-evidence-inner"><a class="drl-brand" href="${homeHref}"><img src="${logoHref}" alt="" width="42" height="42" /><span><strong>暗室藏书</strong><small>Verification Evidence</small></span></a><div class="drl-report-meta"><span class="drl-report-kind">${details.label}</span><span class="drl-report-version">${baseline.targetRelease}</span></div><div class="drl-evidence-nav" role="navigation" aria-label="验证报告导航"><a href="${homeHref}">在线演示</a><a href="${backendHref}"${currentAttribute("backend")}>后端报告</a><a href="${frontendHref}"${currentAttribute("frontend")}>前端报告</a><a href="https://github.com/NoctilumeDev/DarkRoomLibrary">源码仓库</a></div></div></div><div class="drl-evidence-context" role="region" aria-label="报告范围"><span><strong>${details.scopeTitle}</strong>${details.scopeText}</span><span class="drl-evidence-stat">${details.stat}</span></div><div class="drl-report-body" role="main">`,
  };
}

async function themeReport(filePath) {
  let html = await fs.readFile(filePath, "utf8");
  if (html.includes('data-drl-coverage-theme="1"')) {
    if (reportDetails(filePath).current === "backend") {
      validateThemedJacocoCoverage(html, filePath);
    }
    return false;
  }

  const details = reportDetails(filePath);
  const chrome = buildChrome(filePath, details);
  if (details.current === "backend") {
    const nativeCoverage = collectNativeJacocoCoverage(html, filePath);
    html = html.replace(
      /<td class="bar"([^>]*)>(?:<img[^>]+\/>)+<\/td><td class="ctr2"([^>]*)>(\d+%|n\/a)<\/td>/gu,
      (_match, barAttributes, coverageAttributes, coverageText) => `<td class="bar"${barAttributes}>${buildJacocoTrack(coverageText)}</td><td class="ctr2"${coverageAttributes}>${coverageText}</td>`,
    );
    html = html.replace(
      /<td class="bar"([^>]*)\/><td class="ctr2"([^>]*)>(\d+%|n\/a)<\/td>/gu,
      (_match, barAttributes, coverageAttributes, coverageText) => `<td class="bar"${barAttributes}>${buildJacocoTrack(coverageText)}</td><td class="ctr2"${coverageAttributes}>${coverageText}</td>`,
    );
    validateThemedJacocoCoverage(html, filePath, nativeCoverage);
  }
  html = html.replace(/<title>(.*?)<\/title>/iu, `<title>暗室藏书 · $1</title>`);
  if (!/<meta\s+name=["']viewport["']/iu.test(html)) {
    html = html.replace(/<\/head>/iu, '<meta name="viewport" content="width=device-width, initial-scale=1" /></head>');
  }
  html = html.replace(/<\/head>/iu, `<link rel="stylesheet" href="${chrome.cssHref}" /></head>`);
  html = html.replace(/(<body(?:\s[^>]*)?>)/iu, `$1${chrome.header}`);
  html = html.replace(/<\/body>/iu, "</div></body>");
  await fs.writeFile(filePath, html, "utf8");
  return true;
}

const reportDirectories = [path.join(coverageRoot, "backend"), path.join(coverageRoot, "frontend")];
for (const directory of reportDirectories) {
  const info = await fs.stat(directory).catch(() => null);
  if (!info?.isDirectory()) {
    throw new Error(`Coverage report directory does not exist: ${directory}`);
  }
}

await fs.mkdir(coverageRoot, { recursive: true });
await fs.writeFile(themePath, themeCss.trimStart(), "utf8");
const htmlFiles = (await Promise.all(reportDirectories.map(listHtmlFiles))).flat();
const themed = await Promise.all(htmlFiles.map(themeReport));
console.log(`Themed ${themed.filter(Boolean).length}/${htmlFiles.length} coverage report pages.`);
