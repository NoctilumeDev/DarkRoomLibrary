<template>
  <main class="auth-form-page auth-world" :data-reader-theme="theme">
    <button class="auth-theme-toggle" type="button" :title="themeTitle" @click="switchTheme">
      <component :is="themeIcon" />
    </button>

    <section class="auth-sheet">
      <header class="auth-intro">
        <span>暗室·藏书</span>
        <h1>留名</h1>
        <p>注册一个读者账号。借阅、预约与收藏，都将从这里开始。</p>
      </header>

      <section class="auth-card">
        <h2>创建读者账号</h2>
        <el-form @submit.prevent label-position="top">
          <el-form-item label="登录账号">
            <el-input v-model.trim="account" size="large" placeholder="设置登录账号" maxlength="32" autocomplete="username" />
            <small class="field-help">4-32 位，只能使用字母、数字和下划线。</small>
          </el-form-item>
          <el-form-item label="读者署名">
            <el-input v-model.trim="name" size="large" placeholder="设置显示名称" maxlength="20" />
            <small class="field-help">显示在留言与书评中，长度为 2-20 个字符。</small>
          </el-form-item>
          <el-form-item label="联系邮箱">
            <el-input v-model.trim="email" size="large" placeholder="输入常用邮箱" autocomplete="email" />
            <small class="field-help">用于验证码、预约到货与借阅到期提醒。</small>
          </el-form-item>
          <el-form-item label="邮箱验证码">
            <div class="inline-row">
              <el-input v-model.trim="code" size="large" placeholder="请输入验证码" />
              <el-button size="large" :disabled="codeCountdown > 0" @click="sendVerifyCode">
                {{ codeCountdown > 0 ? `${codeCountdown} 秒后重发` : "发送验证码" }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="password"
              size="large"
              type="password"
              placeholder="设置登录密码"
              maxlength="20"
              autocomplete="new-password"
              show-password
              @focus="passwordGuideFocused = true"
              @blur="passwordGuideFocused = false"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="passwordConfirm"
              size="large"
              type="password"
              placeholder="请再次输入密码"
              maxlength="20"
              autocomplete="new-password"
              show-password
              @focus="passwordGuideFocused = true"
              @blur="passwordGuideFocused = false"
              @keyup.enter="registerFunc"
            />
          </el-form-item>

          <PasswordGuide
            :password="password"
            :confirm-password="passwordConfirm"
            :visible="passwordGuideVisible"
            show-confirmation
          />

          <el-button class="entry-button" size="large" :loading="loading" @click="registerFunc">
            完成注册
          </el-button>
        </el-form>

        <button class="plain-link" type="button" @click="toDoLogin">已有账号，返回登录</button>
      </section>
    </section>
  </main>
</template>

<script>
import request from "@/utils/request.js";
import { getReaderTheme, toggleReaderTheme } from "@/utils/readerTheme.js";
import { isStrongPassword } from "@/utils/passwordRules.js";
import PasswordGuide from "@/components/PasswordGuide.vue";
import { Moon, Sunny } from "@element-plus/icons-vue";

const DELAY_TIME = 1300;

export default {
  name: "Register",
  components: { Moon, PasswordGuide, Sunny },
  data() {
    return {
      theme: getReaderTheme(),
      account: "",
      password: "",
      passwordConfirm: "",
      name: "",
      email: "",
      code: "",
      codeCountdown: 0,
      countdownTimer: null,
      loading: false,
      passwordGuideFocused: false,
    };
  },
  computed: {
    themeIcon() {
      return this.theme === "night" ? "Sunny" : "Moon";
    },
    themeTitle() {
      return this.theme === "night" ? "切换至天光" : "切换至灯下";
    },
    passwordGuideVisible() {
      return (
        this.passwordGuideFocused ||
        Boolean(this.password && !isStrongPassword(this.password)) ||
        Boolean(this.passwordConfirm && this.password !== this.passwordConfirm)
      );
    },
  },
  beforeUnmount() {
    if (this.countdownTimer) clearInterval(this.countdownTimer);
  },
  methods: {
    switchTheme() {
      this.theme = toggleReaderTheme(this.theme);
    },
    toDoLogin() {
      this.$router.push("/login");
    },
    async sendVerifyCode() {
      if (!this.email) {
        this.$message.warning("请先输入邮箱地址。");
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email)) {
        this.$message.warning("邮箱格式不正确。");
        return;
      }

      try {
        const { data } = await request.post("/user/sendVerifyCode", {
          email: this.email,
          purpose: "REGISTER",
        });
        if (data.code === 200) {
          this.$message.success("验证码已发送，请查收邮箱。");
          this.codeCountdown = 60;
          this.countdownTimer = setInterval(() => {
            this.codeCountdown -= 1;
            if (this.codeCountdown <= 0) {
              clearInterval(this.countdownTimer);
              this.countdownTimer = null;
            }
          }, 1000);
        } else {
          this.$message.error(data.msg);
        }
      } catch (error) {
        console.error("发送验证码失败:", error);
        this.$message.error("发送失败，请稍后重试。");
      }
    },
    async registerFunc() {
      if (!this.account || !this.password || !this.passwordConfirm || !this.name || !this.email) {
        this.showError("请填写完整的注册信息。");
        return;
      }
      if (!/^[a-zA-Z0-9_]{4,32}$/.test(this.account)) {
        this.showError("账号需要为 4-32 位字母、数字或下划线。");
        return;
      }
      if (this.name.length < 2 || this.name.length > 20) {
        this.showError("读者署名需要为 2-20 个字符。");
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email)) {
        this.showError("邮箱格式不正确。");
        return;
      }
      if (!this.code) {
        this.showError("请输入邮箱验证码。");
        return;
      }
      if (this.password !== this.passwordConfirm) {
        this.showError("两次密码输入不一致。");
        return;
      }
      if (!isStrongPassword(this.password)) {
        this.showError("密码需要为 8-20 位，并在大小写字母、数字、特殊字符中至少满足三类。");
        return;
      }

      this.loading = true;
      try {
        const { data } = await request.post("/user/register", {
          userAccount: this.account,
          userPwd: this.password,
          userName: this.name,
          userEmail: this.email,
          verificationCode: this.code,
        });
        if (data.code !== 200) {
          this.showError(data.msg || "注册失败。");
          return;
        }
        this.$swal.fire({
          title: "注册成功",
          text: "即将返回登录页。",
          icon: "success",
          showConfirmButton: false,
          timer: DELAY_TIME,
        });
        setTimeout(() => this.$router.push("/login"), DELAY_TIME);
      } catch (error) {
        console.error("注册请求错误:", error);
        this.$message.error("注册请求出错，请稍后重试。");
      } finally {
        this.loading = false;
      }
    },
    showError(text) {
      this.$swal.fire({
        title: "填写校验",
        text,
        icon: "error",
        showConfirmButton: false,
        timer: DELAY_TIME,
      });
    },
  },
};
</script>
