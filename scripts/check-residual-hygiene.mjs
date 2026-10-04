import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const GENERATED_DIRECTORY_NAMES = new Set([
  "coverage", "dist", "node_modules", "release", "target", "test-results",
]);
const RUNTIME_ROOTS = [
  "backend/dark-room-library-api/upload/",
  "logs/",
  "tmp/",
  "upload/",
];
const TEMPORARY_SUFFIXES = [".bak", ".orig", ".rej", ".tmp", "~"];

function normalizePath(value) {
  return value.replaceAll("\\", "/").replace(/^\.\//u, "");
}

function uniqueSorted(values) {
  return [...new Set(values.map(normalizePath))].sort();
}

export function inspectResidualHygiene({ trackedPaths, ignoredTrackedPaths = [] }) {
  const tracked = uniqueSorted(trackedPaths);
  const ignored = new Set(uniqueSorted(ignoredTrackedPaths));
  const violations = [];

  for (const file of tracked) {
    const segments = file.split("/");
    if (ignored.has(file)) {
      violations.push({ code: "TRACKED_IGNORED_ARTIFACT", path: file });
    }
    if (segments.some((segment) => GENERATED_DIRECTORY_NAMES.has(segment))) {
      violations.push({ code: "TRACKED_GENERATED_OUTPUT", path: file });
    }
    if (RUNTIME_ROOTS.some((root) => file.startsWith(root))) {
      violations.push({ code: "TRACKED_RUNTIME_STATE", path: file });
    }
    if (TEMPORARY_SUFFIXES.some((suffix) => file.endsWith(suffix))) {
      violations.push({ code: "TRACKED_TEMPORARY_FILE", path: file });
    }
  }

  return { trackedFiles: tracked.length, violations };
}

function gitPaths(repositoryRoot, ...arguments_) {
  const result = spawnSync("git", ["-C", repositoryRoot, ...arguments_], {
    encoding: "buffer",
    windowsHide: true,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`git ${arguments_.join(" ")} failed: ${result.stderr.toString("utf8").trim()}`);
  }
  return result.stdout.toString("utf8").split("\0").filter(Boolean);
}

export function inspectRepository(repositoryRoot) {
  return inspectResidualHygiene({
    trackedPaths: gitPaths(repositoryRoot, "ls-files", "-z"),
    ignoredTrackedPaths: gitPaths(repositoryRoot, "ls-files", "-ci", "--exclude-standard", "-z"),
  });
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
  const result = inspectRepository(repositoryRoot);
  if (result.violations.length > 0) {
    console.error("Residual Hygiene: FAIL");
    for (const violation of result.violations) {
      console.error(`- ${violation.code}: ${violation.path}`);
    }
    process.exitCode = 1;
  } else {
    console.log("Residual Hygiene: PASS");
    console.log(`tracked_files=${result.trackedFiles}`);
    console.log("tracked_ignored_artifacts=0");
    console.log("tracked_generated_outputs=0");
    console.log("tracked_runtime_state=0");
    console.log("tracked_temporary_files=0");
  }
}
