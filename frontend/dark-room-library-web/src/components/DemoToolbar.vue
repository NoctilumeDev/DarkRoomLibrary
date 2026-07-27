<template>
  <aside v-if="visible" class="demo-toolbar" :class="{ open }" aria-label="在线演示控制">
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
          <small>BROWSER DEMO</small>
          <strong>{{ activeIdentity?.label || "选择身份" }}</strong>
        </div>
        <button type="button" title="关闭演示控制" @click="open = false">
          <Close aria-hidden="true" />
        </button>
      </header>

      <label for="demo-identity">切换验收身份</label>
      <select id="demo-identity" v-model="selectedIdentity" @change="switchIdentity">
        <option
          v-for="identity in identities"
          :key="identity.key"
          :value="identity.key"
        >
          {{ identity.label }} · {{ identity.name }}
        </option>
      </select>

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
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 120;
  color: #2d2923;
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
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
  border: 1px solid rgba(80, 61, 39, 0.24);
  border-radius: 4px;
  color: #f8f2e7;
  background: rgba(44, 39, 33, 0.92);
  box-shadow: 0 10px 28px rgba(18, 15, 12, 0.2);
  backdrop-filter: blur(12px);
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
  border: 1px solid rgba(80, 61, 39, 0.2);
  border-radius: 6px;
  background: #f4ecde;
  box-shadow: 0 18px 50px rgba(18, 15, 12, 0.24);
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
  color: #865044;
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
  color: #62594e;
  background: transparent;
}

.demo-panel label {
  display: block;
  margin-bottom: 6px;
  color: #62594e;
  font-size: 11px;
}

.demo-panel select {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid rgba(80, 61, 39, 0.22);
  border-radius: 3px;
  color: #2d2923;
  background: #fffaf0;
  font: inherit;
  font-size: 13px;
}

.demo-panel p {
  margin: 12px 0;
  color: #665d51;
  font-size: 11px;
  line-height: 1.7;
}

.demo-reset {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 10px;
  border: 1px solid rgba(134, 80, 68, 0.24) !important;
  border-radius: 3px;
  color: #824034;
  background: rgba(255, 250, 240, 0.54);
}

@media (max-width: 820px) {
  .demo-toolbar {
    top: 68px;
    right: 10px;
    bottom: auto;
    left: auto;
  }

  .demo-panel {
    top: 44px;
    right: 0;
    bottom: auto;
    left: auto;
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
