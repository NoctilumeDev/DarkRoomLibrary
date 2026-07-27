import { createRouter, createWebHashHistory } from "vue-router";
import { getToken, clearAuthSession } from "@/utils/storage.js";
import { resolveRoleHome } from "@/utils/roleHome.js";
import { USER_ROLE } from "@/utils/userRoles.js";
import jwtDecode from "jwt-decode";

const routes = [
  { path: "/", redirect: "/login" },
  { path: "/login", component: () => import("@/views/login/Login.vue") },
  { path: "/register", component: () => import("@/views/register/Register.vue") },
  { path: "/resetPwd", component: () => import("@/views/login/ResetPwd.vue") },
  { path: "/bookBorrow", redirect: "/bookSearch" },
  {
    path: "/admin",
    component: () => import("@/views/admin/Home.vue"),
    redirect: "/dashboard",
    meta: {
      requireAuth: true,
      roles: [USER_ROLE.SUPER_ADMIN, USER_ROLE.ADMIN],
    },
    children: [
      {
        path: "/dashboard",
        name: "数据总览",
        icon: "DataAnalysis",
        group: "总览",
        component: () => import("@/views/admin/Main.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/statisticsDashboard",
        name: "统计看板",
        icon: "DataLine",
        group: "总览",
        component: () => import("@/views/admin/StatisticsDashboard.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/bookManage",
        name: "图书管理",
        icon: "Notebook",
        group: "馆藏",
        component: () => import("@/views/admin/BookManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/categoryManage",
        name: "分类管理",
        icon: "Menu",
        group: "馆藏",
        component: () => import("@/views/admin/CategoryManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/bookshelfManage",
        name: "书架管理",
        icon: "Grid",
        group: "馆藏",
        component: () => import("@/views/admin/BookshelfManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/borrowManage",
        name: "借阅管理",
        icon: "DocumentCopy",
        group: "流通",
        component: () => import("@/views/admin/BorrowManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/userManage",
        name: "用户管理",
        icon: "UserFilled",
        group: "用户",
        component: () => import("@/views/admin/UserManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/noticeManage",
        name: "公告管理",
        icon: "Edit",
        group: "内容",
        component: () => import("@/views/admin/NoticeManage.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/createNotice",
        name: "公告编辑",
        group: "内容",
        component: () => import("@/views/admin/CreateNotice.vue"),
        meta: {
          requireAuth: true,
          roles: [USER_ROLE.SUPER_ADMIN, USER_ROLE.ADMIN],
          hidden: true,
        },
      },
      {
        path: "/procurementManage",
        name: "采购物流",
        icon: "Van",
        group: "采购",
        component: () => import("@/views/procurement/ProcurementWorkbench.vue"),
        meta: {
          requireAuth: true,
          roles: [USER_ROLE.SUPER_ADMIN, USER_ROLE.ADMIN],
        },
      },
      {
        path: "/contentAudit",
        name: "内容审核",
        icon: "Warning",
        group: "内容",
        component: () => import("@/views/admin/ContentAudit.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/operationLog",
        name: "操作日志",
        icon: "Document",
        group: "系统",
        component: () => import("@/views/admin/OperationLog.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/workflowStatus",
        name: "流程状态",
        icon: "Connection",
        group: "系统",
        component: () => import("@/views/admin/WorkflowStatus.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/dataExport",
        name: "数据导出",
        icon: "Download",
        group: "系统",
        component: () => import("@/views/admin/DataExport.vue"),
        meta: { requireAuth: true },
      },
      {
        path: "/fileManage",
        name: "文件管理",
        icon: "FolderOpened",
        group: "系统",
        component: () => import("@/views/admin/FileManage.vue"),
        meta: { requireAuth: true, roles: [USER_ROLE.SUPER_ADMIN] },
      },
      {
        path: "/messageManage",
        name: "留言管理",
        icon: "ChatLineSquare",
        group: "内容",
        component: () => import("@/views/admin/MessageManage.vue"),
        meta: { requireAuth: true },
      },
    ],
  },
  {
    path: "/user",
    component: () => import("@/views/user/Home.vue"),
    redirect: "/readerRoom",
    meta: { requireAuth: true, roles: [USER_ROLE.READER] },
    children: [
      {
        name: "藏书室",
        functionalName: "读者首页",
        path: "/readerRoom",
        icon: "House",
        component: () => import("@/views/user/ReaderRoom.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "检索台",
        functionalName: "查找图书",
        path: "/bookSearch",
        icon: "Reading",
        component: () => import("@/views/user/BookBorrow.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "我的借阅",
        functionalName: "借阅记录",
        path: "/myBorrows",
        icon: "Collection",
        component: () => import("@/views/user/MyBorrows.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "我的收藏",
        functionalName: "收藏图书",
        path: "/myFavorites",
        icon: "StarFilled",
        component: () => import("@/views/user/MyFavorites.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "我的预约",
        functionalName: "预约队列",
        path: "/myReservations",
        icon: "Date",
        component: () => import("@/views/user/MyReservations.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "书评回廊",
        functionalName: "读者书评",
        path: "/bookReviews",
        icon: "Reading",
        component: () => import("@/views/user/BookReviews.vue"),
        meta: { requireAuth: true },
      },
      {
        name: "留言板",
        functionalName: "读者留言",
        path: "/messageBoard",
        icon: "Message",
        component: () => import("@/views/user/Main.vue"),
        meta: { requireAuth: true },
      },
    ],
  },
  {
    path: "/procurement",
    component: () => import("@/views/procurement/ProcurementHome.vue"),
    redirect: "/procurementWorkbench",
    meta: {
      requireAuth: true,
      roles: [USER_ROLE.ACQUISITIONS, USER_ROLE.LOGISTICS],
    },
    children: [
      {
        path: "/procurementWorkbench",
        name: "采购物流工作台",
        component: () => import("@/views/procurement/ProcurementWorkbench.vue"),
        meta: { requireAuth: true },
      },
    ],
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  if (!to.meta.requireAuth) {
    next();
    return;
  }

  const token = getToken();
  if (!token) {
    next("/login");
    return;
  }

  try {
    const decoded = jwtDecode(token);
    const now = Math.floor(Date.now() / 1000);
    if (decoded.exp && decoded.exp < now) {
      clearAuthSession();
      next("/login");
      return;
    }

    if (to.meta.roles && !to.meta.roles.includes(decoded.role)) {
      next(resolveRoleHome(decoded.role) || "/login");
      return;
    }
  } catch {
    clearAuthSession();
    next("/login");
    return;
  }

  next();
});

export default router;
