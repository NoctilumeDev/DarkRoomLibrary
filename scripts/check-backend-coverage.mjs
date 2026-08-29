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
  "jacoco.xml",
);

const baseline = JSON.parse(await fs.readFile(baselinePath, "utf8"));
const report = await fs.readFile(reportPath, "utf8");
const lineCounters = [
  ...report.matchAll(/<counter type="LINE" missed="(\d+)" covered="(\d+)"\s*\/>/gu),
];

if (lineCounters.length === 0) {
  throw new Error(`JaCoCo XML is missing line counters: ${reportPath}`);
}

// JaCoCo writes the report-level counters after all package and class counters.
// Using that root counter matches the public HTML report's unique source-line
// total. Summing CSV class rows can double-count a line shared by an outer and
// an anonymous or nested class.
const [, missedText, coveredText] = lineCounters.at(-1);
const missed = Number.parseInt(missedText, 10);
const covered = Number.parseInt(coveredText, 10);

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
