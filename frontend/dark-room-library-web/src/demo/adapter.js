import { createDemoState } from "@/demo/data.js";
import {
  activateDemoIdentity,
  DEMO_STATE_KEY,
  findDemoIdentity,
  getActiveDemoIdentity,
  toDemoUser,
} from "@/demo/runtime.js";

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function readState() {
  try {
    const stored = sessionStorage.getItem(DEMO_STATE_KEY);
    if (stored) return JSON.parse(stored);
  } catch {
    // Fall through to a fresh in-memory state.
  }
  const state = createDemoState();
  writeState(state);
  return state;
}

function writeState(state) {
  try {
    sessionStorage.setItem(DEMO_STATE_KEY, JSON.stringify(state));
  } catch {
    // The current response remains valid even if persistence is unavailable.
  }
}

function nextId(state) {
  state.sequence += 1;
  return state.sequence;
}

function parsePayload(config) {
  if (!config.data) return {};
  if (typeof config.data === "object") return config.data;
  try {
    return JSON.parse(config.data);
  } catch {
    return {};
  }
}

function page(items, payload = {}) {
  const current = Math.max(1, Number(payload.current || 1));
  const size = Math.max(1, Number(payload.size || items.length || 10));
  const start = (current - 1) * size;
  return { items: items.slice(start, start + size), total: items.length };
}

function body(code, data, msg, total) {
  return {
    code,
    msg,
    data,
    ...(total === undefined ? {} : { total }),
  };
}

function ok(data = null, msg = "操作成功", total) {
  return body(200, data, msg, total);
}

function rejected(msg) {
  return body(409, null, msg);
}

function response(config, data) {
  return Promise.resolve({
    data,
    status: 200,
    statusText: "OK",
    headers: { "content-type": "application/json;charset=UTF-8" },
    config,
    request: null,
  });
}

function pathOf(config) {
  return new URL(config.url || "/", "https://demo.darkroomlibrary.local")
    .pathname;
}

function queryParams(config) {
  const url = new URL(
    config.url || "/",
    "https://demo.darkroomlibrary.local"
  );
  Object.entries(config.params || {}).forEach(([key, value]) => {
    if (value !== null && value !== undefined) url.searchParams.set(key, value);
  });
  return url.searchParams;
}

function activeUser(state) {
  const identity = getActiveDemoIdentity();
  if (!identity) return null;
  return (
    state.users.find((user) => user.id === identity.id) || toDemoUser(identity)
  );
}

function findBook(state, id) {
  return state.books.find((book) => book.id === Number(id));
}

function paginateResponse(items, payload) {
  const result = page(items, payload);
  return ok(clone(result.items), "查询成功", result.total);
}

function queryBooks(state, payload) {
  let books = state.books.filter(
    (book) => Boolean(book.deleted) === Boolean(payload.deleted)
  );
  if (payload.name) {
    books = books.filter((book) => book.name.includes(String(payload.name)));
  }
  if (payload.author) {
    books = books.filter((book) => book.author.includes(String(payload.author)));
  }
  if (payload.category) {
    books = books.filter((book) => book.category === payload.category);
  }
  return paginateResponse(books, payload);
}

function queryBorrowRecords(state, payload, user) {
  let records = state.borrowRecords;
  if (user?.userRole === 2) {
    records = records.filter((record) => record.userId === user.id);
  } else if (payload.userId) {
    records = records.filter(
      (record) => record.userId === Number(payload.userId)
    );
  }
  if (typeof payload.status === "boolean") {
    records = records.filter((record) => record.status === payload.status);
  }
  if (typeof payload.overdue === "boolean") {
    const now = Date.now();
    records = records.filter((record) => {
      const overdue = !record.status && new Date(record.dueDate).getTime() < now;
      return overdue === payload.overdue;
    });
  }
  return paginateResponse(records, payload);
}

function queryReservations(state, payload, user) {
  let reservations = state.reservations;
  if (user?.userRole === 2) {
    reservations = reservations.filter((item) => item.userId === user.id);
  } else if (payload.userId) {
    reservations = reservations.filter(
      (item) => item.userId === Number(payload.userId)
    );
  }
  return paginateResponse(reservations, payload);
}

