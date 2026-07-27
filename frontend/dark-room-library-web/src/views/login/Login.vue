<template>
  <main
    class="auth-page auth-world"
    :class="{ 'paper-open': paperOpen, 'paper-leaving': paperLeaving }"
    :data-reader-theme="theme"
    @pointermove="trackLight"
    @pointerleave="settleLight"
  >
    <div class="door-scene" aria-hidden="true"></div>
    <div class="door-reveal" aria-hidden="true"></div>
    <div class="door-mist" aria-hidden="true"></div>

    <button class="auth-theme-toggle" type="button" :title="themeTitle" @click="switchTheme">
      <component :is="themeIcon" />
    </button>

    <section v-if="!paperOpen" class="threshold">
      <span class="threshold-brand">暗室·藏书</span>
      <button class="threshold-entry" type="button" @click="revealPaper">
        <span class="threshold-light" aria-hidden="true"></span>
        <span class="threshold-word">循光而入</span>
      </button>
    </section>

    <section v-else class="paper-sheet" aria-label="暗室藏书登录">
      <button class="back-to-door" type="button" @click="returnToDoor">返回门外</button>
      <div class="paper-copy">
        <p>暗室·藏书</p>
        <h1>叩门</h1>
        <blockquote>灯不必照亮整间屋子，足以让人看见下一页。</blockquote>
        <span>读者可自行注册；其他身份由馆内授予。</span>
      </div>

      <section class="auth-card">
        <header>
          <h2>请进</h2>
          <p>读者登录 · 使用账号、密码与验证题</p>
        </header>

        <el-form label-width="60px" @submit.prevent>
          <el-form-item label="登录账号">
            <el-input
              v-model.trim="act"
              size="large"
              placeholder="账号"
              maxlength="32"
              autocomplete="username"
              @keyup.enter="login"
            />
          </el-form-item>
          <el-form-item label="登录密码">
            <el-input
              v-model="pwd"
              size="large"
              type="password"
              placeholder="密码"
              autocomplete="current-password"
              show-password
              @keyup.enter="login"
            />
          </el-form-item>
          <el-form-item class="captcha-form-item">
            <template #label><span class="captcha-label">验证题</span></template>
            <div class="captcha-row">
              <button
                class="captcha-question"
                type="button"
                title="换一题"
                :disabled="captchaLoading"
                @click="retryCaptcha"
              >
                <span>{{ captchaExpression || "加载验证题" }}</span>
                <Refresh class="captcha-refresh" aria-hidden="true" />
                <small>换一题</small>
              </button>
              <el-input
                v-model.trim="captchaAnswer"
                size="large"
                type="number"
                placeholder="答案"
                @keyup.enter="login"
              />
            </div>
          </el-form-item>
          <el-button class="entry-button" size="large" :loading="loading" @click="login">
            进入藏书室
          </el-button>
        </el-form>

        <div class="auth-links">
          <button type="button" @click="toDoRegister">注册读者</button>
          <button type="button" @click="toResetPwd">忘记密码</button>
        </div>
      </section>
    </section>
  </main>
</template>

<script>
import request from "@/utils/request.js";
import { resolveRoleHome } from "@/utils/roleHome.js";
import { clearAuthSession, getToken, setToken } from "@/utils/storage.js";
import { getReaderTheme, toggleReaderTheme } from "@/utils/readerTheme.js";
import { prefersReducedMotion } from "@/utils/viewTransition.js";
import { Moon, Refresh, Sunny } from "@element-plus/icons-vue";

const DELAY_TIME = 1200;

