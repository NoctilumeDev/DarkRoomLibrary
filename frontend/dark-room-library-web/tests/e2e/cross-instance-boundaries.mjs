import fs from "node:fs/promises";
import { getAccount } from "./test-accounts.mjs";

const apiBaseUrls = (
  process.env.E2E_API_BASE_URLS ||
  [
    "http://localhost:20606/api/dark-room-library/v1",
    "http://localhost:20607/api/dark-room-library/v1",
    "http://localhost:20608/api/dark-room-library/v1",
  ].join(",")
)
  .split(",")
  .map((value) => value.trim().replace(/\/$/, ""))
  .filter(Boolean);

if (apiBaseUrls.length < 3) {
  throw new Error("Cross-instance boundary verification requires three API base URLs.");
}

const validationDate =
  process.env.E2E_VALIDATION_DATE || new Date().toISOString().slice(0, 10);
const runKey = `${validationDate.replaceAll("-", "")}-${Date.now().toString(36)}`;
const outputDir = "test-results/cross-instance-boundaries";
const sharedPassword = process.env.DRL_DEMO_PASSWORD;
const initialPassword = `Boundary@${runKey.slice(-8)}`;
const changedPassword = `Changed@${runKey.slice(-8)}`;
const temporaryAccount = `drl_boundary_${Date.now().toString(36)}`.slice(0, 32);
const temporaryName = `边界验收${Date.now().toString(36).slice(-6)}`;
const temporaryEmail = `${temporaryAccount}@darkroomlibrary.local`;
const orderNote = `Cross-instance boundary ${runKey}`;

const identities = Object.freeze({
  root: getAccount("root"),
  purchaser: getAccount("purchaser"),
  logistics: getAccount("logistics"),
});

const report = {
  validationDate,
  startedAt: new Date().toISOString(),
  apiBaseUrls,
  temporaryAccount,
  scenarios: [],
  requests: 0,
};

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function record(name, details = {}) {
  report.scenarios.push({ name, status: "passed", ...details });
}

function base(index) {
  return apiBaseUrls[index % apiBaseUrls.length];
}