function queryReviews(state, payload) {
  let reviews = state.reviews.filter((review) => review.status !== 1);
  if (payload.bookId) {
    reviews = reviews.filter(
      (review) => review.bookId === Number(payload.bookId)
    );
  }
  if (payload.sortBy === "hot") {
    reviews = [...reviews].sort((a, b) => b.likeCount - a.likeCount);
  } else {
    reviews = [...reviews].sort((a, b) =>
      String(b.createTime).localeCompare(String(a.createTime))
    );
  }
  return paginateResponse(reviews, payload);
}

function borrowBook(state, bookId, user) {
  if (user?.userRole !== 2) return rejected("只有读者身份可以借阅图书。");
  const book = findBook(state, bookId);
  if (!book) return rejected("未找到图书。");
  if (book.availableCount <= 0) return rejected("当前无可借库存，请先预约。");
  const active = state.borrowRecords.some(
    (record) =>
      record.userId === user.id &&
      record.bookId === book.id &&
      record.status === false
  );
  if (active) return rejected("你已经借阅了这本书。");

  book.availableCount -= 1;
  state.borrowRecords.unshift({
    id: nextId(state),
    userId: user.id,
    userName: user.userName,
    bookId: book.id,
    bookName: book.name,
    bookAuthor: book.author,
    borrowTime: "2026-07-27 20:30:00",
    dueDate: "2026-08-26 20:30:00",
    returnTime: null,
    status: false,
    renewCount: 0,
    fine: 0,
  });
  writeState(state);
  return ok(null, "借阅成功，库存已同步扣减。");
}

function reserveBook(state, bookId, user) {
  if (user?.userRole !== 2) return rejected("只有读者身份可以预约图书。");
  const book = findBook(state, bookId);
  if (!book) return rejected("未找到图书。");
  const active = state.reservations.some(
    (item) =>
      item.userId === user.id &&
      item.bookId === book.id &&
      [0, 3].includes(item.status)
  );
  if (active) return rejected("你已经在这本书的预约队列中。");
  state.reservations.unshift({
    id: nextId(state),
    userId: user.id,
    userName: user.userName,
    bookId: book.id,
    bookName: book.name,
    status: 0,
    createTime: "2026-07-27 20:32:00",
    expireTime: "2026-08-06 20:32:00",
    queuePosition: 1,
  });
  writeState(state);
  return ok(null, "预约成功，已加入候书队列。");
}

function filterOrdersForIdentity(orders, identity) {
  if (!identity || identity.role === 0) return orders;
  if (identity.role === 1) {
    if (identity.isCoordinatorAdmin) return orders;
    return orders.filter((order) => order.requesterId === identity.id);
  }
  if (identity.role === 3) {
    return orders.filter(
      (order) => !order.purchaserId || order.purchaserId === identity.id
    );
  }
  if (identity.role === 4) {
    return orders.filter((order) => order.logisticsId === identity.id);
  }
  return [];
}

function updateProcurementStatus(state, payload) {
  const order = state.procurementOrders.find(
    (item) => item.id === Number(payload.id)
  );
  if (!order) return rejected("未找到采购单。");
  order.status = Number(payload.status);
  order.updateTime = "2026-07-27 20:40:00";
  writeState(state);
  return ok(null, "采购状态已更新。");
}

function updateLogistics(state, payload) {
  const order = state.procurementOrders.find(
    (item) => item.id === Number(payload.orderId)
  );
  if (!order) return rejected("未找到采购单。");
  const status = Number(payload.status);
  order.logisticsStatus = status;
  order.carrier = payload.carrier || order.carrier;
  order.trackingNo = payload.trackingNo || order.trackingNo;
  order.logisticsRemark = payload.remark || "";
  order.updateTime = "2026-07-27 20:42:00";
  if (status === 3) {
    order.status = Math.max(order.status, 5);
    if (!order.stockApplied) {
      const book = findBook(state, order.bookId);
      if (book) {
        book.totalCount += order.requestCount;
        book.availableCount += order.requestCount;
      }
      order.stockApplied = true;
    }
  }
  writeState(state);
  return ok(
    null,
    status === 3
      ? "入库完成，采购数量已幂等写入库存。"
      : "物流状态已更新。"
  );
}

