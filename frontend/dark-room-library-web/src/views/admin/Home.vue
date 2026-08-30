<template>
  <div class="admin-shell" :data-admin-theme="adminTheme">
    <header class="paper-header">
      <div class="brand-area">
        <button class="brand" type="button" @click="goDashboard">
          <span class="seal">守</span>
          <span>
            <strong>暗室·藏书</strong>
            <em>守夜人后台 · 馆藏中枢</em>
          </span>
        </button>
      </div>

      <nav class="module-nav" aria-label="后台主模块">
        <button
          v-for="group in groupedRoutes"
          :key="group.name"
          type="button"
          :class="{ active: activeGroupName === group.name }"
          @click="selectGroup(group)"
        >
          {{ group.name }}
        </button>
      </nav>

      <div class="header-actions">
        <button
          class="theme-toggle"
          type="button"
          :aria-pressed="adminTheme === 'night'"
          :title="`切换为${adminTheme === 'day' ? '显影' : '素笺'}模式`"
          @click="toggleTheme"
        >
          <el-icon aria-hidden="true">
            <Tickets v-if="adminTheme === 'day'" />
            <Camera v-else />
          </el-icon>
          <span>{{ adminTheme === "day" ? "素笺" : "显影" }}</span>
        </button>

        <el-dropdown trigger="click">
          <button class="admin-user" type="button">
            <el-avatar :size="34" :src="getAvatarUrl(userInfo.url)">
              {{ avatarText }}
            </el-avatar>
            <span>{{ userInfo.name || "管理员" }}</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openProfile">个人资料</el-dropdown-item>
              <el-dropdown-item @click="loginOut">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <section class="sub-nav" aria-label="后台二级功能">
      <button
        v-for="route in activeGroupRoutes"
        :key="route.path"
        type="button"
        :class="{ active: route.path === $route.path }"
        @click="goRoute(route.path)"
      >
        {{ route.name }}
      </button>
    </section>

    <main class="paper-workspace">
      <router-view :key="$route.fullPath" />
    </main>

    <ProfileDialog
      v-model="dialogOperation"
      :user-info="userInfo"
      @saved="tokenCheckLoad"
    />
  </div>
</template>

<script>
import router from "@/router/index";
import request from "@/utils/request.js";
import { clearAuthSession, setUserProfile } from "@/utils/storage";
import { resolveFileUrl } from "@/utils/fileUrl.js";
import ProfileDialog from "@/components/ProfileDialog.vue";
import { Camera, Tickets } from "@element-plus/icons-vue";

const GROUP_ORDER = ["总览", "馆藏", "流通", "采购", "用户", "内容", "系统"];

function resolveAdminTheme() {
  const savedTheme = localStorage.getItem("admin-theme");
  return savedTheme === "night" || savedTheme === "bamboo" ? "night" : "day";
}

