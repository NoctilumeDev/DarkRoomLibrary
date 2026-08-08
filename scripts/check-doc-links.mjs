import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const ignoredDirectories = new Set([".git", "coverage", "dist", "node_modules", "release", "target"]);
const linkPattern = /!?\[[^\]]*\]\((?<target><[^>]+>|[^\s)]+)(?:\s+"[^"]*")?\)/gu;

async function listMarkdownFiles(root) {
  const files = [];
  for (const entry of await fs.readdir(root, { withFileTypes: true })) {
    if (entry.isDirectory() && ignoredDirectories.has(entry.name)) {
      continue;
    }
    const entryPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listMarkdownFiles(entryPath));
    } else if (entry.name.endsWith(".md")) {
      files.push(entryPath);
    }
  }
  return files;
}

const markdownFiles = await listMarkdownFiles(repositoryRoot);
const violations = [];
let relativeLinkCount = 0;

for (const markdownPath of markdownFiles) {
  const source = await fs.readFile(markdownPath, "utf8");
  for (const match of source.matchAll(linkPattern)) {
    let target = match.groups.target.replace(/^<|>$/gu, "");
    if (/^(?:https?:|mailto:|#)/u.test(target)) {
      continue;
    }
    target = target.split("#", 1)[0];
    if (!target) {
      continue;
    }
    try {
      target = decodeURIComponent(target);
    } catch {
      violations.push(`${path.relative(repositoryRoot, markdownPath)}: malformed link ${target}`);
      continue;
    }
    relativeLinkCount += 1;
    const resolved = path.resolve(path.dirname(markdownPath), target);
    try {
      await fs.access(resolved);
    } catch {
      violations.push(
        `${path.relative(repositoryRoot, markdownPath)}: missing relative link ${target}`,
      );
    }
  }
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exitCode = 1;
} else {
  console.log(
    `Documentation links passed for ${markdownFiles.length} Markdown files and ${relativeLinkCount} relative links.`,
  );
}