function statistics(path, state) {
  if (path === "/statistics/overview") {
    return ok({
      totalBooks: state.books
        .filter((book) => !book.deleted)
        .reduce((sum, book) => sum + book.totalCount, 0),
      totalUsers: state.users.length,
      activeBorrows: state.borrowRecords.filter((item) => !item.status).length,
      returnedBorrows: 764 + state.borrowRecords.filter((item) => item.status).length,
    });
  }
  if (path.startsWith("/statistics/monthlyBorrow/")) {
    return ok([
      { day: 1, count: 3 },
      { day: 4, count: 6 },
      { day: 7, count: 5 },
      { day: 10, count: 8 },
      { day: 13, count: 7 },
      { day: 16, count: 10 },
      { day: 19, count: 12 },
      { day: 22, count: 9 },
      { day: 25, count: 14 },
    ]);
  }
  if (path === "/statistics/hotBooks") {
    return ok({
      books: [
        { bookName: "暗室藏书", borrowCount: 37 },
        { bookName: "归架之前", borrowCount: 31 },
        { bookName: "雾灯索引", borrowCount: 28 },
        { bookName: "星阑采书札", borrowCount: 22 },
        { bookName: "青梧守卷录", borrowCount: 17 },
      ],
    });
  }
  if (path === "/statistics/lowStock") {
    return ok({
      books: state.books
        .filter((book) => !book.deleted && book.availableCount < 3)
        .map(({ name, author, availableCount }) => ({
          name,
          author,
          availableCount,
        })),
    });
  }
  if (path === "/statistics/overdueUsers") {
    return ok([
      { userName: "纸月听澜", overdueCount: 1, totalFine: 2 },
    ]);
  }
  if (path === "/statistics/collectionAnalysis") {
    return ok({
      categories: state.categories.map((category) => ({
        category: category.name,
        totalCount: state.books
          .filter((book) => !book.deleted && book.category === category.name)
          .reduce((sum, book) => sum + book.totalCount, 0),
      })),
    });
  }
  return null;
}

