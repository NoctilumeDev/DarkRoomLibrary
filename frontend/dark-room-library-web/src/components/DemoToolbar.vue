<template>
  <aside
    v-if="visible"
    class="demo-toolbar"
    :class="[{ open }, surfaceClass]"
    aria-label="在线演示控制"
  >
    <button
      class="demo-toggle"
      type="button"
      :aria-expanded="open"
      title="在线演示控制"
      @click="open = !open"
    >
      <Monitor aria-hidden="true" />
      <span>在线演示</span>
    </button>

    <section v-if="open" class="demo-panel">
      <header>
        <div>
          <small>在线验收</small>
          <strong>{{ activeIdentity?.label || "选择身份" }}</strong>
        </div>
        <button type="button" title="关闭演示控制" @click="open = false">
          <Close aria-hidden="true" />
        </button>
      </header>

      <label id="demo-identity-label">切换验收身份</label>
      <el-select
        v-model="selectedIdentity"
        class="demo-identity-select"
        aria-labelledby="demo-identity-label"
        popper-class="demo-identity-popper"
        @change="switchIdentity"
      >
        <el-option
          v-for="identity in identities"
          :key="identity.key"
          :label="`${identity.label} · ${identity.name}`"
          :value="identity.key"
        />
      </el-select>

      <p>数据仅保存在当前浏览器会话；文件、邮件与账号注销不会执行。</p>

      <button class="demo-reset" type="button" @click="resetDemo">
        <RefreshLeft aria-hidden="true" />
        <span>重置演示</span>
      </button>
    </section>
  </aside>
</template>

<script>
import {
  activateDemoIdentity,
  DEMO_IDENTITIES,
  DEMO_IDENTITY_EVENT,
  DEMO_MODE,
  getActiveDemoIdentity,
  resetDemoRuntime,
} from "@/demo/runtime.js";
import { getToken } from "@/utils/storage.js";
import { resolveRoleHome } from "@/utils/roleHome.js";
import { Close, Monitor, RefreshLeft } from "@element-plus/icons-vue";

export default {
  name: "DemoToolbar",
  components: { Close, Monitor, RefreshLeft },
  data() {
    const activeIdentity = getActiveDemoIdentity();
    return {
      open: false,
      activeIdentity,
      selectedIdentity: activeIdentity?.key || "reader",
      authenticated: Boolean(getToken()),
      identities: DEMO_IDENTITIES,
    };
  },
  computed: {
    visible() {
      return DEMO_MODE && this.authenticated;
    },
    surfaceClass() {
      const parentPath = this.$route.matched[0]?.path;
      if (parentPath === "/user") return "demo-toolbar--reader";
      if (parentPath === "/admin") return "demo-toolbar--admin";
      if (parentPath === "/procurement") return "demo-toolbar--staff";
      return "demo-toolbar--standalone";
    },
  },
  watch: {
    "$route.fullPath"() {
      this.syncIdentity();
    },
  },
  mounted() {
    window.addEventListener(DEMO_IDENTITY_EVENT, this.syncIdentity);
  },
  beforeUnmount() {
    window.removeEventListener(DEMO_IDENTITY_EVENT, this.syncIdentity);
  },
  methods: {
    syncIdentity() {
      this.activeIdentity = getActiveDemoIdentity();
      this.selectedIdentity = this.activeIdentity?.key || "reader";
      this.authenticated = Boolean(getToken());
    },
    switchIdentity() {
      const identity = activateDemoIdentity(this.selectedIdentity);
      const path = resolveRoleHome(identity?.role);
      if (!path) return;
      this.$router.replace(path).finally(() => this.$router.go(0));
    },
    resetDemo() {
      resetDemoRuntime();
      this.open = false;
      this.$router.replace("/login").finally(() => this.$router.go(0));
    },
  },
};
</script>

