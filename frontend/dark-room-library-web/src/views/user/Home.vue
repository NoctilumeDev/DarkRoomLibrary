<template>
  <div
    class="reader-shell"
    :class="{ 'room-route': isRoom }"
    :data-reader-theme="theme"
  >
    <div class="scene-image" aria-hidden="true"></div>
    <div class="scene-veil" aria-hidden="true"></div>

    <header class="reader-header">
      <button class="wordmark" type="button" @click="goRoom">
        <strong>暗室·藏书</strong>
        <span>{{ theme === "night" ? "灯下" : "天光" }}</span>
      </button>

      <div class="quiet-actions">
        <button type="button" :title="themeToggleTitle" @click="switchTheme">
          <component :is="themeIcon" />
        </button>
        <button type="button" title="个人资料" @click="openProfile">
          <el-avatar :size="30" :src="getAvatarUrl(userInfo.url)">{{ avatarText }}</el-avatar>
        </button>
        <button type="button" title="退出登录" @click="loginOut">
          <SwitchButton />
        </button>
      </div>
    </header>

    <main class="reader-stage" :class="{ 'room-stage': isRoom }">
      <router-view v-slot="{ Component }">
        <transition name="page-shift" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <nav class="edge-nav" aria-label="读者导航">
      <span class="nav-thread" aria-hidden="true"></span>
      <button
        v-for="item in navItems"
        :key="item.key"
        :class="{ active: isActive(item) }"
        type="button"
        :title="item.functional"
        @click="go(item.path)"
      >
        <span class="nav-label">{{ item.label }}</span>
        <component :is="item.icon" />
        <i aria-hidden="true"></i>
      </button>
    </nav>

    <nav class="mobile-nav" aria-label="移动端读者导航">
      <button
        v-for="item in mobilePrimaryItems"
        :key="item.key"
        :class="{ active: isActive(item) }"
        type="button"
        @click="go(item.path)"
      >
        <component :is="item.icon" />
        <span>{{ item.mobileLabel }}</span>
      </button>
      <button type="button" :class="{ active: moreOpen }" @click="moreOpen = !moreOpen">
        <MoreIcon />
        <span>更多</span>
      </button>
    </nav>

    <transition name="page-shift">
      <aside v-if="moreOpen" class="mobile-more">
        <button v-for="item in mobileMoreItems" :key="item.key" type="button" @click="goFromMore(item.path)">
          <component :is="item.icon" />{{ item.label }}
        </button>
        <button type="button" @click="openProfileFromMore"><User />个人资料</button>
        <button type="button" @click="switchTheme"><Sunny />{{ themeToggleTitle }}</button>
        <button type="button" @click="loginOut"><SwitchButton />退出登录</button>
      </aside>
    </transition>

    <ProfileDialog v-model="dialogOperation" :user-info="userInfo" @saved="tokenCheckLoad" />
  </div>
</template>

<script>
import { markRaw } from "vue";
import request from "@/utils/request.js";
import { clearAuthSession, setUserProfile } from "@/utils/storage";
import { resolveFileUrl } from "@/utils/fileUrl.js";
import { getReaderTheme, toggleReaderTheme } from "@/utils/readerTheme.js";
import ProfileDialog from "@/components/ProfileDialog.vue";
import {
  Collection,
  House,
  Menu as MoreIcon,
  Message,
  Moon,
  Reading,
  Search,
  Star,
  SwitchButton,
  Sunny,
  Timer,
  User,
} from "@element-plus/icons-vue";

const READER_NAV_ITEMS = Object.freeze([
  { key: "room", path: "/readerRoom", label: "藏书室", mobileLabel: "首页", functional: "返回藏书室", icon: markRaw(House) },
  { key: "search", path: "/bookSearch", label: "查找藏书", mobileLabel: "找书", functional: "检索图书", icon: markRaw(Search) },
  { key: "borrows", path: "/myBorrows", label: "我的借阅", mobileLabel: "借阅", functional: "借阅与归还", icon: markRaw(Collection) },
  { key: "reservations", path: "/myReservations", label: "我的预约", mobileLabel: "预约", functional: "候书队列", icon: markRaw(Timer) },
  { key: "favorites", path: "/myFavorites", label: "我的收藏", mobileLabel: "收藏", functional: "私人书架", icon: markRaw(Star) },
  { key: "reviews", path: "/bookReviews", label: "书评", mobileLabel: "书评", functional: "读者书评", icon: markRaw(Reading) },
  { key: "message", path: "/messageBoard", label: "留言", mobileLabel: "留言", functional: "读者留言", icon: markRaw(Message) },
]);

