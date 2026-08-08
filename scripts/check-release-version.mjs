import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const requestedTag = process.argv[2];
const semverTag = /^v(?<version>\d+\.\d+\.\d+)$/u;
const baselinePath = path.join(repositoryRoot, ".github", "verification-baseline.json");
const baseline = JSON.parse(await fs.readFile(baselinePath, "utf8"));
const match = semverTag.exec(baseline.targetRelease);
const violations = [];

if (!match) {
  violations.push(`Invalid target release: ${baseline.targetRelease}`);
} else {
  const version = match.groups.version;
  if (requestedTag && requestedTag !== baseline.targetRelease) {
    violations.push(`Tag ${requestedTag} does not match ${baseline.targetRelease}`);
  }
  if (!["release-candidate", "released"].includes(baseline.status)) {
    violations.push(`Unsupported verification status: ${baseline.status}`);
  }
  if (requestedTag && baseline.status !== "released") {
    violations.push(`Release tag requires status released, found ${baseline.status}`);
  }

  const pomPath = path.join(repositoryRoot, "backend", "dark-room-library-api", "pom.xml");
  const pom = (await fs.readFile(pomPath, "utf8")).replace(/<parent>[\s\S]*?<\/parent>/u, "");
  const pomVersion = /<artifactId>dark-room-library-api<\/artifactId>\s*<version>([^<]+)<\/version>/u.exec(pom)?.[1]?.trim();
  if (pomVersion !== version) {
    violations.push(`Backend version must be ${version}, found ${pomVersion ?? "missing"}`);
  }

  const packagePath = path.join(repositoryRoot, "frontend", "dark-room-library-web", "package.json");
  const packageManifest = JSON.parse(await fs.readFile(packagePath, "utf8"));
  if (packageManifest.version !== version) {
    violations.push(`Frontend version must be ${version}, found ${packageManifest.version}`);
  }

  const changelog = await fs.readFile(path.join(repositoryRoot, "CHANGELOG.md"), "utf8");
  if (!changelog.includes(`## [${version}]`)) {
    violations.push(`CHANGELOG.md must include ${version}`);
  }

  const notesPath = path.join(repositoryRoot, ".github", "release-notes", `${baseline.targetRelease}.md`);
  try {
    await fs.access(notesPath);
  } catch {
    violations.push(`Missing release notes for ${baseline.targetRelease}`);
  }
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exitCode = 1;
} else {
  console.log("Release version boundary is current.");
}
