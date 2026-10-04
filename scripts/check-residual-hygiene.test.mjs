import assert from "node:assert/strict";
import test from "node:test";

import { inspectResidualHygiene } from "./check-residual-hygiene.mjs";

test("keeps current screenshots, product assets, and declared delivery documents", () => {
  const result = inspectResidualHygiene({
    trackedPaths: [
      "docs/images/reader-room.jpg",
      "docs/DarkRoomLibrary-project-overview.pptx",
      "docs/暗室藏书项目复盘.pdf",
      "frontend/dark-room-library-web/src/assets/images/reading-room-day-v2.webp",
    ],
  });
  assert.deepEqual(result.violations, []);
});

test("rejects tracked generated and runtime state", () => {
  const result = inspectResidualHygiene({
    trackedPaths: [
      "backend/dark-room-library-api/target/app.jar",
      "frontend/dark-room-library-web/dist/index.html",
      "backend/dark-room-library-api/upload/avatar.png",
    ],
  });
  assert.deepEqual(result.violations.map(({ code }) => code), [
    "TRACKED_GENERATED_OUTPUT",
    "TRACKED_RUNTIME_STATE",
    "TRACKED_GENERATED_OUTPUT",
  ]);
});

test("rejects ignored and temporary tracked files", () => {
  const result = inspectResidualHygiene({
    trackedPaths: ["debug.log", "docs/README.md.orig"],
    ignoredTrackedPaths: ["debug.log"],
  });
  assert.deepEqual(result.violations.map(({ code }) => code), [
    "TRACKED_IGNORED_ARTIFACT",
    "TRACKED_TEMPORARY_FILE",
  ]);
});