export default {
  name: "AdminHome",
  components: {
    ProfileDialog,
    Camera,
    Tickets,
  },
  data() {
    return {
      adminRoutes: [],
      userInfo: {
        id: null,
        url: "",
        name: "",
        role: null,
        email: "",
      },
      dialogOperation: false,
      adminTheme: resolveAdminTheme(),
    };
  },
  computed: {
    visibleAdminRoutes() {
      return this.adminRoutes.filter(
        (route) =>
          !route.meta?.hidden &&
          (!route.meta?.roles || route.meta.roles.includes(this.userInfo.role))
      );
    },
    groupedRoutes() {
      return GROUP_ORDER.map((groupName) => ({
        name: groupName,
        routes: this.visibleAdminRoutes.filter((route) => route.group === groupName),
      })).filter((group) => group.routes.length > 0);
    },
    activeGroupName() {
      const route = this.adminRoutes.find((item) => item.path === this.$route.path);
      return route?.group || this.groupedRoutes[0]?.name || "";
    },
    activeGroupRoutes() {
      const group = this.groupedRoutes.find(
        (item) => item.name === this.activeGroupName
      );
      return group?.routes || [];
    },
    avatarText() {
      return this.userInfo.name ? this.userInfo.name.charAt(0) : "守";
    },
  },
  created() {
    const menus = router.options.routes.find((route) => route.path === "/admin");
    this.adminRoutes = menus?.children || [];
    this.tokenCheckLoad();
  },
  mounted() {
    this.applyTheme();
  },
  beforeUnmount() {
    delete document.body.dataset.adminTheme;
  },
  methods: {
    applyTheme() {
      document.body.dataset.adminTheme = this.adminTheme;
      localStorage.setItem("admin-theme", this.adminTheme);
      window.dispatchEvent(
        new CustomEvent("admin-theme-change", {
          detail: { theme: this.adminTheme },
        })
      );
    },
    toggleTheme() {
      this.adminTheme = this.adminTheme === "day" ? "night" : "day";
      this.$nextTick(this.applyTheme);
    },
    getAvatarUrl(url) {
      return resolveFileUrl(url);
    },
    goDashboard() {
      this.goRoute("/dashboard");
    },
    selectGroup(group) {
      const firstRoute = group.routes[0];
      if (firstRoute) this.goRoute(firstRoute.path);
    },
    goRoute(path) {
      if (this.$route.path !== path) {
        this.$router.push(path);
      }
    },
    openProfile() {
      this.dialogOperation = true;
    },
    async loginOut() {
      const confirmed = await this.$swalConfirm({
        title: "退出馆务后台？",
        text: "当前账号将退出，下次进入需要重新登录。",
        icon: undefined,
        confirmButtonText: "退出",
        cancelButtonText: "留下",
        quiet: true,
      });
      if (!confirmed) return;
      clearAuthSession();
      this.$router.push("/login");
    },
    async tokenCheckLoad() {
      try {
        const res = await request.get("/user/auth");
        if (res.data.code !== 200) {
          this.$message.error(res.data.msg || "认证信息已失效。");
          this.$router.push("/login");
          return;
        }

        const {
          id,
          userAvatar: url,
          userName: name,
          userRole: role,
          userEmail: email,
        } = res.data.data;
        this.userInfo = { id, url, name, role, email };
        setUserProfile(this.userInfo);
      } catch (error) {
        console.error("获取管理员认证信息失败:", error);
        this.$message.error("认证信息加载失败，请稍后重试。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.paper-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: grid;
  grid-template-columns: minmax(190px, auto) 1fr auto;
  gap: 22px;
  align-items: center;
  min-height: 76px;
  padding: 13px 28px;
  border-bottom: 1px solid color-mix(in srgb, var(--admin-border) 72%, transparent);
  background: color-mix(in srgb, var(--admin-paper-light) 84%, transparent);
  box-shadow: 0 12px 42px color-mix(in srgb, var(--admin-shadow) 26%, transparent);
  backdrop-filter: blur(14px);
}

.brand,
.module-nav button,
.sub-nav button,
.theme-toggle,
.admin-user {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--admin-text);
  text-align: left;

  strong {
    display: block;
    font-size: 20px;
    font-weight: 600;
  }

  em {
    display: block;
    margin-top: 2px;
    color: var(--admin-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.seal {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--admin-seal);
  border-radius: 2px;
  color: var(--admin-seal);
  background: color-mix(in srgb, var(--admin-paper-light) 28%, transparent);
  font-weight: 700;
  transform: rotate(-2deg);
  box-shadow: inset 0 0 8px color-mix(in srgb, var(--admin-seal) 8%, transparent);
}

.module-nav {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;

  button {
    position: relative;
    min-width: 44px;
    padding: 9px 2px;
    color: var(--admin-text-secondary);
    transition: background-color 0.2s ease, color 0.2s ease;

    &::after {
      content: "";
      position: absolute;
      left: 16%;
      right: 16%;
      bottom: 1px;
      height: 1px;
      background: transparent;
    }

    &:hover,
    &.active {
      color: var(--admin-seal);
      background: transparent;
    }

    &.active::after {
      background: linear-gradient(90deg, transparent, var(--admin-seal), transparent);
    }
  }
}

.header-actions,
.theme-toggle,
.admin-user {
  display: inline-flex;
  align-items: center;
}

.header-actions {
  gap: 14px;
}

.theme-toggle {
  gap: 7px;
  min-height: 36px;
  padding: 6px 10px;
  border: 1px solid color-mix(in srgb, var(--admin-border) 64%, transparent);
  border-radius: 3px;
  color: var(--admin-text-secondary);
  background: color-mix(in srgb, var(--admin-paper-light) 34%, transparent);
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    color: var(--admin-text);
    border-color: var(--admin-border-strong);
    background: color-mix(in srgb, var(--admin-paper-wash) 74%, transparent);
  }

  .el-icon {
    color: var(--admin-gold);
    font-size: 17px;
  }
}

.admin-user {
  gap: 8px;
  color: var(--admin-text);
}

.sub-nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 11px 28px;
  border-bottom: 1px solid color-mix(in srgb, var(--admin-border) 68%, transparent);
  background: color-mix(in srgb, var(--admin-paper) 54%, transparent);
  backdrop-filter: blur(10px);

  button {
    position: relative;
    padding: 7px 11px;
    color: var(--admin-muted);

    &::after {
      content: "";
      position: absolute;
      left: 11px;
      right: 11px;
      bottom: 1px;
      height: 1px;
      background: transparent;
    }

    &:hover,
    &.active {
      color: var(--admin-text);
    }

    &.active::after {
      background: var(--admin-accent);
    }
  }
}

.paper-workspace {
  min-height: calc(100vh - 126px);
}

@media (max-width: 980px) {
  .paper-header {
    grid-template-columns: 1fr auto;
    gap: 12px;
  }

  .module-nav {
    grid-column: 1 / -1;
    grid-row: 2;
    justify-content: flex-start;
  }
}

@media (max-width: 680px) {
  .paper-header,
  .sub-nav {
    padding-left: 14px;
    padding-right: 14px;
  }

  .paper-header {
    grid-template-columns: 1fr;
  }

  .header-actions {
    justify-content: space-between;
  }

  .module-nav {
    grid-column: 1;
    flex-wrap: nowrap;
    justify-content: space-between;
    gap: 0;

    button {
      min-width: 38px;
      padding-inline: 1px;
    }
  }

}
</style>