export default {
  name: "UserHome",
  components: { ProfileDialog, MoreIcon, User, Moon, Sunny, SwitchButton },
  data() {
    return {
      theme: getReaderTheme(),
      moreOpen: false,
      dialogOperation: false,
      userInfo: { id: null, url: "", name: "", role: null, email: "" },
      navItems: READER_NAV_ITEMS,
    };
  },
  computed: {
    isRoom() {
      return this.$route.path === "/readerRoom";
    },
    avatarText() {
      return this.userInfo.name ? this.userInfo.name.charAt(0) : "读";
    },
    themeToggleTitle() {
      return this.theme === "night" ? "切换至天光" : "切换至灯下";
    },
    themeIcon() {
      return this.theme === "night" ? "Sunny" : "Moon";
    },
    mobilePrimaryItems() {
      return [this.navItems[0], this.navItems[1], this.navItems[2], this.navItems[5]];
    },
    mobileMoreItems() {
      return [this.navItems[3], this.navItems[4], this.navItems[6]];
    },
  },
  watch: {
    $route() {
      this.moreOpen = false;
    },
  },
  created() {
    this.tokenCheckLoad();
  },
  methods: {
    getAvatarUrl(url) {
      return resolveFileUrl(url);
    },
    switchTheme() {
      this.theme = toggleReaderTheme(this.theme);
    },
    goRoom() {
      this.go("/readerRoom");
    },
    go(path) {
      if (path && this.$route.path !== path) this.$router.push(path);
    },
    goFromMore(path) {
      this.moreOpen = false;
      this.go(path);
    },
    isActive(item) {
      return this.$route.path === item.path;
    },
    openProfile() {
      this.dialogOperation = true;
    },
    openProfileFromMore() {
      this.moreOpen = false;
      this.openProfile();
    },
    async loginOut() {
      const confirmed = await this.$swalConfirm({
        title: "退出登录？",
        text: "退出后需要重新登录。",
        icon: "warning",
      });
      if (!confirmed) return;
      clearAuthSession();
      this.$router.push("/login");
    },
    async tokenCheckLoad() {
      try {
        const res = await request.get("/user/auth");
        if (res.data.code !== 200) {
          clearAuthSession();
          this.$router.push("/login");
          return;
        }
        const { id, userAvatar, userName, userRole, userEmail } = res.data.data;
        this.userInfo = {
          id,
          url: userAvatar || "",
          name: userName || "",
          role: userRole,
          email: userEmail || "",
        };
        setUserProfile(this.userInfo);
      } catch (error) {
        console.error("获取用户认证信息失败:", error);
        this.$message.error("认证信息加载失败，请稍后重试。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.reader-shell {
  position: relative;
  min-height: 100vh;
  overflow-x: hidden;
  color: var(--scene-text);
  background: var(--scene-base);
}

.scene-image,
.scene-veil {
  position: fixed;
  inset: 0;
  pointer-events: none;
}

.scene-image {
  z-index: 0;
  background-image: url("../../assets/images/reading-room-night-v2.webp");
  background-position: center;
  background-size: cover;
  filter: brightness(1.18) saturate(0.9);
  transform: scale(1.015);
  transition: opacity 0.8s ease, filter 0.8s ease;
}

.reader-shell[data-reader-theme="day"] .scene-image {
  background-image: url("../../assets/images/reading-room-day-v2.webp");
  filter: brightness(1) saturate(0.82);
}

.scene-veil {
  z-index: 1;
  background: linear-gradient(180deg, rgba(5, 5, 4, 0.28), transparent 24%, transparent 72%, rgba(5, 5, 4, 0.24));
  transition: background 0.8s ease;
}

.reader-shell[data-reader-theme="day"] .scene-veil {
  background: linear-gradient(180deg, rgba(246, 244, 236, 0.32), transparent 27%, transparent 70%, rgba(232, 229, 217, 0.28));
}

.reader-shell:not(.room-route) .scene-image {
  filter: blur(5px) saturate(0.72);
  opacity: 0.34;
}

.reader-shell:not(.room-route) .scene-veil {
  background: color-mix(in srgb, var(--scene-base) 72%, transparent);
  backdrop-filter: blur(2px);
}

.reader-shell[data-reader-theme="day"]:not(.room-route) .scene-veil {
  background: rgba(231, 233, 227, 0.5);
  backdrop-filter: blur(1.5px);
}

.reader-header {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 74px;
  padding: 0 34px;
  pointer-events: none;
}

.reader-header button { pointer-events: auto; }

.wordmark {
  display: flex;
  align-items: baseline;
  gap: 11px;
  padding: 8px 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  text-shadow: 0 1px 18px var(--scene-shadow);
}

.wordmark strong {
  font-family: var(--reader-serif);
  font-size: 19px;
  font-weight: 500;
}

.wordmark span { font-size: 11px; opacity: 0.55; }

.quiet-actions {
  display: flex;
  align-items: center;
  gap: 3px;
  opacity: 0.6;
  transition: opacity 0.25s ease;
}

.quiet-actions:hover { opacity: 1; }

.quiet-actions button {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.quiet-actions svg { width: 17px; }
.quiet-actions .el-avatar { border: 1px solid color-mix(in srgb, currentColor 25%, transparent); }

.reader-stage {
  position: relative;
  z-index: 5;
  width: min(1180px, calc(100% - 180px));
  min-height: 100vh;
  margin: 0 auto;
  padding: 104px 0 80px;
}

.reader-stage.room-stage {
  width: 100%;
  padding: 0;
}

.edge-nav {
  position: fixed;
  top: 50%;
  right: 22px;
  z-index: 32;
  display: grid;
  gap: 4px;
  transform: translateY(-50%);
}

.nav-thread {
  position: absolute;
  top: 17px;
  right: 6px;
  bottom: 17px;
  width: 1px;
  background: color-mix(in srgb, currentColor 16%, transparent);
}

.edge-nav button {
  position: relative;
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  opacity: 0.42;
  transition: opacity 0.22s ease;
}

.edge-nav button:hover,
.edge-nav button.active { opacity: 1; }
.edge-nav svg { width: 16px; }

.edge-nav i {
  position: absolute;
  right: 3px;
  width: 7px;
  height: 7px;
  border: 1px solid currentColor;
  border-radius: 50%;
  background: var(--scene-base);
  transform: scale(0.55);
  transition: transform 0.22s ease, background 0.22s ease;
}

.edge-nav button.active i {
  background: var(--accent);
  border-color: var(--accent);
  box-shadow: 0 0 12px var(--accent-glow);
  transform: scale(0.9);
}

.nav-label {
  position: absolute;
  right: 42px;
  width: max-content;
  padding-right: 10px;
  font-family: var(--reader-serif);
  font-size: 13px;
  opacity: 0;
  transform: translateX(6px);
  transition: opacity 0.2s ease, transform 0.2s ease;
  pointer-events: none;
  text-shadow: 0 1px 12px var(--scene-shadow);
}

.edge-nav button:hover .nav-label,
.edge-nav button:focus-visible .nav-label { opacity: 1; transform: translateX(0); }

.mobile-nav,
.mobile-more { display: none; }

.page-shift-enter-active,
.page-shift-leave-active { transition: opacity 0.28s ease, transform 0.28s ease; }
.page-shift-enter-from { opacity: 0; transform: translateY(8px); }
.page-shift-leave-to { opacity: 0; transform: translateY(-5px); }

@media (max-width: 820px) {
  .reader-header { height: 60px; padding: 0 18px; }
  .wordmark strong { font-size: 17px; }
  .quiet-actions button:nth-child(3) { display: none; }
  .edge-nav { display: none; }

  .reader-stage,
  .reader-stage.room-stage {
    width: 100%;
    min-height: 100vh;
    padding: 78px 14px 84px;
  }

  .reader-stage.room-stage { padding: 0 0 70px; }

  .mobile-nav {
    position: fixed;
    right: 10px;
    bottom: 10px;
    left: 10px;
    z-index: 40;
    height: 58px;
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    border: 1px solid color-mix(in srgb, var(--paper-ink) 10%, transparent);
    background: color-mix(in srgb, var(--paper) 94%, transparent);
    box-shadow: 0 12px 34px var(--scene-shadow);
    backdrop-filter: blur(16px);
  }

  .mobile-nav button {
    display: grid;
    place-items: center;
    align-content: center;
    gap: 3px;
    padding: 0;
    border: 0;
    color: var(--paper-ink-soft);
    background: transparent;
  }

  .mobile-nav button.active { color: var(--seal); }
  .mobile-nav svg { width: 17px; }
  .mobile-nav span { font-size: 10px; }

  .mobile-more {
    position: fixed;
    right: 10px;
    bottom: 76px;
    z-index: 39;
    width: min(250px, calc(100vw - 20px));
    display: grid;
    padding: 10px;
    border: 1px solid color-mix(in srgb, var(--paper-ink) 12%, transparent);
    color: var(--paper-ink);
    background: var(--paper);
    box-shadow: 0 18px 45px var(--scene-shadow);
  }

  .mobile-more button {
    min-height: 42px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 12px;
    border: 0;
    color: inherit;
    background: transparent;
    text-align: left;
  }

  .mobile-more svg { width: 17px; }
}
</style>
