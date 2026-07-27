import fs from "node:fs/promises";
import { getAccount, getConcurrentReaderPassword } from "./test-accounts.mjs";

const defaultApiBaseUrls = [
  process.env.E2E_PRIMARY_API_BASE_URL ||
    "http://localhost:20606/api/dark-room-library/v1",
  process.env.E2E_SECONDARY_API_BASE_URL ||
    "http://localhost:20607/api/dark-room-library/v1",
];
const apiBaseUrls = Object.freeze(
  (process.env.E2E_API_BASE_URLS
    ? process.env.E2E_API_BASE_URLS.split(",")
    : defaultApiBaseUrls
  )
    .map((url) => url.trim().replace(/\/$/, ""))
    .filter(Boolean)
);
const validationDate = "2026-07-27";
const outputDir = "test-results/multi-instance";
const requestedRunKey =
  process.env.E2E_MULTI_INSTANCE_RUN_KEY || String(Date.now()).slice(-8);
const runKey = normalizeRunKey(requestedRunKey);
const readerPassword = getConcurrentReaderPassword();
const burstSize = Number(process.env.E2E_MULTI_INSTANCE_BURST_SIZE || 32);
const readerCount = Number(process.env.E2E_MULTI_INSTANCE_READER_COUNT || 5);
const distributionMode =
  process.env.E2E_MULTI_INSTANCE_DISTRIBUTION || "balanced";
const randomSeed = resolveRandomSeed(
  process.env.E2E_MULTI_INSTANCE_RANDOM_SEED || requestedRunKey
);
let randomState = randomSeed;

assertConfiguration();

const identities = Object.freeze({
  root: getAccount("root"),
  coordinator: getAccount("coordinator"),
  admin: getAccount("admin"),
  reader: getAccount("reader"),
  purchaser: getAccount("purchaser"),
  logistics: getAccount("logistics"),
});

const report = {
  validationDate,
  runKey,
  apiBaseUrls,
  startedAt: new Date().toISOString(),
  configuration: {
    instances: apiBaseUrls.length,
    burstSize,
    readerCount,
    distributionMode,
    randomSeed,
    database: "dark_room_library_e2e",
  },
  scenarios: [],
};

await fs.mkdir(outputDir, { recursive: true });

function normalizeRunKey(value) {
  const normalized = String(value).replaceAll(/[^a-zA-Z0-9_]/g, "").slice(-8);
  return normalized || String(Date.now()).slice(-8);
}

function resolveRandomSeed(value) {
  const numeric = Number(value);
  if (Number.isSafeInteger(numeric) && numeric > 0) {
    return numeric >>> 0 || 1;
  }
  let hash = 2166136261;
  for (const character of String(value)) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0 || 1;
}

function assertConfiguration() {
  if (apiBaseUrls.length < 2) {
    throw new Error("Multi-instance verification requires at least two API base URLs.");
  }
  if (new Set(apiBaseUrls).size !== apiBaseUrls.length) {
    throw new Error("API base URLs must be unique.");
  }
  if (!Number.isSafeInteger(burstSize) || burstSize < 2 || burstSize > 500) {
    throw new Error("E2E_MULTI_INSTANCE_BURST_SIZE must be an integer from 2 to 500.");
  }
  if (!Number.isSafeInteger(readerCount) || readerCount < 5 || readerCount > 100) {
    throw new Error("E2E_MULTI_INSTANCE_READER_COUNT must be an integer from 5 to 100.");
  }
  if (!["balanced", "random"].includes(distributionMode)) {
    throw new Error(
      "E2E_MULTI_INSTANCE_DISTRIBUTION must be balanced or random."
    );
  }
}

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

async function apiRequest(baseUrl, token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const startedAt = Date.now();
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      throw new Error(
        `${method} ${path} returned invalid JSON: ${text.slice(0, 200)}`
      );
    }
  }
  return {
    baseUrl,
    method,
    path,
    httpStatus: response.status,
    payload,
    durationMs: Date.now() - startedAt,
  };
}

function requireSuccess(result, label) {
  assert(
    result.httpStatus >= 200 &&
      result.httpStatus < 300 &&
      result.payload?.code === 200,
    `${label} failed: ${JSON.stringify(result)}`
  );
  return result.payload;
}