async function apiRequest(apiBaseUrl, token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  report.requests += 1;
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${path} returned invalid JSON: ${text.slice(0, 200)}`);
  }
  return { response, payload };
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

async function loginRaw(apiBaseUrl, account, password) {
  const captcha = await apiRequest(apiBaseUrl, null, "/captcha/generate");
  assert(
    captcha.response.ok && captcha.payload?.code === 200,
    `Captcha generation failed for ${account}`
  );
  return apiRequest(apiBaseUrl, null, "/user/login", {
    method: "POST",
    body: {
      userAccount: account,
      userPwd: password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
}

async function login(apiBaseUrl, identity, expectedRole = identity.role) {
  const result = await loginRaw(apiBaseUrl, identity.account, identity.password);
  assert(
    result.response.ok && result.payload?.code === 200,
    `${identity.account} login failed: ${result.payload?.msg || result.response.status}`
  );
  assert(
    Number(result.payload.data.role) === Number(expectedRole),
    `${identity.account} returned role ${result.payload.data.role}, expected ${expectedRole}`
  );
  return result.payload.data.token;
}

async function requireSuccess(apiBaseUrl, token, path, options) {
  const result = await apiRequest(apiBaseUrl, token, path, options);
  assert(
    result.response.ok && result.payload?.code === 200,
    `${options?.method || "GET"} ${path} failed: ${
      result.payload?.msg || result.response.status
    }`
  );
  return result.payload;
}

async function requireRejected(apiBaseUrl, token, path, options) {
  const result = await apiRequest(apiBaseUrl, token, path, options);
  assert(
    !result.response.ok || result.payload?.code !== 200,
    `${options?.method || "GET"} ${path} unexpectedly succeeded`
  );
  return result.payload;
}

async function queryUser(rootToken, account) {
  const payload = await requireSuccess(base(1), rootToken, "/user/query", {
    method: "POST",
    body: { current: 1, size: 20, userAccount: account },
  });
  const user = (payload.data || []).find((item) => item.userAccount === account);
  assert(user, `User ${account} was not found`);
  return user;
}

async function genericLoginError(apiBaseUrl, account, password) {
  const result = await loginRaw(apiBaseUrl, account, password);
  assert(
    result.payload?.code !== 200,
    `Login unexpectedly succeeded for ${account}`
  );
  return result.payload?.msg;
}

async function runAuthenticationBoundaries(rootToken) {
  await requireSuccess(base(1), rootToken, "/user/insert", {
    method: "POST",
    body: {
      userName: temporaryName,
      userAccount: temporaryAccount,
      userPwd: initialPassword,
      userEmail: temporaryEmail,
      userRole: 2,
      isCoordinatorAdmin: false,
    },
  });
  const temporaryUser = await queryUser(rootToken, temporaryAccount);
  report.temporaryUserId = temporaryUser.id;

  const readerIdentity = {
    account: temporaryAccount,
    password: initialPassword,
    role: 2,
  };
  const originalToken = await login(base(0), readerIdentity);
  await requireSuccess(base(2), originalToken, "/user/auth");

  await requireSuccess(base(1), originalToken, "/user/updatePwd", {
    method: "PUT",
    body: {
      oldPwd: initialPassword,
      newPwd: changedPassword,
      againPwd: changedPassword,
    },
  });
  await requireRejected(base(2), originalToken, "/user/auth");

  const changedIdentity = {
    account: temporaryAccount,
    password: changedPassword,
    role: 2,
  };
  const passwordToken = await login(base(2), changedIdentity);
  await requireSuccess(base(1), rootToken, "/user/backUpdate", {
    method: "PUT",
    body: { id: temporaryUser.id, userRole: 3 },
  });
  await requireRejected(base(0), passwordToken, "/user/auth");

  const purchaserToken = await login(base(0), changedIdentity, 3);
  await requireSuccess(base(2), purchaserToken, "/user/auth");
  await requireSuccess(base(1), rootToken, "/user/backUpdate", {
    method: "PUT",
    body: { id: temporaryUser.id, userRole: 2 },
  });
  await requireRejected(base(2), purchaserToken, "/user/auth");

  const wrongPasswordMessage = await genericLoginError(
    base(0),
    temporaryAccount,
    `${changedPassword}x`
  );
  const missingAccountMessage = await genericLoginError(
    base(1),
    `missing_${Date.now().toString(36)}`,
    changedPassword
  );

  await requireSuccess(base(1), rootToken, `/user/freeze/${temporaryUser.id}`, {
    method: "PUT",
  });
  const frozenMessage = await genericLoginError(
    base(2),
    temporaryAccount,
    changedPassword
  );
  await requireSuccess(base(0), rootToken, `/user/unfreeze/${temporaryUser.id}`, {
    method: "PUT",
  });

  await requireSuccess(base(1), rootToken, "/user/backUpdate", {
    method: "PUT",
    body: { id: temporaryUser.id, isLogin: true },
  });
  const disabledMessage = await genericLoginError(
    base(0),
    temporaryAccount,
    changedPassword
  );
  await requireSuccess(base(1), rootToken, "/user/backUpdate", {
    method: "PUT",
    body: { id: temporaryUser.id, isLogin: false },
  });

  const messages = [
    wrongPasswordMessage,
    missingAccountMessage,
    frozenMessage,
    disabledMessage,
  ];
  assert(
    messages.every((message) => message === messages[0]),
    `Login errors expose account state: ${JSON.stringify(messages)}`
  );
  assert(
    messages[0] === "登录失败，请检查账号凭据或联系管理员",
    `Unexpected generic login message: ${messages[0]}`
  );

  record("cross-instance-auth-version-invalidation", {
    passwordChange: "20606 token -> 20607 password change -> 20608 rejection",
    roleChange: "20608 token -> 20607 role change -> 20606 rejection",
  });
  record("generic-login-errors", {
    variants: ["wrong-password", "missing-account", "frozen", "disabled"],
    message: messages[0],
  });

  return temporaryUser.id;
}

async function runProcurementBoundaries(rootToken, purchaserToken, logisticsToken) {
  const purchaser = await queryUser(rootToken, identities.purchaser.account);
  const logistics = await queryUser(rootToken, identities.logistics.account);
  const books = await requireSuccess(base(0), rootToken, "/book/query", {
    method: "POST",
    body: { current: 1, size: 20, deleted: false },
  });
  const book = (books.data || [])[0];
  assert(book?.id, "No active book is available for procurement verification");

  await requireSuccess(base(0), rootToken, "/procurement/save", {
    method: "POST",
    body: {
      bookId: book.id,
      requestCount: 1,
      purchaserId: purchaser.id,
      requestNote: orderNote,
    },
  });
  const orders = await requireSuccess(base(1), rootToken, "/procurement/query", {
    method: "POST",
    body: { current: 1, size: 100, bookId: book.id, status: 0 },
  });
  const order = (orders.data || []).find((item) => item.requestNote === orderNote);
  assert(order?.id, "Temporary procurement order was not found");
  report.temporaryOrderId = order.id;

  await requireSuccess(base(1), purchaserToken, `/procurement/claim/${order.id}`, {
    method: "PUT",
  });
  await requireSuccess(base(2), purchaserToken, "/procurement/updateStatus", {
    method: "PUT",
    body: { id: order.id, status: 2, purchaseNote: "Boundary verification placed" },
  });
  const directShipping = await requireRejected(
    base(0),
    purchaserToken,
    "/procurement/updateStatus",
    {
      method: "PUT",
      body: { id: order.id, status: 3, purchaseNote: "Must be rejected" },
    }
  );
  assert(
    directShipping?.msg === "发货和到货状态必须通过物流进度更新",
    `Unexpected direct-shipping rejection: ${directShipping?.msg}`
  );

  await requireSuccess(base(1), purchaserToken, "/procurement/assignLogistics", {
    method: "PUT",
    body: { orderId: order.id, userId: logistics.id },
  });

  for (let index = 1; index <= 25; index += 1) {
    await requireSuccess(base(index), purchaserToken, "/procurement/message/send", {
      method: "POST",
      body: {
        orderId: order.id,
        channelType: 1,
        receiverId: logistics.id,
        content: `Boundary message ${String(index).padStart(2, "0")} ${runKey}`,
      },
    });
  }

  const firstPage = await requireSuccess(
    base(2),
    logisticsToken,
    "/procurement/message/query",
    {
      method: "POST",
      body: {
        current: 1,
        size: 20,
        orderId: order.id,
        channelType: 1,
        unreadOnly: true,
      },
    }
  );
  const renderedIds = (firstPage.data || []).map((message) => Number(message.id));
  assert(renderedIds.length === 20, `Expected 20 newest messages, got ${renderedIds.length}`);
  assert(Number(firstPage.total) === 25, `Expected 25 unread messages, got ${firstPage.total}`);

  await requireSuccess(base(0), logisticsToken, "/procurement/message/read", {
    method: "PUT",
    body: { orderId: order.id, channelType: 1, messageIds: renderedIds },
  });
  const unreadAfterFirstPage = await requireSuccess(
    base(1),
    logisticsToken,
    `/procurement/message/unread?orderId=${order.id}`
  );
  assert(
    Number(unreadAfterFirstPage.data?.total) === 5,
    `Unrendered messages were marked read: ${JSON.stringify(unreadAfterFirstPage.data)}`
  );

  const olderPage = await requireSuccess(
    base(2),
    logisticsToken,
    "/procurement/message/query",
    {
      method: "POST",
      body: {
        current: 1,
        size: 20,
        orderId: order.id,
        channelType: 1,
        unreadOnly: true,
        beforeId: Math.min(...renderedIds),
      },
    }
  );
  const olderIds = (olderPage.data || []).map((message) => Number(message.id));
  assert(olderIds.length === 5, `Expected 5 older unread messages, got ${olderIds.length}`);
  await requireSuccess(base(0), logisticsToken, "/procurement/message/read", {
    method: "PUT",
    body: { orderId: order.id, channelType: 1, messageIds: olderIds },
  });
  const unreadAfterAllPages = await requireSuccess(
    base(1),
    logisticsToken,
    `/procurement/message/unread?orderId=${order.id}`
  );
  assert(
    Number(unreadAfterAllPages.data?.total) === 0,
    `Unread messages remain after both pages: ${JSON.stringify(unreadAfterAllPages.data)}`
  );

  record("procurement-shipping-boundary", {
    orderId: order.id,
    rejection: directShipping.msg,
  });
  record("rendered-message-read-boundary", {
    orderId: order.id,
    totalMessages: 25,
    firstPageRead: 20,
    olderPageRead: 5,
  });

  return order.id;
}

let rootToken;
let temporaryUserId;
let temporaryOrderId;

try {
  assert(sharedPassword, "Set DRL_DEMO_PASSWORD before running boundary verification.");
  rootToken = await login(base(0), identities.root);
  const purchaserToken = await login(base(1), identities.purchaser);
  const logisticsToken = await login(base(2), identities.logistics);

  temporaryUserId = await runAuthenticationBoundaries(rootToken);
  temporaryOrderId = await runProcurementBoundaries(
    rootToken,
    purchaserToken,
    logisticsToken
  );

  report.status = "passed";
} catch (error) {
  report.status = "failed";
  report.error = error instanceof Error ? error.stack : String(error);
  throw error;
} finally {
  const orderIdToClean = temporaryOrderId || report.temporaryOrderId;
  const userIdToClean = temporaryUserId || report.temporaryUserId;
  if (rootToken && orderIdToClean) {
    await apiRequest(base(0), rootToken, "/procurement/updateStatus", {
      method: "PUT",
      body: {
        id: orderIdToClean,
        status: 7,
        purchaseNote: "Boundary verification cleanup",
      },
    }).catch(() => {});
  }
  if (rootToken && userIdToClean) {
    await apiRequest(base(1), rootToken, "/user/batchDelete", {
      method: "POST",
      body: [userIdToClean],
    }).catch(() => {});
  }
  report.completedAt = new Date().toISOString();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`
  );
}

console.log(
  `Cross-instance boundary verification passed: ${report.scenarios.length} scenarios, ` +
    `${report.requests} real requests.`
);