export default {
  name: "Login",
  components: { Moon, Refresh, Sunny },
  data() {
    return {
      theme: getReaderTheme(),
      paperOpen: sessionStorage.getItem("auth-intro-seen") === "1",
      paperLeaving: false,
      act: "",
      pwd: "",
      captchaId: "",
      captchaExpression: "",
      captchaAnswer: "",
      captchaLoading: false,
      captchaRetryCount: 0,
      captchaRetryTimer: null,
      loading: false,
      lightFrame: null,
      lightMotion: {
        currentX: 21.87,
        currentY: 56.72,
        targetX: 21.87,
        targetY: 56.72,
      },
    };
  },
  computed: {
    themeIcon() {
      return this.theme === "night" ? "Sunny" : "Moon";
    },
    themeTitle() {
      return this.theme === "night" ? "切换至天光" : "切换至灯下";
    },
  },
  created() {
    this.defaultLoad();
    this.fetchCaptcha();
  },
  mounted() {
    this.syncLightToLamp();
    window.addEventListener("resize", this.syncLightToLamp);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.syncLightToLamp);
    if (this.captchaRetryTimer) clearTimeout(this.captchaRetryTimer);
    if (this.lightFrame) cancelAnimationFrame(this.lightFrame);
  },
  methods: {
    switchTheme() {
      this.theme = toggleReaderTheme(this.theme);
    },
    revealPaper() {
      this.paperOpen = true;
      sessionStorage.setItem("auth-intro-seen", "1");
    },
    returnToDoor() {
      this.paperOpen = false;
      sessionStorage.removeItem("auth-intro-seen");
      this.$nextTick(this.syncLightToLamp);
    },
    trackLight(event) {
      if (this.paperOpen || prefersReducedMotion() || event.pointerType === "touch") return;
      const bounds = this.$el.getBoundingClientRect();
      const x = ((event.clientX - bounds.left) / bounds.width) * 100;
      const y = ((event.clientY - bounds.top) / bounds.height) * 100;
      const lamp = this.getLampPosition();
      this.setLightTarget(
        lamp.x + (x - 50) * 0.12,
        lamp.y + (y - 50) * 0.08
      );
    },
    settleLight() {
      if (!this.paperOpen) {
        const lamp = this.getLampPosition();
        this.setLightTarget(lamp.x, lamp.y);
      }
    },
    getLampPosition() {
      if (!this.$el) return { x: 21.87, y: 56.72 };
      const styles = getComputedStyle(this.$el);
      return {
        x: Number.parseFloat(styles.getPropertyValue("--lamp-x")) || 21.87,
        y: Number.parseFloat(styles.getPropertyValue("--lamp-y")) || 56.72,
      };
    },
    syncLightToLamp() {
      if (!this.$el || this.paperOpen) return;
      const lamp = this.getLampPosition();
      Object.assign(this.lightMotion, {
        currentX: lamp.x,
        currentY: lamp.y,
        targetX: lamp.x,
        targetY: lamp.y,
      });
      this.applyLightPosition();
    },
    setLightTarget(x, y) {
      this.lightMotion.targetX = x;
      this.lightMotion.targetY = y;
      if (!this.lightFrame) {
        this.lightFrame = requestAnimationFrame(this.animateLight);
      }
    },
    animateLight() {
      this.lightFrame = null;
      const motion = this.lightMotion;
      motion.currentX += (motion.targetX - motion.currentX) * 0.065;
      motion.currentY += (motion.targetY - motion.currentY) * 0.065;
      this.applyLightPosition();

      if (
        Math.abs(motion.targetX - motion.currentX) > 0.08
        || Math.abs(motion.targetY - motion.currentY) > 0.08
      ) {
        this.lightFrame = requestAnimationFrame(this.animateLight);
      }
    },
    applyLightPosition() {
      if (!this.$el) return;
      this.$el.style.setProperty("--reader-light-x", `${this.lightMotion.currentX}%`);
      this.$el.style.setProperty("--reader-light-y", `${this.lightMotion.currentY}%`);
    },
    defaultLoad() {
      const token = getToken();
      if (!token) return;

      this.$axios.get("/user/auth").then((response) => {
        const { data } = response;
        if (data.code !== 200) return;
        this.navigateToRole(data.data.userRole);
      });
    },
    toDoRegister() {
      this.$router.push("/register");
    },
    toResetPwd() {
      this.$router.push("/resetPwd");
    },
    retryCaptcha() {
      this.captchaRetryCount = 0;
      if (this.captchaRetryTimer) clearTimeout(this.captchaRetryTimer);
      this.fetchCaptcha();
    },
    async fetchCaptcha() {
      this.captchaLoading = true;
      try {
        const { data } = await request.get("/captcha/generate");
        if (data.code === 200 && data.data) {
          this.captchaId = data.data.captchaId;
          this.captchaExpression = data.data.expression;
          this.captchaAnswer = "";
          this.captchaRetryCount = 0;
        } else {
          this.$message.error(data.msg || "验证码加载失败。");
        }
      } catch (error) {
        console.error("验证码加载失败:", error);
        if (this.captchaRetryCount < 2) {
          this.captchaRetryCount += 1;
          this.captchaRetryTimer = setTimeout(() => this.fetchCaptcha(), 1200);
          return;
        }
        this.$message.error("验证码加载失败，请确认后端 20606 已启动后重试。");
      } finally {
        this.captchaLoading = false;
      }
    },
    async login() {
      if (!this.act || !this.pwd) {
        this.$swal.fire({
          title: "这扇门没开",
          text: "账号和密码都需要填写。",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }
      if (!this.captchaId || this.captchaAnswer === "") {
        this.$swal.fire({
          title: "还差一道题",
          text: "请先填写登录验证码答案。",
          icon: "error",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        return;
      }

      this.loading = true;
      try {
        const { data } = await request.post("/user/login", {
          userAccount: this.act,
          userPwd: this.pwd,
          captchaId: this.captchaId,
          captchaAnswer: Number(this.captchaAnswer),
        });

        if (data.code !== 200) {
          this.$swal.fire({
            title: "登录失败",
            text: data.msg || "账号、密码或验证题有误，请再试一次。",
            icon: "error",
            showConfirmButton: false,
            timer: DELAY_TIME,
          });
          this.fetchCaptcha();
          return;
        }

        setToken(data.data.token);
        this.paperLeaving = true;
        setTimeout(() => {
          this.navigateToRole(data.data.role);
        }, 850);
      } catch (error) {
        console.error("登录请求错误:", error);
        const message = error.response
          ? "登录请求失败，请检查账号和验证码。"
          : "无法连接后端服务，请确认 20606 端口已经启动。";
        this.$message.error(message);
        this.fetchCaptcha();
      } finally {
        this.loading = false;
      }
    },
    navigateToRole(role) {
      const path = resolveRoleHome(role);
      if (!path) {
        clearAuthSession();
        this.$router.push("/login");
        return;
      }
      this.$router.push(path);
    },
  },
};
</script>

<style scoped lang="scss">
.auth-page {
  --lamp-x: 21.87%;
  --lamp-y: 56.72%;

  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  overflow: hidden;
  isolation: isolate;
}

.door-scene,
.door-reveal,
.door-mist {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.door-scene {
  z-index: 0;
  filter: brightness(0.94) saturate(0.76);
  transform: scale(1.035);
  transition: filter 0.8s ease, transform 1.2s ease;
}

.door-scene::before,
.door-scene::after,
.door-reveal::before,
.door-reveal::after {
  content: "";
  position: absolute;
  inset: 0;
  background-position: center;
  background-size: cover;
  background-repeat: no-repeat;
  transition: opacity 1.4s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity;
}

.door-scene::before,
.door-reveal::before {
  background-image: url("../../assets/images/reading-room-night-v2.webp");
  opacity: 1;
}

.door-scene::after,
.door-reveal::after {
  background-image: url("../../assets/images/reading-room-day-v2.webp");
  opacity: 0;
}

.auth-page[data-reader-theme="day"] .door-scene::after,
.auth-page[data-reader-theme="day"] .door-reveal::after {
  opacity: 1;
}

.auth-page[data-reader-theme="day"] .door-scene {
  filter: brightness(0.9) saturate(0.64);
}

.door-reveal {
  z-index: 1;
  display: none;
  filter: brightness(1.06) saturate(0.9);
  opacity: 0.28;
  transform: scale(1.035);
  transition: opacity var(--reader-motion-slow) ease, filter var(--reader-motion-slow) ease;
  -webkit-mask-image: radial-gradient(
    circle 25rem at var(--reader-light-x) var(--reader-light-y),
    #000 0,
    rgba(0, 0, 0, 0.92) 24%,
    rgba(0, 0, 0, 0.42) 55%,
    transparent 78%
  );
  mask-image: radial-gradient(
    circle 25rem at var(--reader-light-x) var(--reader-light-y),
    #000 0,
    rgba(0, 0, 0, 0.92) 24%,
    rgba(0, 0, 0, 0.42) 55%,
    transparent 78%
  );
}

@supports ((mask-image: radial-gradient(circle, #000, transparent)) or (-webkit-mask-image: radial-gradient(circle, #000, transparent))) {
  .door-reveal { display: block; }
}

.auth-page[data-reader-theme="day"] .door-reveal {
  filter: brightness(1.02) saturate(0.7);
  opacity: 0.16;
}

.door-mist {
  z-index: 2;
  background: transparent;
}

.door-mist::before,
.door-mist::after {
  content: "";
  position: absolute;
  inset: 0;
  transition: opacity 1.4s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: opacity;
}

.door-mist::before {
  background:
    radial-gradient(
      circle 18rem at var(--lamp-x) var(--lamp-y),
      rgba(4, 4, 3, 0.04),
      rgba(4, 4, 3, 0.16) 58%,
      rgba(4, 4, 3, 0.24) 100%
    ),
    rgba(4, 4, 3, 0.08);
  opacity: 1;
}

.door-mist::after {
  background:
    radial-gradient(ellipse at 50% 42%, rgba(239, 241, 235, 0.05), rgba(239, 241, 235, 0.15) 72%),
    linear-gradient(180deg, rgba(244, 245, 239, 0.18), rgba(235, 238, 231, 0.08) 48%, rgba(229, 232, 224, 0.16));
  opacity: 0;
}

.auth-page[data-reader-theme="day"] .door-mist::before { opacity: 0; }
.auth-page[data-reader-theme="day"] .door-mist::after { opacity: 1; }
.auth-page.paper-open .door-scene { filter: brightness(0.72) saturate(0.66) blur(2px); transform: scale(1.01); }
.auth-page.paper-open .door-reveal { opacity: 0.08; filter: brightness(0.84) saturate(0.58) blur(2px); }
.auth-page[data-reader-theme="day"].paper-open .door-scene { filter: brightness(0.78) saturate(0.48) blur(3px); }

.auth-theme-toggle {
  position: fixed;
  top: 24px;
  right: 26px;
  z-index: 10;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  color: var(--scene-text);
  background: transparent;
  cursor: pointer;
  opacity: 0.62;
}

.auth-theme-toggle svg { width: 18px; }

.threshold {
  position: absolute;
  inset: 0;
  z-index: 3;
  width: 100%;
  color: var(--scene-text);
  pointer-events: none;
}

.threshold-brand {
  position: absolute;
  top: 34px;
  left: 38px;
  font-family: var(--reader-serif);
  font-size: 18px;
  opacity: 0.68;
}

.threshold-light {
  position: absolute;
  top: 0;
  left: 50%;
  display: block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1px solid rgba(244, 222, 178, 0.48);
  background: #d6ae72;
  box-shadow: 0 0 13px 2px rgba(205, 158, 89, 0.13);
  transform: translate(-50%, -50%);
}

.threshold-entry {
  position: absolute;
  top: var(--lamp-y);
  left: var(--lamp-x);
  width: 112px;
  height: 64px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  pointer-events: auto;
  transform: translateX(-50%);
}

.threshold-word {
  position: absolute;
  top: 26px;
  left: 50%;
  font-family: var(--reader-serif);
  font-size: 12px;
  line-height: 17px;
  letter-spacing: 0.16em;
  opacity: 0.72;
  white-space: nowrap;
  transform: translateX(calc(-50% + 0.08em));
}

.paper-sheet {
  position: relative;
  z-index: 3;
  width: min(960px, calc(100vw - 48px));
  min-height: 470px;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  overflow: hidden;
  color: var(--paper-ink);
  border: 1px solid var(--paper-line);
  background:
    radial-gradient(ellipse at 14% 18%, rgba(89, 66, 39, 0.052), transparent 29%),
    radial-gradient(ellipse at 79% 82%, rgba(77, 91, 76, 0.036), transparent 25%),
    radial-gradient(ellipse 2.4% 88% at 34% 50%, rgba(76, 57, 34, 0.035), transparent 72%),
    linear-gradient(1deg, transparent 0 31%, rgba(92, 75, 49, 0.018) 31.2%, transparent 31.7%, transparent 69%, rgba(74, 59, 40, 0.014) 69.2%, transparent 69.7%),
    linear-gradient(90deg, rgba(117, 91, 51, 0.035), transparent 12%, transparent 88%, rgba(117, 91, 51, 0.035)),
    var(--paper);
  box-shadow:
    inset 0 0 46px rgba(71, 55, 34, 0.055),
    0 28px 100px rgba(0, 0, 0, 0.4);
  animation: paper-arrive 0.7s cubic-bezier(0.2, 0.75, 0.25, 1) both;
  transform-origin: left center;
}

.auth-page[data-reader-theme="night"] .paper-sheet {
  filter: brightness(0.86) saturate(0.72);
}

.paper-sheet::after {
  content: "";
  position: absolute;
  top: 26px;
  right: 26px;
  bottom: 26px;
  left: 26px;
  border: 1px solid color-mix(in srgb, var(--paper-ink) 7%, transparent);
  box-shadow: inset 0 0 28px rgba(79, 59, 35, 0.035);
  pointer-events: none;
}

.paper-sheet::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 17% 23%, rgba(86, 61, 33, 0.16), transparent 24%),
    radial-gradient(circle at 82% 71%, rgba(74, 91, 78, 0.1), transparent 27%),
    radial-gradient(ellipse 2% 82% at 67% 47%, rgba(75, 56, 34, 0.08), transparent 72%),
    linear-gradient(92deg, transparent 0 21%, rgba(77, 56, 33, 0.032) 21.2%, transparent 21.8%, transparent 73%, rgba(77, 56, 33, 0.021) 73.2%, transparent 73.9%);
  mix-blend-mode: multiply;
  filter: url("#reader-paper-grain");
  opacity: 0.34;
  pointer-events: none;
}

.auth-page[data-reader-theme="day"] .paper-sheet::before { opacity: 0.24; }

.back-to-door {
  position: absolute;
  top: 34px;
  right: 38px;
  z-index: 3;
  padding: 0;
  border: 0;
  border-bottom: 1px solid transparent;
  color: var(--paper-ink-faint);
  background: transparent;
  font-size: 11px;
  cursor: pointer;
}

.back-to-door:hover { color: var(--paper-ink); border-bottom-color: currentColor; }

.paper-copy,
.auth-card { position: relative; z-index: 1; padding: clamp(48px, 6vw, 78px); }
.paper-copy { border-right: 1px solid var(--paper-line); }
.paper-copy > p { margin: 0 0 62px; color: var(--seal); font-size: 12px; letter-spacing: 0.14em; }
.paper-copy h1 { margin: 0; font-family: var(--reader-serif); font-size: 58px; font-weight: 400; }
.paper-copy blockquote { max-width: 290px; margin: 25px 0 48px; color: var(--paper-ink-soft); font-family: var(--reader-serif); font-size: 16px; line-height: 2; }
.paper-copy > span { color: var(--paper-ink-faint); font-size: 11px; }

.auth-card header { margin-bottom: 26px; }
.auth-card h2 { margin: 0; font-family: var(--reader-serif); font-size: 28px; font-weight: 500; }
.auth-card header p { margin: 8px 0 0; color: var(--paper-ink-faint); font-size: 12px; }
.auth-card :deep(.el-form-item) { margin-bottom: 16px; }
.auth-card :deep(.el-form-item__content) { min-width: 0; }
.auth-card :deep(.el-form-item__label) { color: var(--paper-ink-soft); font-size: 12px; }
.captcha-label {
  display: inline-block;
  margin-right: -6px;
  letter-spacing: 6px;
  white-space: nowrap;
}

.captcha-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 76px;
  align-items: stretch;
  gap: 6px;
}
.captcha-question {
  width: 100%;
  height: 40px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px;
  align-items: center;
  gap: 3px;
  padding: 0 6px 0 14px;
  overflow: hidden;
  border: 1px solid var(--paper-line);
  color: var(--paper-ink);
  background: rgba(255, 255, 255, 0.2);
  font-size: 14px;
  cursor: pointer;
}
.captcha-question > span {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-align: left;
  font-variant-numeric: tabular-nums;
}
.captcha-refresh { display: none; width: 14px; flex: 0 0 auto; }
.captcha-question small {
  width: 30px;
  color: var(--paper-ink-faint);
  font-size: 10px;
  line-height: 1;
  text-align: right;
  white-space: nowrap;
  transform: translateX(-3px);
}

.entry-button {
  width: 100%;
  color: #fffdf8;
  border: 1px solid var(--paper-ink);
  border-radius: 1px;
  background: var(--paper-ink);
}

.entry-button:hover,
.entry-button:focus { color: #fffdf8; border-color: var(--seal); background: var(--seal); }

.auth-links { display: flex; justify-content: space-between; margin-top: 22px; }
.auth-links button { padding: 0; border: 0; color: var(--paper-ink-soft); background: transparent; font-size: 12px; cursor: pointer; }
.auth-links button:hover { color: var(--seal); }

.entry-button,
.captcha-question,
.back-to-door,
.auth-links button {
  transition: color 0.2s ease, border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.24s ease, transform 0.18s ease;
}

@media (hover: hover) {
  .entry-button:not(.is-disabled):hover,
  .captcha-question:not(:disabled):hover {
    transform: translateY(-1px);
  }

  .entry-button:not(.is-disabled):hover {
    box-shadow: 0 7px 18px color-mix(in srgb, var(--seal) 20%, transparent);
  }

  .auth-links button:hover,
  .back-to-door:hover { transform: translateY(-1px); }
}

.entry-button:not(.is-disabled):active,
.captcha-question:not(:disabled):active,
.auth-links button:active,
.back-to-door:active {
  transform: translateY(0) scale(0.985);
}

.paper-leaving .paper-sheet { animation: paper-leave 0.85s cubic-bezier(0.55, 0, 0.7, 0.2) both; }

@keyframes paper-arrive {
  from { opacity: 0; clip-path: inset(0 100% 0 0); transform: perspective(1000px) rotateY(-5deg); }
  to { opacity: 1; clip-path: inset(0); transform: perspective(1000px) rotateY(0); }
}

@keyframes paper-leave {
  from { opacity: 1; transform: perspective(1100px) translateX(0) rotateY(0) rotateZ(0); }
  to { opacity: 0; transform: perspective(1100px) translateX(48vw) rotateY(-30deg) rotateZ(4deg); }
}

@media (max-width: 820px) {
  .captcha-question { grid-template-columns: minmax(0, 1fr) 14px; gap: 6px; }
  .captcha-question small { display: none; }
  .captcha-refresh { display: block; }
}

@media (max-width: 760px) {
  .auth-page { --lamp-x: 34.82%; }
  .door-scene::before,
  .door-scene::after,
  .door-reveal::before,
  .door-reveal::after { background-position: 25.4% center; }
  .threshold-brand { top: 24px; left: 20px; }
  .paper-sheet { width: calc(100vw - 28px); min-height: 0; grid-template-columns: 1fr; }
  .paper-copy { padding: 34px 34px 20px; border-right: 0; border-bottom: 1px solid var(--paper-line); }
  .paper-copy > p { margin-bottom: 20px; }
  .paper-copy h1 { font-size: 38px; }
  .paper-copy blockquote { margin: 14px 0 0; font-size: 13px; }
  .paper-copy > span { display: none; }
  .auth-card { padding: 26px 34px 34px; }
  .door-reveal {
    -webkit-mask-image: radial-gradient(circle 15rem at var(--reader-light-x) var(--reader-light-y), #000 0, rgba(0, 0, 0, 0.72) 48%, transparent 82%);
    mask-image: radial-gradient(circle 15rem at var(--reader-light-x) var(--reader-light-y), #000 0, rgba(0, 0, 0, 0.72) 48%, transparent 82%);
  }
}
</style>
