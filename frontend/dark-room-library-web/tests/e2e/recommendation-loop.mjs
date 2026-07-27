import fs from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { getAccount } from "./test-accounts.mjs";

const apiBaseUrls = Object.freeze(
  (
    process.env.E2E_API_BASE_URLS ||
    process.env.E2E_API_BASE_URL ||
    "http://127.0.0.1:20606/api/dark-room-library/v1"
  )
    .split(",")
    .map((url) => url.trim().replace(/\/$/, ""))
    .filter(Boolean)
);
assert(apiBaseUrls.length > 0, "At least one API base URL is required");
const mysqlContainer = process.env.E2E_MYSQL_CONTAINER || "";
const mysqlPassword = process.env.DRL_MYSQL_ROOT_PASSWORD || "DarkRoomMySQL@20606";
const reader = getAccount("reader");
const outputDir = "test-results/recommendation-loop";

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function solveCaptcha(expression) {
  const match = expression.match(/(-?\d+)\s*([+\-xX*×])\s*(-?\d+)/);
  assert(match, `Unsupported captcha expression: ${expression}`);
  const left = Number(match[1]);
  const right = Number(match[3]);
  if (match[2] === "+") return left + right;
  if (match[2] === "-") return left - right;
  return left * right;
}

async function apiRequest(
  token,
  path,
  { method = "GET", body, baseUrl = apiBaseUrls[0] } = {}
) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${path} returned invalid JSON: ${text.slice(0, 200)}`);
  }
  return { httpStatus: response.status, payload };
}

async function requireSuccess(token, path, options) {
  const result = await apiRequest(token, path, options);
  assert(
    result.httpStatus === 200 && result.payload?.code === 200,
    `${options?.method || "GET"} ${path} failed: ${JSON.stringify(result)}`
  );
  return result.payload.data;
}

async function login() {
  const captcha = await requireSuccess(null, "/captcha/generate");
  const data = await requireSuccess(null, "/user/login", {
    method: "POST",
    body: {
      userAccount: reader.account,
      userPwd: reader.password,
      captchaId: captcha.captchaId,
      captchaAnswer: solveCaptcha(captcha.expression),
    },
  });
  assert(Number(data.role) === reader.role, `Unexpected reader role: ${data.role}`);
  return data.token;
}

function mysqlScalar(sql) {
  if (!mysqlContainer) return null;
  const result = spawnSync(
    "docker",
    [
      "exec",
      "-e",
      `MYSQL_PWD=${mysqlPassword}`,
      mysqlContainer,
      "mysql",
      "-uroot",
      "-Nse",
      sql,
      "dark_room_library",
    ],
    { encoding: "utf8", windowsHide: true }
  );
  assert(
    result.status === 0,
    `MySQL verification failed: ${result.stderr || result.stdout}`
  );
  return result.stdout.trim();
}

function assertRecommendationFeed(feed, favoriteIds, label) {
  assert(["CONTENT", "HYBRID"].includes(feed.mode), `${label} mode: ${feed.mode}`);
  assert(feed.personalized === true, `${label} should be personalized`);
  assert(feed.enabled === true, `${label} should be enabled`);
  assert(feed.signalCount >= 3, `${label} signal count: ${feed.signalCount}`);
  assert(feed.items.length >= 3, `${label} item count: ${feed.items.length}`);
  assert(
    feed.items.every((item) => !favoriteIds.has(Number(item.bookId))),
    `${label} contains an existing favorite`
  );
  assert(
    feed.items.every((item) => item.itemId && item.bookId && item.reason?.trim()),
    `${label} contains an untraceable item`
  );
  assert(
    new Set(feed.items.slice(0, 3).map((item) => item.author)).size === 3,
    `${label} top three authors are not diverse`
  );
}

const report = {
  apiBaseUrls,
  startedAt: new Date().toISOString(),
  checks: [],
};
let token;
let addedBookId;

try {
  token = await login();
  const profile = await requireSuccess(token, "/user/auth");
  const initialFavorites = await requireSuccess(token, "/bookFavorite/query", {
    method: "POST",
    body: { current: 1, size: 100, userId: profile.id },
  });
  const favoriteIds = new Set(initialFavorites.map((item) => Number(item.bookId)));
  assert(favoriteIds.size >= 3, `Reader needs at least three favorites, got ${favoriteIds.size}`);

  const initialFeed = await requireSuccess(token, "/recommendation/feed?size=6");
  assertRecommendationFeed(initialFeed, favoriteIds, "initial feed");
  report.checks.push("personalized-content-feed");

  await requireSuccess(token, "/recommendation/history", { method: "DELETE" });
  const concurrentFeeds = await Promise.all(
    Array.from({ length: 8 }, (_, index) =>
      requireSuccess(token, "/recommendation/feed?size=6", {
        baseUrl: apiBaseUrls[index % apiBaseUrls.length],
      })
    )
  );
  const expectedFeed = concurrentFeeds[0];
  const expectedIds = expectedFeed.items.map((item) => item.itemId).join(",");
  assert(
    concurrentFeeds.every(
      (feed) =>
        feed.generatedAt === expectedFeed.generatedAt &&
        feed.items.map((item) => item.itemId).join(",") === expectedIds
    ),
    "Concurrent requests did not reuse one deterministic batch"
  );
  const concurrentBatchCount = mysqlScalar(
    `SELECT COUNT(*) FROM recommendation_batch WHERE user_id = ${Number(profile.id)}`
  );
  if (concurrentBatchCount !== null) {
    assert(
      concurrentBatchCount === "1",
      `Concurrent generation created ${concurrentBatchCount} batches`
    );
  }
  report.checks.push("eight-request-batch-reuse");

  const disabled = await requireSuccess(token, "/recommendation/setting", {
    method: "PUT",
    body: { enabled: false },
  });
  assert(disabled.enabled === false, "Personalization was not disabled");
  const publicFeed = await requireSuccess(token, "/recommendation/feed?size=6");
  assert(publicFeed.mode === "PUBLIC", `Disabled feed mode: ${publicFeed.mode}`);
  assert(publicFeed.personalized === false && publicFeed.enabled === false,
    "Disabled feed still reports personalization");

  const enabled = await requireSuccess(token, "/recommendation/setting", {
    method: "PUT",
    body: { enabled: true },
  });
  assert(enabled.enabled === true, "Personalization was not re-enabled");
  const personalizedFeed = await requireSuccess(token, "/recommendation/feed?size=6");
  assertRecommendationFeed(personalizedFeed, favoriteIds, "restored feed");
  report.checks.push("privacy-toggle-public-fallback");

  const candidate = personalizedFeed.items[0];
  addedBookId = Number(candidate.bookId);
  await requireSuccess(token, `/recommendation/items/${candidate.itemId}/events`, {
    method: "POST",
    body: { eventType: "CLICK" },
  });
  await requireSuccess(token, `/bookFavorite/add/${addedBookId}`, { method: "POST" });
  const favorited = await requireSuccess(token, `/bookFavorite/isFavorited/${addedBookId}`);
  assert(favorited === true, "Recommended book was not favorited");

  const attributedEvents = mysqlScalar(
    `SELECT COUNT(DISTINCT re.event_type)
     FROM recommendation_event re
     JOIN recommendation_item ri ON ri.id = re.item_id
     WHERE re.user_id = ${Number(profile.id)}
       AND ri.book_id = ${addedBookId}
       AND re.event_type IN ('EXPOSE','CLICK','FAVORITE')`
  );
  if (attributedEvents !== null) {
    assert(attributedEvents === "3", `Expected three attribution events, got ${attributedEvents}`);
    report.checks.push("mysql-expose-click-favorite-attribution");
  }

  const afterFavorite = await requireSuccess(token, "/recommendation/feed?size=6");
  assert(
    afterFavorite.items.every((item) => Number(item.bookId) !== addedBookId),
    "New favorite remained in the recommendation feed"
  );
  report.checks.push("favorite-invalidates-and-filters-feed");

  await requireSuccess(token, "/recommendation/history", { method: "DELETE" });
  const derivedRows = mysqlScalar(
    `SELECT
       (SELECT COUNT(*) FROM recommendation_batch WHERE user_id = ${Number(profile.id)}) +
       (SELECT COUNT(*) FROM recommendation_item WHERE user_id = ${Number(profile.id)}) +
       (SELECT COUNT(*) FROM recommendation_event WHERE user_id = ${Number(profile.id)})`
  );
  if (derivedRows !== null) {
    assert(derivedRows === "0", `History clear left ${derivedRows} derived rows`);
    report.checks.push("mysql-history-clear-no-derived-rows");
  }
  const favoriteAfterClear = await requireSuccess(
    token,
    `/bookFavorite/isFavorited/${addedBookId}`
  );
  assert(favoriteAfterClear === true, "History clear deleted the source favorite");
  report.checks.push("history-clear-preserves-source-signals");

  await requireSuccess(token, `/bookFavorite/remove/${addedBookId}`, { method: "POST" });
  addedBookId = undefined;
  const restoredFavorites = await requireSuccess(token, "/bookFavorite/query", {
    method: "POST",
    body: { current: 1, size: 100, userId: profile.id },
  });
  const restoredIds = restoredFavorites.map((item) => Number(item.bookId)).sort((a, b) => a - b);
  assert(
    restoredIds.join(",") === [...favoriteIds].sort((a, b) => a - b).join(","),
    "Fixture favorites were not restored"
  );
  const finalFeed = await requireSuccess(token, "/recommendation/feed?size=6");
  assertRecommendationFeed(finalFeed, favoriteIds, "final feed");
  report.checks.push("fixture-restored-and-feed-regenerated");

  report.mode = finalFeed.mode;
  report.signalCount = finalFeed.signalCount;
  report.itemCount = finalFeed.items.length;
  report.finishedAt = new Date().toISOString();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  console.log(
    `RECOMMENDATION_E2E_OK instances=${apiBaseUrls.length} mode=${report.mode} signals=${report.signalCount} checks=${report.checks.length}`
  );
} finally {
  if (token && addedBookId) {
    const current = await apiRequest(token, `/bookFavorite/isFavorited/${addedBookId}`);
    if (current.payload?.code === 200 && current.payload.data === true) {
      await apiRequest(token, `/bookFavorite/remove/${addedBookId}`, { method: "POST" });
    }
  }
  if (token) {
    await apiRequest(token, "/recommendation/setting", {
      method: "PUT",
      body: { enabled: true },
    });
  }
}