<style scoped lang="scss">
.demo-toolbar {
  --demo-panel: #f4ecde;
  --demo-field: #fffaf0;
  --demo-text: #2d2923;
  --demo-text-soft: #62594e;
  --demo-text-muted: #665d51;
  --demo-accent: #824034;
  --demo-accent-soft: rgba(130, 64, 52, 0.1);
  --demo-border: rgba(80, 61, 39, 0.24);
  --demo-shadow: rgba(18, 15, 12, 0.24);
  --demo-toggle: rgba(44, 39, 33, 0.92);
  --demo-toggle-text: #f8f2e7;
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 120;
  color: var(--demo-text);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
}

.demo-toolbar--admin {
  --demo-panel: var(--admin-paper-light);
  --demo-field: var(--admin-paper);
  --demo-text: var(--admin-ink);
  --demo-text-soft: var(--admin-ink-soft);
  --demo-text-muted: var(--admin-ink-muted);
  --demo-accent: var(--admin-seal);
  --demo-accent-soft: var(--admin-accent-soft);
  --demo-border: var(--admin-line-strong);
  --demo-shadow: var(--admin-shadow);
  --demo-toggle: color-mix(in srgb, var(--admin-paper-light) 94%, transparent);
  --demo-toggle-text: var(--admin-ink);
}

.demo-toolbar--reader {
  --demo-panel: var(--paper-raised);
  --demo-field: var(--paper);
  --demo-text: var(--paper-ink);
  --demo-text-soft: var(--paper-ink-soft);
  --demo-text-muted: var(--paper-ink-faint);
  --demo-accent: var(--seal);
  --demo-accent-soft: color-mix(in srgb, var(--seal) 10%, transparent);
  --demo-border: var(--paper-line);
  --demo-shadow: var(--scene-shadow);
  --demo-toggle: color-mix(in srgb, var(--scene-base) 90%, transparent);
  --demo-toggle-text: var(--scene-text);
}

.demo-toggle,
.demo-panel button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.demo-toggle {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid var(--demo-border);
  border-radius: 4px;
  color: var(--demo-toggle-text);
  background: var(--demo-toggle);
  box-shadow: 0 10px 28px color-mix(in srgb, var(--demo-shadow) 86%, transparent);
  backdrop-filter: blur(12px);
  transition: color 0.24s ease, border-color 0.24s ease, background-color 0.24s ease;
}

.demo-toggle svg,
.demo-panel svg {
  width: 16px;
}

.demo-panel {
  position: absolute;
  bottom: 44px;
  left: 0;
  width: min(310px, calc(100vw - 32px));
  padding: 16px;
  border: 1px solid var(--demo-border);
  border-radius: 6px;
  background: var(--demo-panel);
  box-shadow: 0 18px 50px var(--demo-shadow);
  transition: color 0.24s ease, border-color 0.24s ease, background-color 0.24s ease;
}

.demo-panel header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.demo-panel header small,
.demo-panel header strong {
  display: block;
}

.demo-panel header small {
  color: var(--demo-accent);
  font-size: 10px;
  font-weight: 700;
}

.demo-panel header strong {
  margin-top: 3px;
  font-size: 18px;
  font-weight: 600;
}

.demo-panel header button {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--demo-text-soft);
  background: transparent;
}

.demo-panel label {
  display: block;
  margin-bottom: 6px;
  color: var(--demo-text-soft);
  font-size: 11px;
}

.demo-identity-select {
  width: 100%;

  :deep(.el-select__wrapper) {
    min-height: 36px;
    padding-inline: 10px;
    border-radius: 3px;
    color: var(--demo-text);
    background: var(--demo-field);
    box-shadow: 0 0 0 1px var(--demo-border) inset;
    font-family: inherit;
    font-size: 13px;
  }

  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px var(--demo-accent) inset !important;
  }

  :deep(.el-select__selected-item),
  :deep(.el-select__placeholder),
  :deep(.el-select__caret) {
    color: var(--demo-text);
  }
}

// 下拉浮层挂载在 body 下，不能继承 DemoToolbar 的局部变量；先给 staff / standalone
// 提供宣纸基线，再按 body 上的真实页面主题接入管理员或读者 token。
:global(body .demo-identity-popper) {
  --demo-popper-panel: #fffaf0;
  --demo-popper-text: #4f473c;
  --demo-popper-active: #2d2923;
  --demo-popper-accent: #824034;
  --demo-popper-accent-soft: rgba(130, 64, 52, 0.1);
  --demo-popper-border: rgba(80, 61, 39, 0.24);
  --demo-popper-shadow: rgba(43, 34, 24, 0.2);
}

