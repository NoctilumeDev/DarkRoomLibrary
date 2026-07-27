<template>
  <div class="staff-shell">
    <header class="staff-header">
      <button class="brand" type="button" @click="$router.push('/procurementWorkbench')">
        <span class="seal">{{ roleSeal }}</span>
        <span><strong>暗室·藏书</strong><em>{{ roleLabel }}工作台 · 协作流转</em></span>
      </button>
      <div class="staff-user">
        <button class="profile-button" type="button" @click="profileVisible=true">{{ userInfo.name || roleLabel }}</button>
        <button class="logout-button" type="button" @click="logout">退出</button>
      </div>
    </header>
    <main class="staff-workspace"><router-view /></main>
    <ProfileDialog v-model="profileVisible" :user-info="userInfo" @saved="loadAuth" />
  </div>
</template>

<script>
import ProfileDialog from "@/components/ProfileDialog.vue";
import { clearAuthSession } from "@/utils/storage";
import { USER_ROLE } from "@/utils/userRoles.js";

export default {
  name: "ProcurementHome",
  components: { ProfileDialog },
  data() { return { profileVisible: false, userInfo: { id: null, role: null, name: "", email: "", url: "" } }; },
  computed: {
    roleLabel() { return this.userInfo.role === USER_ROLE.LOGISTICS ? "物流" : "采购"; },
    roleSeal() { return this.userInfo.role === USER_ROLE.LOGISTICS ? "运" : "筹"; },
  },
  created() { this.loadAuth(); },
  methods: {
    async loadAuth() {
      const response = await this.$axios.get("/user/auth");
      const user = response.data.data || {};
      this.userInfo = { id: user.id, role: user.userRole, name: user.userName, email: user.userEmail, url: user.userAvatar };
    },
    async logout() {
      const confirmed = await this.$swalConfirm({ title: "退出工作台？", text: "退出后需要重新登录。", icon: "warning" });
      if (!confirmed) return;
      clearAuthSession(); this.$router.push("/login");
    },
  },
};
</script>

<style scoped lang="scss">
.staff-shell {
  --admin-text: #2d2923;
  --admin-text-secondary: #4f473c;
  --admin-muted: #665d51;
  --admin-border: rgba(72, 58, 41, 0.17);
  --admin-border-strong: rgba(72, 58, 41, 0.28);
  --admin-surface: #f3eadb;
  --admin-surface-strong: #fbf5e9;
  --admin-surface-muted: #e9ddca;
  --admin-accent: #93483a;
  --admin-accent-solid: #824034;
  --admin-accent-soft: rgba(147, 72, 58, 0.09);
  --admin-gold: #72562f;
  position: relative;
  min-height: 100vh;
  padding: 18px 28px 38px;
  overflow-x: hidden;
  color: var(--admin-text);
  background:
    radial-gradient(ellipse at 14% 0%, rgba(255, 252, 243, 0.74), transparent 34%),
    linear-gradient(180deg, #eadfce, #dfd1bd);
  font-family: "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
}

.staff-shell::before {
  content: "";
  position: fixed;
  inset: 0;
  pointer-events: none;
  opacity: 0.32;
  background:
    repeating-linear-gradient(0deg, rgba(74, 58, 38, 0.018) 0 1px, transparent 1px 9px),
    radial-gradient(circle at 27% 34%, rgba(67, 51, 32, 0.04) 0 0.6px, transparent 0.8px);
  background-size: auto, 6px 6px;
}

.staff-header {
  position: sticky;
  top: 18px;
  z-index: 20;
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 1560px;
  margin: 0 auto 22px;
  padding: 12px 16px 13px;
  border: 0;
  border-bottom: 1px solid var(--admin-border-strong);
  border-radius: 0;
  background: color-mix(in srgb, var(--admin-surface-strong) 88%, transparent);
  box-shadow: 0 12px 34px rgba(76, 58, 37, 0.09);
  backdrop-filter: blur(12px);
}
.brand, .staff-user button { border: 0; background: transparent; cursor: pointer; font-family: inherit; }
.brand { display: flex; gap: 11px; align-items: center; text-align: left; color: var(--admin-text); }
.brand strong, .brand em { display: block; }
.brand strong { font-size: 18px; font-weight: 600; }
.brand em { margin-top: 2px; color: var(--admin-muted); font-size: 12px; font-style: normal; }
.seal { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid var(--admin-accent); border-radius: 2px; color: var(--admin-accent); background: rgba(255, 250, 240, 0.34); font-weight: 700; transform: rotate(-1deg); }
.staff-user { display: flex; gap: 16px; }
.staff-user button { padding: 7px 2px; color: var(--admin-text-secondary); }
.staff-user button:hover { color: var(--admin-accent); }
.logout-button { border-bottom: 1px solid var(--admin-border) !important; }

.staff-workspace {
  position: relative;
  z-index: 1;
  max-width: 1560px;
  min-height: calc(100vh - 126px);
  margin: 0 auto;
  padding: clamp(24px, 3vw, 40px);
  background:
    linear-gradient(90deg, rgba(117, 86, 47, 0.025), transparent 7%, transparent 93%, rgba(117, 86, 47, 0.025)),
    repeating-linear-gradient(0deg, rgba(74, 58, 38, 0.015) 0 1px, transparent 1px 9px),
    var(--admin-surface);
  box-shadow:
    0 26px 72px rgba(75, 57, 36, 0.14),
    0 0 0 1px rgba(72, 58, 41, 0.1),
    inset 0 0 48px rgba(255, 251, 242, 0.34);
}

@media (max-width: 720px) {
  .staff-shell { padding: 10px 10px 24px; }
  .staff-header { top: 10px; gap: 12px; padding-inline: 10px; }
  .brand em { display: none; }
  .staff-user { gap: 10px; }
  .staff-workspace { padding: 22px 14px; }
}
</style>
