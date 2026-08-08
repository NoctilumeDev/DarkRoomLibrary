import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const backendRoot = path.join(
  repositoryRoot,
  "backend",
  "dark-room-library-api",
  "src",
  "main",
  "java",
);
const frontendDevelopmentEnv = path.join(
  repositoryRoot,
  "frontend",
  "dark-room-library-web",
  ".env.development",
);

async function listFiles(root, extension) {
  const files = [];
  for (const entry of await fs.readdir(root, { withFileTypes: true })) {
    const entryPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      files.push(...await listFiles(entryPath, extension));
    } else if (entry.name.endsWith(extension)) {
      files.push(entryPath);
    }
  }
  return files;
}

const violations = [];
const javaFiles = await listFiles(backendRoot, ".java");

for (const filePath of javaFiles) {
  const relativePath = path.relative(repositoryRoot, filePath).replaceAll("\\", "/");
  const source = await fs.readFile(filePath, "utf8");

  if (relativePath.includes("/controller/")) {
    if (/import org\.darkroomlibrary\.mapper\./u.test(source)) {
      violations.push(`${relativePath}: controllers must not depend on mappers`);
    }
    if (/@Transactional\b/u.test(source)) {
      violations.push(`${relativePath}: transaction boundaries belong in services`);
    }
  }

  if (relativePath.includes("/service/") && /import org\.darkroomlibrary\.controller\./u.test(source)) {
    violations.push(`${relativePath}: services must not depend on controllers`);
  }

  if (/\b(?:TODO|FIXME)\b/u.test(source)) {
    violations.push(`${relativePath}: unfinished marker found`);
  }
}

const developmentEnv = await fs.readFile(frontendDevelopmentEnv, "utf8");
if (!/^VITE_API_BASE_URL=\/api\/dark-room-library\/v1$/mu.test(developmentEnv)) {
  violations.push(
    "frontend development API must use the Vite /api proxy to preserve the CSP same-origin boundary",
  );
}

if (violations.length > 0) {
  console.error(violations.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`Architecture boundaries passed for ${javaFiles.length} Java sources.`);
}