export async function demoAdapter(config) {
  const state = readState();
  const path = pathOf(config);
  const method = String(config.method || "get").toLowerCase();
  const payload = parsePayload(config);
  const params = queryParams(config);
  const identity = getActiveDemoIdentity();
  const user = activeUser(state);
  let result;

  if (path === "/captcha/generate") {
    result = ok({ captchaId: "browser-demo", expression: "7 + 5 = ?" });
  } else if (path === "/user/login" && method === "post") {
    const loginIdentity = findDemoIdentity(payload.userAccount);
    if (
      !loginIdentity ||
      payload.userPwd !== "DarkRoom@20606" ||
      Number(payload.captchaAnswer) !== 12
    ) {
      result = rejected("演示账号、密码或验证题答案不正确。");
    } else {
      activateDemoIdentity(loginIdentity.key);
      result = ok(
        {
          token: sessionStorage.getItem("token"),
          role: loginIdentity.role,
        },
        "登录成功"
      );
    }
  } else if (path === "/user/auth") {
    result = user ? ok(clone(user)) : body(401, null, "请先选择演示身份。");
  } else if (
    [
      "/user/sendVerifyCode",
      "/user/sendEmailChangeCode",
      "/user/register",
      "/user/resetPwd",
    ].includes(path)
  ) {
    result = rejected("在线演示不发送邮件或创建真实账号。");
  } else if (path === "/user/update" && method === "put") {
    if (!user) result = body(401, null, "请先选择演示身份。");
    else {
      const requestedEmail = String(payload.userEmail || user.userEmail)
        .trim()
        .toLowerCase();
      const linkedAccounts = state.users.filter(
        (candidate) =>
          candidate.id !== user.id &&
          String(candidate.userEmail || "").trim().toLowerCase() ===
            requestedEmail
      ).length;
      if (requestedEmail && linkedAccounts >= 3) {
        result = body(400, null, "同一邮箱最多关联 3 个账号，请更换邮箱");
      } else {
        user.userName = payload.userName || user.userName;
        user.userEmail = requestedEmail || user.userEmail;
        user.userAvatar = payload.userAvatar || user.userAvatar;
        writeState(state);
        result = ok(clone(user), "个人资料已在当前演示会话中更新。");
      }
    }
  } else if (path === "/user/cancelAccount") {
    result = rejected("在线演示不执行账号注销。");
  } else if (path === "/category/queryAll") {
    result = ok(clone(state.categories));
  } else if (path === "/bookshelf/queryAll") {
    result = ok(clone(state.bookshelves));
  } else if (path === "/category/query") {
    result = paginateResponse(state.categories, payload);
  } else if (path === "/bookshelf/query") {
    result = paginateResponse(state.bookshelves, payload);
  } else if (path === "/book/query") {
    result = queryBooks(state, payload);
  } else if (path.startsWith("/book/queryByDays/")) {
    result = ok([
      { name: "07-20", count: 1 },
      { name: "07-21", count: 2 },
      { name: "07-22", count: 4 },
      { name: "07-23", count: 3 },
      { name: "07-24", count: 5 },
      { name: "07-25", count: 6 },
    ]);
  } else if (path.startsWith("/user/queryByDays/")) {
    result = ok([
      { name: "07-20", count: 2 },
      { name: "07-21", count: 3 },
      { name: "07-22", count: 3 },
      { name: "07-23", count: 5 },
      { name: "07-24", count: 6 },
      { name: "07-25", count: 8 },
    ]);
  } else if (path === "/views/staticControls") {
    result = ok([
      { name: "注册读者", count: state.users.filter((item) => item.userRole === 2).length },
      { name: "在馆图书", count: state.books.reduce((sum, book) => sum + book.totalCount, 0) },
      { name: "借阅中", count: state.borrowRecords.filter((item) => !item.status).length },
      { name: "采购流转", count: state.procurementOrders.filter((item) => item.status < 6).length },
    ]);
  } else if (path === "/borrowRecord/query") {
    result = queryBorrowRecords(state, payload, user);
  } else if (path.startsWith("/borrowRecord/borrow/")) {
    result = borrowBook(state, path.split("/").pop(), user);
  } else if (path.startsWith("/borrowRecord/return/")) {
    const record = state.borrowRecords.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!record || record.status) result = rejected("借阅记录不可归还。");
    else {
      record.status = true;
      record.returnTime = "2026-07-27 20:35:00";
      const book = findBook(state, record.bookId);
      if (book) book.availableCount += 1;
      writeState(state);
      result = ok(null, "归还成功，库存已同步恢复。");
    }
  } else if (path.startsWith("/borrowRecord/renew/")) {
    const record = state.borrowRecords.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!record || record.status) result = rejected("借阅记录不可续借。");
    else {
      const due = new Date(record.dueDate);
      due.setDate(due.getDate() + 14);
      record.dueDate = due.toISOString().replace("T", " ").slice(0, 19);
      record.renewCount = Number(record.renewCount || 0) + 1;
      writeState(state);
      result = ok(null, "续借成功，到期时间已延长 14 天。");
    }
  } else if (path === "/bookReservation/query") {
    result = queryReservations(state, payload, user);
  } else if (path.startsWith("/bookReservation/reserve/")) {
    result = reserveBook(state, path.split("/").pop(), user);
  } else if (path.startsWith("/bookReservation/cancel/")) {
    const reservation = state.reservations.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!reservation || ![0, 3].includes(reservation.status)) {
      result = rejected("预约记录不可取消。");
    } else {
      reservation.status = 2;
      writeState(state);
      result = ok(null, "预约已取消。");
    }
  } else if (path === "/bookFavorite/query") {
    const favorites = state.favorites
      .filter((item) => item.userId === user?.id)
      .map((favorite) => {
        const book = findBook(state, favorite.bookId);
        return {
          ...favorite,
          bookId: book.id,
          bookName: book.name,
          bookAuthor: book.author,
          cover: book.cover,
          availableCount: book.availableCount,
        };
      });
    result = paginateResponse(favorites, payload);
  } else if (path.startsWith("/bookFavorite/isFavorited/")) {
    const bookId = Number(path.split("/").pop());
    result = ok(
      state.favorites.some(
        (item) => item.userId === user?.id && item.bookId === bookId
      )
    );
  } else if (
    path.startsWith("/bookFavorite/add/") ||
    path.startsWith("/bookFavorite/remove/")
  ) {
    const bookId = Number(path.split("/").pop());
    const index = state.favorites.findIndex(
      (item) => item.userId === user?.id && item.bookId === bookId
    );
    if (path.includes("/add/") && index < 0) {
      state.favorites.push({ id: nextId(state), userId: user.id, bookId });
    }
    if (path.includes("/remove/") && index >= 0) {
      state.favorites.splice(index, 1);
    }
    writeState(state);
    result = ok(null, path.includes("/add/") ? "收藏成功。" : "已取消收藏。");
  } else if (path === "/bookReview/query") {
    result = queryReviews(state, payload);
  } else if (path === "/bookReview/save") {
    const book = findBook(state, payload.bookId);
    if (!book || user?.userRole !== 2) result = rejected("当前身份不能提交书评。");
    else {
      state.reviews.unshift({
        id: nextId(state),
        userId: user.id,
        userName: user.userName,
        bookId: book.id,
        bookName: book.name,
        rating: Number(payload.rating),
        content: payload.content,
        createTime: "2026-07-27 20:36:00",
        likeCount: 0,
        liked: false,
        reported: false,
        reportCount: 0,
        status: 0,
        replies: [],
      });
      writeState(state);
      result = ok(null, "书评已保存在当前演示会话。");
    }
  } else if (path.startsWith("/bookReview/like/")) {
    const review = state.reviews.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!review) result = rejected("未找到书评。");
    else {
      review.liked = !review.liked;
      review.likeCount = Math.max(
        0,
        review.likeCount + (review.liked ? 1 : -1)
      );
      writeState(state);
      result = ok(review.liked, review.liked ? "已点赞。" : "已取消点赞。");
    }
  } else if (path.startsWith("/bookReview/reply/")) {
    const review = state.reviews.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!review || !user) result = rejected("未找到可回复的书评。");
    else {
      review.replies.push({
        id: nextId(state),
        userId: user.id,
        userName: user.userName,
        replyToUserName: review.userName,
        content: payload.content,
        createTime: "2026-07-27 20:37:00",
      });
      writeState(state);
      result = ok(null, "回复已保存。");
    }
  } else if (path.startsWith("/bookReview/report/")) {
    const review = state.reviews.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!review || !user) result = rejected("未找到可举报的书评。");
    else {
      review.reported = true;
      review.reportCount = Number(review.reportCount || 0) + 1;
      state.reviewReports.unshift({
        id: nextId(state),
        reviewId: review.id,
        bookName: review.bookName,
        reviewerName: review.userName,
        reporterName: user.userName,
        reason: payload.reason,
        status: 0,
        createTime: "2026-07-27 20:38:00",
      });
      writeState(state);
      result = ok(null, "举报已进入演示审核队列。");
    }
  } else if (path === "/bookReviewReport/query") {
    result = paginateResponse(state.reviewReports, payload);
  } else if (path.startsWith("/bookReviewReport/")) {
    const reportId = Number(path.split("/").pop());
    const report = state.reviewReports.find((item) => item.id === reportId);
    if (!report) result = rejected("未找到举报记录。");
    else {
      report.status = 1;
      writeState(state);
      result = ok(null, "举报审核状态已更新。");
    }
  } else if (path === "/notice/query") {
    result = paginateResponse(state.notices, payload);
  } else if (path === "/messageBoard/query") {
    result = paginateResponse(state.messageBoard, payload);
  } else if (path === "/messageBoard/save") {
    if (!user) result = body(401, null, "请先选择演示身份。");
    else {
      state.messageBoard.unshift({
        id: nextId(state),
        userId: user.id,
        userName: user.userName,
        content: payload.content,
        replyContent: "",
        createTime: "2026-07-27 20:39:00",
        attachmentUrl: "",
        attachmentName: "",
      });
      writeState(state);
      result = ok(null, "留言已保存在当前演示会话。");
    }
  } else if (path === "/user/query") {
    result = paginateResponse(state.users, payload);
  } else if (path === "/user/collaborationUsers") {
    const role = Number(params.get("role"));
    result = ok(clone(state.users.filter((item) => item.userRole === role)));
  } else if (path === "/procurement/query") {
    let orders = filterOrdersForIdentity(state.procurementOrders, identity);
    if (payload.bookName) {
      orders = orders.filter((order) =>
        order.bookName.includes(String(payload.bookName))
      );
    }
    if (payload.status !== null && payload.status !== undefined) {
      orders = orders.filter((order) => order.status === Number(payload.status));
    }
    result = paginateResponse(orders, payload);
  } else if (path === "/procurement/save") {
    const book = findBook(state, payload.bookId);
    if (!book || !identity || ![0, 1].includes(identity.role)) {
      result = rejected("当前身份不能创建采购单。");
    } else {
      const purchaser = state.users.find(
        (item) => item.id === Number(payload.purchaserId)
      );
      state.procurementOrders.unshift({
        id: nextId(state),
        bookId: book.id,
        bookName: book.name,
        requestCount: Number(payload.requestCount),
        status: 0,
        requesterId: identity.id,
        requesterName: identity.name,
        purchaserId: purchaser?.id || null,
        purchaserName: purchaser?.userName || "",
        logisticsId: null,
        logisticsName: "",
        logisticsStatus: 0,
        carrier: "",
        trackingNo: "",
        logisticsRemark: "",
        updateTime: "2026-07-27 20:40:00",
        unreadCount: 0,
        stockApplied: false,
      });
      writeState(state);
      result = ok(null, "采购单已创建。");
    }
  } else if (path.startsWith("/procurement/claim/")) {
    const order = state.procurementOrders.find(
      (item) => item.id === Number(path.split("/").pop())
    );
    if (!order || identity?.role !== 3 || order.purchaserId) {
      result = rejected("当前采购单不可认领。");
    } else {
      order.purchaserId = identity.id;
      order.purchaserName = identity.name;
      writeState(state);
      result = ok(null, "采购单认领成功。");
    }
  } else if (
    ["/procurement/assignPurchaser", "/procurement/assignLogistics"].includes(
      path
    )
  ) {
    const order = state.procurementOrders.find(
      (item) => item.id === Number(payload.orderId)
    );
    const assigned = state.users.find(
      (item) => item.id === Number(payload.userId)
    );
    if (!order || !assigned) result = rejected("采购单或人员不存在。");
    else {
      if (path.endsWith("assignPurchaser")) {
        order.purchaserId = assigned.id;
        order.purchaserName = assigned.userName;
      } else {
        order.logisticsId = assigned.id;
        order.logisticsName = assigned.userName;
        order.logisticsStatus = 0;
      }
      writeState(state);
      result = ok(null, "协作人员已更新。");
    }
  } else if (path === "/procurement/updateStatus") {
    result = updateProcurementStatus(state, payload);
  } else if (path === "/procurement/updateLogistics") {
    result = updateLogistics(state, payload);
  } else if (path === "/procurement/message/query") {
    const messages = state.procurementMessages.filter(
      (item) =>
        item.orderId === Number(payload.orderId) &&
        item.channelType === Number(payload.channelType)
    );
    result = paginateResponse(messages, payload);
  } else if (path === "/procurement/message/read") {
    result = ok(null, "消息已读。");
  } else if (path === "/procurement/message/send") {
    if (!identity) result = body(401, null, "请先选择演示身份。");
    else {
      state.procurementMessages.push({
        id: nextId(state),
        orderId: Number(payload.orderId),
        channelType: Number(payload.channelType),
        senderId: identity.id,
        senderName: identity.name,
        receiverId: Number(payload.receiverId),
        content: payload.content,
        createTime: "2026-07-27 20:43:00",
      });
      writeState(state);
      result = ok(null, "协作消息已发送。");
    }
  } else if (path === "/operationLog/query") {
    result = paginateResponse(state.operationLogs, payload);
  } else if (path === "/adminWorkflow/auditStatus") {
    result = ok({
      pendingReviewReports: state.reviewReports.filter((item) => item.status === 0).length,
      activeProcurements: state.procurementOrders.filter((item) => item.status < 6).length,
      unreadMessages: state.procurementOrders.reduce((sum, item) => sum + Number(item.unreadCount || 0), 0),
    });
  } else if (path === "/adminWorkflow/backendFlow") {
    result = ok([
      { name: "认证与权限", status: "NORMAL", detail: "JWT 与角色边界" },
      { name: "借阅与库存", status: "NORMAL", detail: "会话内一致性" },
      { name: "采购与物流", status: "NORMAL", detail: "幂等入库演示" },
    ]);
  } else {
    const stats = statistics(path, state);
    if (stats) {
      result = stats;
    } else if (path === "/file/query") {
      result = ok([], "在线演示不保存真实文件。", 0);
    } else if (
      path.startsWith("/file/") ||
      path.includes("/export") ||
      config.responseType === "blob"
    ) {
      result = rejected("在线演示不上传、下载或导出真实文件。");
    } else if (
      method === "get" ||
      path.endsWith("/query") ||
      path.endsWith("/queryAll")
    ) {
      result = ok([], "当前页面没有更多演示数据。", 0);
    } else {
      result = rejected("此写操作未纳入浏览器演示，不会伪造成功结果。");
    }
  }

  return response(config, result);
}

export function resetDemoStateForTest() {
  try {
    sessionStorage.removeItem(DEMO_STATE_KEY);
  } catch {
    // No-op in storage-restricted test environments.
  }
}