:global(body[data-admin-theme] .demo-identity-popper) {
  --demo-popper-panel: var(--admin-paper-light);
  --demo-popper-text: var(--admin-ink-soft);
  --demo-popper-active: var(--admin-ink);
  --demo-popper-accent: var(--admin-seal);
  --demo-popper-accent-soft: var(--admin-accent-soft);
  --demo-popper-border: var(--admin-line-strong);
  --demo-popper-shadow: var(--admin-shadow);
}

:global(body[data-reader-theme] .demo-identity-popper) {
  --demo-popper-panel: var(--paper-raised);
  --demo-popper-text: var(--paper-ink-soft);
  --demo-popper-active: var(--paper-ink);
  --demo-popper-accent: var(--seal);
  --demo-popper-accent-soft: color-mix(in srgb, var(--seal) 10%, transparent);
  --demo-popper-border: var(--paper-line);
  --demo-popper-shadow: var(--scene-shadow);
}

:global(body .demo-identity-popper.el-popper.is-light) {
  border-color: var(--demo-popper-border) !important;
  background: var(--demo-popper-panel) !important;
  box-shadow: 0 14px 34px var(--demo-popper-shadow) !important;
}

:global(body .demo-identity-popper .el-popper__arrow::before) {
  border-color: var(--demo-popper-border) !important;
  background: var(--demo-popper-panel) !important;
}

:global(body .demo-identity-popper .el-select-dropdown__item) {
  color: var(--demo-popper-text) !important;
  background: transparent !important;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
}

:global(body .demo-identity-popper .el-select-dropdown__item.is-hovering),
:global(body .demo-identity-popper .el-select-dropdown__item:hover) {
  color: var(--demo-popper-active) !important;
  background: var(--demo-popper-accent-soft) !important;
}

:global(body .demo-identity-popper .el-select-dropdown__item.is-selected) {
  color: var(--demo-popper-accent) !important;
  background: var(--demo-popper-accent-soft) !important;
  font-weight: 600;
}

.demo-panel p {
  margin: 12px 0;
  color: var(--demo-text-muted);
  font-size: 11px;
  line-height: 1.7;
}

.demo-reset {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border: 1px solid var(--demo-border) !important;
  border-radius: 3px;
  color: var(--demo-accent);
  background: var(--demo-accent-soft);
}

@media (max-width: 820px) {
  .demo-toolbar {
    top: auto;
    right: 10px;
    bottom: calc(12px + env(safe-area-inset-bottom, 0px));
    left: auto;
  }

  .demo-toolbar--reader {
    top: 12px;
    right: 124px;
    bottom: auto;
  }

  .demo-toolbar--admin {
    top: 14px;
    right: 14px;
    bottom: auto;
  }

  .demo-toolbar--staff {
    top: 23px;
    right: 136px;
    bottom: auto;
  }

  .demo-panel {
    position: fixed;
    top: auto;
    right: 12px;
    bottom: calc(58px + env(safe-area-inset-bottom, 0px));
    left: 12px;
    width: auto;
    max-width: none;
    box-sizing: border-box;
  }

  .demo-toolbar--reader .demo-panel,
  .demo-toolbar--staff .demo-panel {
    top: 66px;
    right: 12px;
    bottom: auto;
    left: 12px;
  }

  // 管理端头部在窄屏会折成三行；若面板仍从 66px 开始，会挡住主题切换。
  // 将它放到视口底部，保留头部控制权，也让下拉层有空间自动向上展开。
  .demo-toolbar--admin .demo-panel {
    top: auto;
    right: 12px;
    bottom: calc(12px + env(safe-area-inset-bottom, 0px));
    left: 12px;
  }

  .demo-toggle span {
    display: none;
  }

  .demo-toggle {
    width: 38px;
    padding: 0;
    justify-content: center;
  }
}
</style>
