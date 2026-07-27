import fs from "node:fs/promises";
import { getAccount, getConcurrentReaderPassword } from "./test-accounts.mjs";

const apiBaseUrls = Object.freeze([
  process.env.E2E_PRIMARY_API_BASE_URL ||
    "http://localhost:20606/api/dark-room-library/v1",
  process.env.E2E_SECONDARY_API_BASE_URL ||
    "http://localhost:20607/api/dark-room-library/v1",
]);
const validationDate = "2026-07-26";
const outputDir = "test-results/multi-instance";
const runKey = process.env.E2E_MULTI_INSTANCE_RUN_KEY || String(Date.now()).slice(-7);
const readerPassword = getConcurrentReaderPassword();
const burstSize = 32;

const identities = Object.freeze({
  root: getAccount("root"),
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
    database: "dark_room_library_e2e",
  },
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
  return payload.data.token;
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
  return { ...identity, id: user.id, role: 2 };
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
        description: "Two application instances sharing MySQL, Redis, and RabbitMQ.",
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

async function runDuplicateBorrowAndReturn(rootToken, reader) {
  const bookName = `MI-duplicate-${runKey}`;
  const book = await createBook(rootToken, bookName, 2, 2);
  const borrowResults = await runBurst(burstSize, (index) =>
    apiRequest(
      alternatingBase(index),
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
      alternatingBase(index),
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
  const results = await runBurst(25, (index) => {
    const reader = readers[index % readers.length];
    return apiRequest(
      alternatingBase(index),
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
  const borrowers = readers.slice(0, 2);
  const reservers = readers.slice(2, 5);

  for (let index = 0; index < borrowers.length; index += 1) {
    requireSuccess(
      await apiRequest(
        alternatingBase(index),
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
        alternatingBase(index),
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
      alternatingBase(index),
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
        alternatingBase(status),
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
      alternatingBase(index),
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
  const rootToken = await login(identities.root, apiBaseUrls[0]);
  const readers = [];
  for (let index = 0; index < 5; index += 1) {
    const account = `mi_reader_${runKey}_${index}`;
    const identity = await createUser(rootToken, {
      account,
      name: `MI Reader ${runKey} ${index}`,
      password: readerPassword,
      email: `${account}@darkroomlibrary.local`,
    });
    readers.push({
      ...identity,
      token: await login(identity, alternatingBase(index)),
    });
  }
  const purchaserToken = await login(identities.purchaser, apiBaseUrls[1]);
  const logisticsToken = await login(identities.logistics, apiBaseUrls[0]);
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
    readerAccounts: readers.map((reader) => reader.account),
    purchaserAccount: identities.purchaser.account,
    logisticsAccount: identities.logistics.account,
  };

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
