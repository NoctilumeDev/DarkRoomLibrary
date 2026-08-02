import fs from "node:fs/promises";
import { getAccount, getConcurrentReaderPassword } from "./test-accounts.mjs";

const apiBaseUrl =
  process.env.E2E_API_BASE_URL ||
  "http://localhost:20606/api/dark-room-library/v1";
const validationDate =
  process.env.E2E_VALIDATION_DATE || new Date().toISOString().slice(0, 10);
const outputDir = "test-results/concurrency";
const runKey = process.env.E2E_CONCURRENCY_RUN_KEY || String(Date.now()).slice(-7);
const readerPassword = getConcurrentReaderPassword();
const readerCount = 20;
const burstSize = 32;
const databaseName = process.env.E2E_DATABASE_NAME || "dark_room_library";

const identities = Object.freeze({
  root: getAccount("root"),
  purchaser: getAccount("purchaser"),
  logistics: getAccount("logistics"),
});

const report = {
  validationDate,
  runKey,
  apiBaseUrl,
  startedAt: new Date().toISOString(),
  configuration: {
    readerCount,
    burstSize,
    database: databaseName,
  },
  scenarios: [],
};

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function solveCaptcha(expression) {
  const match = expression.match(/(-?\d+)\s*([+\-×*xX])\s*(-?\d+)/);
  if (!match) {
    throw new Error(`Unsupported captcha expression: ${expression}`);
  }
  const left = Number(match[1]);
  const right = Number(match[3]);
  if (match[2] === "+") return left + right;
  if (match[2] === "-") return left - right;
  return left * right;
}