async function login(identity, baseUrl) {
  const captcha = await apiRequest(baseUrl, null, "/captcha/generate");
  requireSuccess(captcha, `captcha for ${identity.account}`);
  const result = await apiRequest(baseUrl, null, "/user/login", {
    method: "POST",
    body: {
      userAccount: identity.account,
      userPwd: identity.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
  const payload = requireSuccess(result, `login ${identity.account}`);
  assert(
    Number(payload.data.role) === identity.role,
    `${identity.account} returned role ${payload.data.role}`
  );
  const token = payload.data.token;
  const profile = requireSuccess(
    await apiRequest(baseUrl, token, "/user/auth"),
    `auth ${identity.account}`
  ).data;
  assert(
    Number(profile.userRole) === identity.role &&
      Boolean(profile.isCoordinatorAdmin) === identity.isCoordinatorAdmin,
    `${identity.account} returned unexpected permission profile: ${JSON.stringify(profile)}`
  );
  return token;
}

async function queryOne(token, path, body, label) {
  const result = await apiRequest(apiBaseUrls[0], token, path, {
    method: "POST",
    body,
  });
  const payload = requireSuccess(result, label);
  const rows = payload.data || [];
  assert(rows.length === 1, `${label} expected one row, received ${rows.length}`);
  return rows[0];
}

async function createUser(rootToken, identity) {
  requireSuccess(
    await apiRequest(apiBaseUrls[0], rootToken, "/user/insert", {
      method: "POST",
      body: {
        userName: identity.name,
        userAccount: identity.account,
        userPwd: identity.password,
        userEmail: identity.email,
        userRole: 2,
        isCoordinatorAdmin: false,
      },
    }),
    `create user ${identity.account}`
  );
  const user = await queryOne(
    rootToken,
    "/user/query",
    { current: 1, size: 10, userAccount: identity.account },
    `query user ${identity.account}`
  );
  return {
    ...identity,
    id: user.id,
    role: 2,
    isCoordinatorAdmin: false,
  };
}

async function createBook(rootToken, name, totalCount, availableCount) {
  requireSuccess(
    await apiRequest(apiBaseUrls[0], rootToken, "/book/save", {
      method: "POST",
      body: {
        name,
        author: "Multi Instance Lab",
        isbn: "",
        publisher: "Dark Room Test Press",
        category: "文学",
        totalCount,
        availableCount,
        description: `${apiBaseUrls.length} application instances sharing MySQL, Redis, and RabbitMQ.`,
      },
    }),
    `create book ${name}`
  );
  return getBook(rootToken, name);
}

async function getBook(rootToken, name) {
  return queryOne(
    rootToken,
    "/book/query",
    { current: 1, size: 10, name },
    `query book ${name}`
  );
}

function staleBookEditPayload(book) {
  return {
    id: book.id,
    version: book.version,
    name: book.name,
    author: book.author,
    isbn: book.isbn || "",
    publisher: book.publisher || "",
    category: book.category,
    totalCount: book.totalCount,
    availableCount: book.availableCount,
    originalTotalCount: book.totalCount,
    originalAvailableCount: book.availableCount,
    cover: book.cover || "",
    description: `${book.description || ""} cross-instance-stale-edit-check`,
    bookshelfId: book.bookshelfId,
  };
}

async function queryBorrows(rootToken, query) {
  const result = await apiRequest(apiBaseUrls[0], rootToken, "/borrowRecord/query", {
    method: "POST",
    body: { current: 1, size: 100, ...query },
  });
  return requireSuccess(result, "query borrow records").data || [];
}

async function queryReservations(rootToken, query) {
  const result = await apiRequest(
    apiBaseUrls[0],
    rootToken,
    "/bookReservation/query",
    {
      method: "POST",
      body: { current: 1, size: 100, ...query },
    }
  );
  return requireSuccess(result, "query reservations").data || [];
}

async function runBurst(count, task) {
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const jobs = Array.from({ length: count }, (_, index) =>
    (async () => {
      await gate;
      return task(index);
    })()
  );
  release();
  return Promise.all(jobs);
}

function summarize(results) {
  const durations = results
    .map((result) => result.durationMs)
    .sort((left, right) => left - right);
  const businessCodes = {};
  const instanceRequests = {};
  for (const result of results) {
    const businessCode = String(result.payload?.code ?? "none");
    businessCodes[businessCode] = (businessCodes[businessCode] || 0) + 1;
    instanceRequests[result.baseUrl] = (instanceRequests[result.baseUrl] || 0) + 1;
  }
  const successes = results.filter(
    (result) =>
      result.httpStatus >= 200 &&
      result.httpStatus < 300 &&
      result.payload?.code === 200
  ).length;
  const percentileIndex = Math.max(0, Math.ceil(durations.length * 0.95) - 1);
  return {
    requests: results.length,
    successes,
    failures: results.length - successes,
    instanceRequests,
    businessCodes,
    durationMs: {
      min: durations[0] ?? 0,
      average: durations.length
        ? Math.round(
            durations.reduce((sum, value) => sum + value, 0) / durations.length
          )
        : 0,
      p95: durations[percentileIndex] ?? 0,
      max: durations.at(-1) ?? 0,
    },
  };
}

function recordScenario(name, results, verification) {
  const entry = {
    name,
    ...summarize(results),
    verification,
  };
  report.scenarios.push(entry);
  return entry;
}

function alternatingBase(index) {
  return apiBaseUrls[index % apiBaseUrls.length];
}

function nextRandom() {
  randomState =
    (Math.imul(1664525, randomState) + 1013904223) >>> 0;
  return randomState / 4294967296;
}

function randomIndex(length) {
  return Math.floor(nextRandom() * length);
}

function loadBase(index) {
  return distributionMode === "random"
    ? apiBaseUrls[randomIndex(apiBaseUrls.length)]
    : alternatingBase(index);
}

function selectDistinct(items, count) {
  const candidates = [...items];
  if (distributionMode === "random") {
    for (let index = candidates.length - 1; index > 0; index -= 1) {
      const target = randomIndex(index + 1);
      [candidates[index], candidates[target]] = [
        candidates[target],
        candidates[index],
      ];
    }
  }
  return candidates.slice(0, count);
}

function loadSchedule(items, count) {
  const schedule = Array.from(
    { length: count },
    (_, index) => items[index % items.length]
  );
  if (distributionMode === "random") {
    for (let index = schedule.length - 1; index > 0; index -= 1) {
      const target = randomIndex(index + 1);
      [schedule[index], schedule[target]] = [
        schedule[target],
        schedule[index],
      ];
    }
  }
  return schedule;
}

async function runSixIdentityTokenPortability(tokens) {
  const results = [];
  for (const [name, identity] of Object.entries(identities)) {
    for (const baseUrl of apiBaseUrls) {
      const result = await apiRequest(baseUrl, tokens[name], "/user/auth");
      const payload = requireSuccess(
        result,
        `cross-instance auth ${identity.account} via ${baseUrl}`
      );
      assert(
        Number(payload.data.userRole) === identity.role &&
          Boolean(payload.data.isCoordinatorAdmin) === identity.isCoordinatorAdmin,
        `${identity.account} returned unexpected permission profile via ${baseUrl}: ` +
          `${JSON.stringify(payload.data)}`
      );
      results.push(result);
    }
  }
  const summary = recordScenario(
    "cross-instance-six-identity-token-portability",
    results,
    {
      roleCodes: [...new Set(Object.values(identities).map((identity) => identity.role))],
      identities: Object.keys(identities),
      expectedChecks: Object.keys(identities).length * apiBaseUrls.length,
    }
  );
  assert(
    summary.successes === Object.keys(identities).length * apiBaseUrls.length,
    `six-identity token portability failed: ${JSON.stringify(summary)}`
  );
}

async function runAdministratorEditRace(rootToken, coordinatorToken, adminToken) {
  const bookName = `MI-admin-edit-${runKey}`;
  const book = await createBook(rootToken, bookName, 4, 4);
  const administratorTokens = [rootToken, coordinatorToken, adminToken];
  const administratorSchedule = loadSchedule(administratorTokens, burstSize);
  const results = await runBurst(burstSize, (index) => {
    const payload = staleBookEditPayload(book);
    payload.description = `Concurrent management edit ${runKey}-${index}`;
    return apiRequest(
      loadBase(index),
      administratorSchedule[index],
      "/book/update",
      {
        method: "PUT",
        body: payload,
      }
    );
  });
  const finalBook = await getBook(rootToken, bookName);
  const summary = recordScenario(
    "cross-instance-three-admin-identity-edit-race",
    results,
    {
      expectedSuccesses: 1,
      versionBefore: book.version,
      versionAfter: finalBook.version,
      totalCountBefore: book.totalCount,
      totalCountAfter: finalBook.totalCount,
      availableCountBefore: book.availableCount,
      availableCountAfter: finalBook.availableCount,
    }
  );
  assert(
    summary.successes === 1 &&
      Number(finalBook.version) === Number(book.version) + 1 &&
      Number(finalBook.totalCount) === Number(book.totalCount) &&
      Number(finalBook.availableCount) === Number(book.availableCount),
    `administrator edit invariant failed: ${JSON.stringify(summary)}`
  );
}

async function runDuplicateBorrowAndReturn(rootToken, reader) {
  const bookName = `MI-duplicate-${runKey}`;
  const book = await createBook(rootToken, bookName, 2, 2);
  const borrowResults = await runBurst(burstSize, (index) =>
    apiRequest(
      loadBase(index),
      reader.token,
      `/borrowRecord/borrow/${book.id}`,
      { method: "POST" }
    )
  );
  const borrowedBook = await getBook(rootToken, bookName);
  const active = await queryBorrows(rootToken, {
    userId: reader.id,
    bookId: book.id,
    status: false,
  });
  const borrowSummary = recordScenario(
    "cross-instance-duplicate-borrow",
    borrowResults,
    {
      expectedSuccesses: 1,
      finalAvailableCount: borrowedBook.availableCount,
      activeBorrowCount: active.length,
    }
  );
  assert(
    borrowSummary.successes === 1 &&
      Number(borrowedBook.availableCount) === 1 &&
      active.length === 1,
    `cross-instance borrow invariant failed: ${JSON.stringify(borrowSummary)}`
  );

  const returnResults = await runBurst(burstSize, (index) =>
    apiRequest(
      loadBase(index),
      reader.token,
      `/borrowRecord/return/${active[0].id}`,
      { method: "POST" }
    )
  );
  const returnedBook = await getBook(rootToken, bookName);
  const remaining = await queryBorrows(rootToken, {
    userId: reader.id,
    bookId: book.id,
    status: false,
  });
  const returnSummary = recordScenario(
    "cross-instance-duplicate-return",
    returnResults,
    {
      expectedSuccesses: 1,
      finalAvailableCount: returnedBook.availableCount,
      activeBorrowCount: remaining.length,
    }
  );
  assert(
    returnSummary.successes === 1 &&
      Number(returnedBook.availableCount) === 2 &&
      remaining.length === 0,
    `cross-instance return invariant failed: ${JSON.stringify(returnSummary)}`
  );
}

async function runSingleStockCompetition(rootToken, readers) {
  const bookName = `MI-single-stock-${runKey}`;
  const book = await createBook(rootToken, bookName, 1, 1);
  const requestCount = Math.max(25, burstSize);
  const readerSchedule = loadSchedule(readers, requestCount);
  const results = await runBurst(requestCount, (index) => {
    const reader = readerSchedule[index];
    return apiRequest(
      loadBase(index),
      reader.token,
      `/borrowRecord/borrow/${book.id}`,
      { method: "POST" }
    );
  });
  const finalBook = await getBook(rootToken, bookName);
  const active = await queryBorrows(rootToken, {
    bookId: book.id,
    status: false,
  });
  const summary = recordScenario(
    "cross-instance-single-stock-competition",
    results,
    {
      expectedSuccesses: 1,
      finalAvailableCount: finalBook.availableCount,
      activeBorrowCount: active.length,
    }
  );
  assert(
    summary.successes === 1 &&
      Number(finalBook.availableCount) === 0 &&
      active.length === 1,
    `single-stock invariant failed: ${JSON.stringify(summary)}`
  );
}

async function runReservationCapacity(rootToken, readers) {
  const bookName = `MI-reservation-${runKey}`;
  const book = await createBook(rootToken, bookName, 2, 2);
  const participants = selectDistinct(readers, 5);
  const borrowers = participants.slice(0, 2);
  const reservers = participants.slice(2, 5);

  for (let index = 0; index < borrowers.length; index += 1) {
    requireSuccess(
      await apiRequest(
        loadBase(index),
        borrowers[index].token,
        `/borrowRecord/borrow/${book.id}`,
        { method: "POST" }
      ),
      `borrow reservation fixture ${index}`
    );
  }
  for (let index = 0; index < reservers.length; index += 1) {
    requireSuccess(
      await apiRequest(
        loadBase(index),
        reservers[index].token,
        `/bookReservation/reserve/${book.id}`,
        { method: "POST" }
      ),
      `reserve fixture ${index}`
    );
  }

  const activeBorrows = await queryBorrows(rootToken, {
    bookId: book.id,
    status: false,
  });
  assert(activeBorrows.length === 2, "reservation fixture expected two active borrows");
  const returnResults = await runBurst(2, (index) =>
    apiRequest(
      loadBase(index),
      borrowers[index].token,
      `/borrowRecord/return/${
        activeBorrows.find(
          (record) => Number(record.userId) === Number(borrowers[index].id)
        )?.id
      }`,
      { method: "POST" }
    )
  );
  await new Promise((resolve) => setTimeout(resolve, 1500));

  const reservations = await queryReservations(rootToken, { bookId: book.id });
  const notified = reservations.filter((item) => Number(item.status) === 3);
  const waiting = reservations.filter((item) => Number(item.status) === 0);
  const finalBook = await getBook(rootToken, bookName);
  const summary = recordScenario(
    "cross-instance-reservation-capacity",
    returnResults,
    {
      expectedReturnSuccesses: 2,
      availableCount: finalBook.availableCount,
      notifiedReservations: notified.length,
      waitingReservations: waiting.length,
    }
  );
  assert(
    summary.successes === 2 &&
      Number(finalBook.availableCount) === 2 &&
      notified.length === 2 &&
      waiting.length === 1,
    `reservation capacity invariant failed: ${JSON.stringify(summary)}`
  );
}

async function runProcurementIdempotency(
  rootToken,
  purchaserToken,
  logisticsToken,
  logisticsId
) {
  const bookName = `MI-procurement-${runKey}`;
  const book = await createBook(rootToken, bookName, 2, 2);
  requireSuccess(
    await apiRequest(apiBaseUrls[0], rootToken, "/procurement/save", {
      method: "POST",
      body: {
        bookId: book.id,
        requestCount: 3,
        requestNote: "Cross-instance stock application fixture",
      },
    }),
    "create procurement order"
  );
  const order = await queryOne(
    rootToken,
    "/procurement/query",
    { current: 1, size: 20, bookName, status: 0 },
    "query procurement order"
  );
  requireSuccess(
    await apiRequest(
      apiBaseUrls[1],
      purchaserToken,
      `/procurement/claim/${order.id}`,
      { method: "PUT" }
    ),
    "claim procurement order"
  );
  requireSuccess(
    await apiRequest(apiBaseUrls[0], purchaserToken, "/procurement/updateStatus", {
      method: "PUT",
      body: {
        id: order.id,
        status: 2,
        purchaseNote: "Placed during multi-instance verification",
      },
    }),
    "place procurement order"
  );
  requireSuccess(
    await apiRequest(
      apiBaseUrls[1],
      purchaserToken,
      "/procurement/assignLogistics",
      {
        method: "PUT",
        body: { orderId: order.id, userId: logisticsId },
      }
    ),
    "assign logistics"
  );
  for (const status of [1, 2]) {
    requireSuccess(
      await apiRequest(
        loadBase(status),
        logisticsToken,
        "/procurement/updateLogistics",
        {
          method: "PUT",
          body: {
            orderId: order.id,
            status,
            trackingNo: `MI-${validationDate.replaceAll("-", "")}-${runKey}`,
            carrier: "Multi Instance Carrier",
            remark: `Logistics status ${status}`,
          },
        }
      ),
      `update logistics status ${status}`
    );
  }

  const before = await getBook(rootToken, bookName);
  const stockRaceResults = await runBurst(2, (index) =>
    index === 0
      ? apiRequest(
          apiBaseUrls[0],
          rootToken,
          "/book/update",
          {
            method: "PUT",
            body: staleBookEditPayload(before),
          }
        )
      : apiRequest(
          apiBaseUrls[1],
          logisticsToken,
          "/procurement/updateLogistics",
          {
            method: "PUT",
            body: {
              orderId: order.id,
              status: 3,
              trackingNo: `MI-${validationDate.replaceAll("-", "")}-${runKey}`,
              carrier: "Multi Instance Carrier",
              remark: "Warehouse raced with a stale management form",
            },
          }
        )
  );
  const afterStockRace = await getBook(rootToken, bookName);
  const stockRaceSummary = recordScenario(
    "cross-instance-stale-edit-vs-procurement",
    stockRaceResults,
    {
      warehouseSucceeded: stockRaceResults[1].payload?.code === 200,
      staleEditSucceeded: stockRaceResults[0].payload?.code === 200,
      expectedStockDelta: 3,
      totalCountBefore: before.totalCount,
      totalCountAfter: afterStockRace.totalCount,
      availableCountBefore: before.availableCount,
      availableCountAfter: afterStockRace.availableCount,
    }
  );
  assert(
    stockRaceResults[1].payload?.code === 200 &&
      Number(afterStockRace.totalCount) === Number(before.totalCount) + 3 &&
      Number(afterStockRace.availableCount) ===
        Number(before.availableCount) + 3,
    `stale edit/procurement race lost stock: ${JSON.stringify(
      stockRaceSummary
    )}`
  );

  const results = await runBurst(burstSize, (index) =>
    apiRequest(
      loadBase(index),
      logisticsToken,
      "/procurement/updateLogistics",
      {
        method: "PUT",
        body: {
          orderId: order.id,
          status: 3,
          trackingNo: `MI-${validationDate.replaceAll("-", "")}-${runKey}`,
          carrier: "Multi Instance Carrier",
          remark: "Repeated cross-instance warehouse submission",
        },
      }
    )
  );
  const after = await getBook(rootToken, bookName);
  const warehoused = await queryOne(
    rootToken,
    "/procurement/query",
    { current: 1, size: 20, bookName },
    "query warehoused procurement order"
  );
  const summary = recordScenario(
    "cross-instance-procurement-warehouse-idempotency",
    results,
    {
      expectedStockDelta: 3,
      totalCountBefore: afterStockRace.totalCount,
      totalCountAfter: after.totalCount,
      availableCountBefore: afterStockRace.availableCount,
      availableCountAfter: after.availableCount,
      stockApplied: warehoused.stockApplied,
      logisticsStatus: warehoused.logisticsStatus,
    }
  );
  assert(
    summary.successes === burstSize &&
      Number(after.totalCount) === Number(afterStockRace.totalCount) &&
      Number(after.availableCount) === Number(afterStockRace.availableCount) &&
      Boolean(warehoused.stockApplied) &&
      Number(warehoused.logisticsStatus) === 3,
    `procurement invariant failed: ${JSON.stringify(summary)}`
  );
}

async function main() {
  const tokens = {};
  const fixedRoleEntries = Object.entries(identities);
  for (let index = 0; index < fixedRoleEntries.length; index += 1) {
    const [name, identity] = fixedRoleEntries[index];
    tokens[name] = await login(identity, loadBase(index));
  }
  await runSixIdentityTokenPortability(tokens);

  const rootToken = tokens.root;
  const readers = [];
  for (let index = 0; index < readerCount; index += 1) {
    const account = `mi_reader_${runKey}_${index}`;
    const identity = await createUser(rootToken, {
      account,
      name: `MI ${runKey} ${index}`,
      password: readerPassword,
      email: `${account}@darkroomlibrary.local`,
    });
    readers.push({
      ...identity,
      token: await login(identity, loadBase(index)),
    });
  }
  const purchaserToken = tokens.purchaser;
  const logisticsToken = tokens.logistics;
  const logisticsUsers = requireSuccess(
    await apiRequest(
      apiBaseUrls[1],
      rootToken,
      "/user/collaborationUsers?role=4"
    ),
    "query logistics users"
  ).data || [];
  const logistics = logisticsUsers.find(
    (user) => user.userName === "归架沉香"
  );
  assert(logistics, "demo logistics user was not returned");

  report.fixtures = {
    fixedRoleAccounts: Object.fromEntries(
      Object.entries(identities).map(([name, identity]) => [
        name,
        identity.account,
      ])
    ),
    readerAccounts: readers.map((reader) => reader.account),
  };

  await runAdministratorEditRace(rootToken, tokens.coordinator, tokens.admin);
  await runDuplicateBorrowAndReturn(rootToken, readers[0]);
  await runSingleStockCompetition(rootToken, readers);
  await runReservationCapacity(rootToken, readers);
  await runProcurementIdempotency(
    rootToken,
    purchaserToken,
    logisticsToken,
    logistics.id
  );
}

try {
  await main();
  report.completedAt = new Date().toISOString();
  report.status = "passed";
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  console.log(
    `MULTI_INSTANCE_E2E_OK scenarios=${report.scenarios.length} ` +
      `requests=${report.scenarios.reduce((sum, item) => sum + item.requests, 0)}`
  );
} catch (error) {
  report.completedAt = new Date().toISOString();
  report.status = "failed";
  report.error = error instanceof Error ? error.stack : String(error);
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  throw error;
}