async function apiRequest(token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const startedAt = Date.now();
  const response = await fetch(`${apiBaseUrl}${path}`, {
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

async function login(identity) {
  const captcha = await apiRequest(null, "/captcha/generate");
  requireSuccess(captcha, `captcha for ${identity.account}`);
  const result = await apiRequest(null, "/user/login", {
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
  const result = await apiRequest(token, path, { method: "POST", body });
  const payload = requireSuccess(result, label);
  const rows = payload.data || [];
  assert(rows.length === 1, `${label} expected one row, received ${rows.length}`);
  return rows[0];
}

async function createUser(rootToken, identity) {
  const result = await apiRequest(rootToken, "/user/insert", {
    method: "POST",
    body: {
      userName: identity.name,
      userAccount: identity.account,
      userPwd: identity.password,
      userEmail: identity.email,
      userRole: identity.role,
      isCoordinatorAdmin: false,
    },
  });
  requireSuccess(result, `create user ${identity.account}`);
  const user = await queryOne(
    rootToken,
    "/user/query",
    { current: 1, size: 10, userAccount: identity.account },
    `query user ${identity.account}`
  );
  return { ...identity, id: user.id };
}

async function createBook(rootToken, name, totalCount, availableCount) {
  const result = await apiRequest(rootToken, "/book/save", {
    method: "POST",
    body: {
      name,
      author: "Concurrency Lab",
      isbn: "",
      publisher: "Dark Room Test Press",
      category: "文学",
      totalCount,
      availableCount,
      description: "Real HTTP and MySQL concurrency fixture.",
    },
  });
  requireSuccess(result, `create book ${name}`);
  return queryOne(
    rootToken,
    "/book/query",
    { current: 1, size: 10, name },
    `query book ${name}`
  );
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
    description: `${book.description || ""} stale-edit-check`,
    bookshelfId: book.bookshelfId,
  };
}

async function queryBorrows(rootToken, query) {
  const result = await apiRequest(rootToken, "/borrowRecord/query", {
    method: "POST",
    body: { current: 1, size: 100, ...query },
  });
  return requireSuccess(result, "query borrow records").data || [];
}

async function queryReservations(rootToken, query) {
  const result = await apiRequest(rootToken, "/bookReservation/query", {
    method: "POST",
    body: { current: 1, size: 100, ...query },
  });
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
  const durations = results.map((result) => result.durationMs).sort((a, b) => a - b);
  const businessCodes = {};
  const httpStatuses = {};
  for (const result of results) {
    const businessCode = String(result.payload?.code ?? "none");
    const httpStatus = String(result.httpStatus);
    businessCodes[businessCode] = (businessCodes[businessCode] || 0) + 1;
    httpStatuses[httpStatus] = (httpStatuses[httpStatus] || 0) + 1;
  }
  const successes = results.filter(
    (result) => result.httpStatus >= 200 &&
      result.httpStatus < 300 &&
      result.payload?.code === 200
  ).length;
  const percentileIndex = Math.max(0, Math.ceil(durations.length * 0.95) - 1);
  return {
    requests: results.length,
    successes,
    failures: results.length - successes,
    httpStatuses,
    businessCodes,
    durationMs: {
      min: durations[0] ?? 0,
      average: durations.length
        ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length)
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

async function runSingleStockBorrowRounds(rootToken, readers) {
  for (let round = 1; round <= 3; round += 1) {
    const bookName = `CC-single-${runKey}-${round}`;
    const book = await createBook(rootToken, bookName, 1, 1);
    const results = await runBurst(readers.length, (index) =>
      apiRequest(readers[index].token, `/borrowRecord/borrow/${book.id}`, {
        method: "POST",
      })
    );
    const finalBook = await getBook(rootToken, bookName);
    const active = await queryBorrows(rootToken, {
      bookId: book.id,
      status: false,
    });
    const summary = recordScenario(
      `single-stock-borrow-round-${round}`,
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
      `single-stock borrow invariant failed: ${JSON.stringify(summary)}`
    );
  }
}

async function runDuplicateBorrowReturnRounds(rootToken, readers) {
  for (let round = 1; round <= 3; round += 1) {
    const reader = readers[round - 1];
    const bookName = `CC-duplicate-${runKey}-${round}`;
    const book = await createBook(rootToken, bookName, 2, 2);
    const borrowResults = await runBurst(burstSize, () =>
      apiRequest(reader.token, `/borrowRecord/borrow/${book.id}`, {
        method: "POST",
      })
    );
    const borrowedBook = await getBook(rootToken, bookName);
    const active = await queryBorrows(rootToken, {
      userId: reader.id,
      bookId: book.id,
      status: false,
    });
    const borrowSummary = recordScenario(
      `duplicate-borrow-round-${round}`,
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
      `duplicate borrow invariant failed: ${JSON.stringify(borrowSummary)}`
    );

    const returnResults = await runBurst(burstSize, () =>
      apiRequest(reader.token, `/borrowRecord/return/${active[0].id}`, {
        method: "POST",
      })
    );
    const returnedBook = await getBook(rootToken, bookName);
    const remainingActive = await queryBorrows(rootToken, {
      userId: reader.id,
      bookId: book.id,
      status: false,
    });
    const returnSummary = recordScenario(
      `duplicate-return-round-${round}`,
      returnResults,
      {
        expectedSuccesses: 1,
        finalAvailableCount: returnedBook.availableCount,
        activeBorrowCount: remainingActive.length,
      }
    );
    assert(
      returnSummary.successes === 1 &&
        Number(returnedBook.availableCount) === 2 &&
        remainingActive.length === 0,
      `duplicate return invariant failed: ${JSON.stringify(returnSummary)}`
    );
  }
}

async function runDuplicateReservationRounds(rootToken, readers) {
  for (let round = 1; round <= 3; round += 1) {
    const reader = readers[round + 2];
    const bookName = `CC-reserve-${runKey}-${round}`;
    const book = await createBook(rootToken, bookName, 0, 0);
    const results = await runBurst(burstSize, () =>
      apiRequest(reader.token, `/bookReservation/reserve/${book.id}`, {
        method: "POST",
      })
    );
    const active = await queryReservations(rootToken, {
      userId: reader.id,
      bookId: book.id,
      status: 0,
    });
    const summary = recordScenario(
      `duplicate-reservation-round-${round}`,
      results,
      {
        expectedSuccesses: 1,
        activeReservationCount: active.length,
      }
    );
    assert(
      summary.successes === 1 && active.length === 1,
      `duplicate reservation invariant failed: ${JSON.stringify(summary)}`
    );
  }
}

async function runCancelBorrowRaces(rootToken, readers) {
  for (let round = 1; round <= 5; round += 1) {
    const reader = readers[round + 5];
    const bookName = `CC-cancel-race-${runKey}-${round}`;
    const book = await createBook(rootToken, bookName, 1, 1);
    const results = await runBurst(2, (index) =>
      index === 0
        ? apiRequest(reader.token, `/borrowRecord/borrow/${book.id}`, {
          method: "POST",
        })
        : apiRequest(reader.token, "/user/cancelAccount", {
          method: "PUT",
        })
    );
    const finalBook = await getBook(rootToken, bookName);
    const active = await queryBorrows(rootToken, {
      userId: reader.id,
      bookId: book.id,
      status: false,
    });
    const borrowSucceeded = results[0].payload?.code === 200;
    const cancelSucceeded = results[1].payload?.code === 200;
    const summary = recordScenario(
      `cancel-vs-borrow-round-${round}`,
      results,
      {
        borrowSucceeded,
        cancelSucceeded,
        finalAvailableCount: finalBook.availableCount,
        activeBorrowCount: active.length,
      }
    );
    const borrowWon =
      borrowSucceeded &&
      !cancelSucceeded &&
      Number(finalBook.availableCount) === 0 &&
      active.length === 1;
    const cancelWon =
      !borrowSucceeded &&
      cancelSucceeded &&
      Number(finalBook.availableCount) === 1 &&
      active.length === 0;
    assert(
      summary.successes === 1 && (borrowWon || cancelWon),
      `cancel/borrow race invariant failed: ${JSON.stringify(summary)}`
    );
  }
}

async function runProcurementRace(
  rootToken,
  purchaserA,
  purchaserB,
  logistics
) {
  const bookName = `CC-procurement-${runKey}`;
  const book = await createBook(rootToken, bookName, 2, 2);
  requireSuccess(
    await apiRequest(rootToken, "/procurement/save", {
      method: "POST",
      body: {
        bookId: book.id,
        requestCount: 3,
        requestNote: "Real concurrency fixture",
      },
    }),
    "create procurement order"
  );
  const order = await queryOne(
    rootToken,
    "/procurement/query",
    { current: 1, size: 20, bookName, status: 0 },
    "query pending procurement order"
  );
  const claimResults = await runBurst(2, (index) =>
    apiRequest(
      index === 0 ? purchaserA.token : purchaserB.token,
      `/procurement/claim/${order.id}`,
      { method: "PUT" }
    )
  );
  const claimSummary = recordScenario(
    "procurement-claim-race",
    claimResults,
    { expectedSuccesses: 1 }
  );
  assert(
    claimSummary.successes === 1,
    `procurement claim invariant failed: ${JSON.stringify(claimSummary)}`
  );
  const purchaser = claimResults[0].payload?.code === 200 ? purchaserA : purchaserB;

  requireSuccess(
    await apiRequest(purchaser.token, "/procurement/updateStatus", {
      method: "PUT",
      body: {
        id: order.id,
        status: 2,
        purchaseNote: "Concurrent claim winner placed the order",
      },
    }),
    "place procurement order"
  );
  requireSuccess(
    await apiRequest(purchaser.token, "/procurement/assignLogistics", {
      method: "PUT",
      body: {
        orderId: order.id,
        userId: logistics.id,
      },
    }),
    "assign logistics"
  );
  for (const status of [1, 2]) {
    requireSuccess(
      await apiRequest(logistics.token, "/procurement/updateLogistics", {
        method: "PUT",
        body: {
          orderId: order.id,
          status,
          trackingNo: `CC-${validationDate.replaceAll("-", "")}-${runKey}`,
          carrier: "Concurrency Carrier",
          remark: `Logistics status ${status}`,
        },
      }),
      `update logistics status ${status}`
    );
  }

  const before = await getBook(rootToken, bookName);
  const warehouseResults = await runBurst(burstSize, () =>
    apiRequest(logistics.token, "/procurement/updateLogistics", {
      method: "PUT",
      body: {
        orderId: order.id,
        status: 3,
        trackingNo: `CC-${validationDate.replaceAll("-", "")}-${runKey}`,
        carrier: "Concurrency Carrier",
        remark: "Repeated concurrent warehouse submission",
      },
    })
  );
  const after = await getBook(rootToken, bookName);
  const warehoused = await queryOne(
    rootToken,
    "/procurement/query",
    { current: 1, size: 20, bookName },
    "query warehoused procurement order"
  );
  const warehouseSummary = recordScenario(
    "procurement-warehouse-idempotency",
    warehouseResults,
    {
      expectedStockDelta: 3,
      totalCountBefore: before.totalCount,
      totalCountAfter: after.totalCount,
      availableCountBefore: before.availableCount,
      availableCountAfter: after.availableCount,
      stockApplied: warehoused.stockApplied,
      logisticsStatus: warehoused.logisticsStatus,
    }
  );
  assert(
    warehouseSummary.successes === burstSize &&
      Number(after.totalCount) === Number(before.totalCount) + 3 &&
      Number(after.availableCount) === Number(before.availableCount) + 3 &&
      Boolean(warehoused.stockApplied) &&
      Number(warehoused.logisticsStatus) === 3,
    `procurement warehouse invariant failed: ${JSON.stringify(warehouseSummary)}`
  );

  const staleEditResult = await apiRequest(rootToken, "/book/update", {
    method: "PUT",
    body: staleBookEditPayload(before),
  });
  const afterStaleEdit = await getBook(rootToken, bookName);
  const staleEditSummary = recordScenario(
    "stale-book-edit-after-procurement",
    [staleEditResult],
    {
      expectedSuccesses: 0,
      totalCountBeforeProcurement: before.totalCount,
      totalCountAfterProcurement: after.totalCount,
      totalCountAfterStaleEdit: afterStaleEdit.totalCount,
      availableCountAfterProcurement: after.availableCount,
      availableCountAfterStaleEdit: afterStaleEdit.availableCount,
    }
  );
  assert(
    staleEditSummary.successes === 0 &&
      Number(afterStaleEdit.totalCount) === Number(after.totalCount) &&
      Number(afterStaleEdit.availableCount) === Number(after.availableCount),
    `stale book edit overwrote procurement stock: ${JSON.stringify(
      staleEditSummary
    )}`
  );

  requireSuccess(
    await apiRequest(purchaser.token, "/procurement/updateStatus", {
      method: "PUT",
      body: {
        id: order.id,
        status: 6,
        purchaseNote: "Concurrency verification completed",
      },
    }),
    "complete procurement order"
  );
}

async function main() {
  const rootToken = await login(identities.root);
  const readerIdentities = [];
  for (let index = 0; index < readerCount; index += 1) {
    const account = `cc_reader_${runKey}_${String(index).padStart(2, "0")}`;
    readerIdentities.push(
      await createUser(rootToken, {
        account,
        name: `CC Reader ${runKey} ${index}`,
        password: readerPassword,
        email: `${account}@darkroomlibrary.local`,
        role: 2,
      })
    );
  }
  const purchaserBIdentity = await createUser(rootToken, {
    account: `cc_buyer_${runKey}`,
    name: `CC Buyer ${runKey}`,
    password: readerPassword,
    email: `cc_buyer_${runKey}@darkroomlibrary.local`,
    role: 3,
  });

  const readers = [];
  for (const identity of readerIdentities) {
    readers.push({
      ...identity,
      token: await login({
        account: identity.account,
        password: identity.password,
        role: 2,
      }),
    });
  }
  const purchaserA = {
    ...identities.purchaser,
    token: await login(identities.purchaser),
  };
  const purchaserB = {
    ...purchaserBIdentity,
    token: await login({
      account: purchaserBIdentity.account,
      password: purchaserBIdentity.password,
      role: 3,
    }),
  };
  const logisticsToken = await login(identities.logistics);
  const logisticsUsers = requireSuccess(
    await apiRequest(rootToken, "/user/collaborationUsers?role=4"),
    "query logistics users"
  ).data || [];
  const logisticsUser = logisticsUsers.find(
    (user) => user.userName === "归架沉香"
  );
  assert(logisticsUser, "demo logistics user was not returned");
  const logistics = {
    ...logisticsUser,
    token: logisticsToken,
  };

  report.fixtures = {
    readerAccounts: readers.map((reader) => reader.account),
    purchaserAccounts: [purchaserA.account, purchaserB.account],
    logisticsAccount: identities.logistics.account,
  };

  await runSingleStockBorrowRounds(rootToken, readers);
  await runDuplicateBorrowReturnRounds(rootToken, readers);
  await runDuplicateReservationRounds(rootToken, readers);
  await runCancelBorrowRaces(rootToken, readers);
  await runProcurementRace(rootToken, purchaserA, purchaserB, logistics);
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
    `CONCURRENCY_E2E_OK scenarios=${report.scenarios.length} ` +
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
