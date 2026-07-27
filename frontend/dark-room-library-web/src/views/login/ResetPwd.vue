<template>
  <main class="auth-form-page auth-world" :data-reader-theme="theme">
    <button class="auth-theme-toggle" type="button" :title="themeTitle" @click="switchTheme">
      <component :is="themeIcon" />
    </button>

    <section class="auth-sheet">
      <header class="auth-intro">
        <span>暗室·藏书</span>
        <h1>寻回</h1>
        <p>验证注册邮箱后，可以重新设置进入藏书室的密码。</p>
      </header>

      <section class="auth-card">
        <h2>重置密码</h2>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="账号" prop="account">
            <el-input v-model.trim="form.account" size="large" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <div class="inline-row">
              <el-input v-model.trim="form.email" size="large" placeholder="请输入注册邮箱" />
              <el-button size="large" :disabled="codeCountdown > 0" @click="sendVerifyCode">
                {{ codeCountdown > 0 ? `${codeCountdown} 秒后重发` : "发送验证码" }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="验证码" prop="code">
            <el-input v-model.trim="form.code" size="large" placeholder="请输入邮箱验证码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPwd">
            <el-input
              v-model="form.newPwd"
              size="large"
              type="password"
              placeholder="设置新密码"
              maxlength="20"
              autocomplete="new-password"
              show-password
              @focus="passwordGuideFocused = true"
              @blur="passwordGuideFocused = false"
            />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPwd">
            <el-input
              v-model="form.confirmPwd"
              size="large"
              type="password"
              placeholder="请再次输入新密码"
              maxlength="20"
              autocomplete="new-password"
              show-password
              @focus="passwordGuideFocused = true"
              @blur="passwordGuideFocused = false"
              @keyup.enter="handleReset"
            />
          </el-form-item>
          <PasswordGuide
            :password="form.newPwd"
            :confirm-password="form.confirmPwd"
            :visible="passwordGuideVisible"
            show-confirmation
          />
          <el-button class="entry-button" size="large" :loading="loading" @click="handleReset">
            重置密码
          </el-button>
        </el-form>
        <button class="plain-link" type="button" @click="$router.push('/login')">返回登录</button>
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

export default {
  name: "ResetPwd",
  components: { Moon, PasswordGuide, Sunny },
  data() {
    const validateConfirmPwd = (rule, value, callback) => {
      if (value !== this.form.newPwd) {
        callback(new Error("两次密码输入不一致。"));
      } else {
        callback();
      }
    };
    const validatePwd = (rule, value, callback) => {
      if (value && !isStrongPassword(value)) {
        callback(new Error("请满足下方密码要求。"));
      } else {
        callback();
      }
    };

    return {
      theme: getReaderTheme(),
      form: { account: "", email: "", code: "", newPwd: "", confirmPwd: "" },
      loading: false,
      codeCountdown: 0,
      countdownTimer: null,
      passwordGuideFocused: false,
      rules: {
        account: [{ required: true, message: "请输入账号。", trigger: "blur" }],
        email: [
          { required: true, message: "请输入邮箱。", trigger: "blur" },
          { type: "email", message: "邮箱格式不正确。", trigger: "blur" },
        ],
        code: [{ required: true, message: "请输入验证码。", trigger: "blur" }],
        newPwd: [
          { required: true, message: "请输入新密码。", trigger: "blur" },
          { validator: validatePwd, trigger: "blur" },
        ],
        confirmPwd: [
          { required: true, message: "请确认新密码。", trigger: "blur" },
          { validator: validateConfirmPwd, trigger: "blur" },
        ],
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
    passwordGuideVisible() {
      return (
        this.passwordGuideFocused ||
        Boolean(this.form.newPwd && !isStrongPassword(this.form.newPwd)) ||
        Boolean(this.form.confirmPwd && this.form.newPwd !== this.form.confirmPwd)
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
    async sendVerifyCode() {
      if (!this.form.email) {
        this.$message.warning("请先输入邮箱地址。");
        return;
      }
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email)) {
        this.$message.warning("邮箱格式不正确。");
        return;
      }

      try {
        const { data } = await request.post("/user/sendVerifyCode", {
          email: this.form.email,
          purpose: "RESET_PASSWORD",
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
    async handleReset() {
      try {
        await this.$refs.formRef.validate();
      } catch {
        return;
      }

      this.loading = true;
      try {
        const response = await request.post("/user/resetPwd", {
          account: this.form.account,
          email: this.form.email,
          code: this.form.code,
          newPwd: this.form.newPwd,
        });

        if (response.data.code === 200) {
          this.$swal.fire({
            title: "重置成功",
            text: response.data.msg || "请使用新密码登录。",
            icon: "success",
            showConfirmButton: false,
            timer: 1800,
          });
          setTimeout(() => this.$router.push("/login"), 1800);
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        console.error("重置密码失败:", error);
        this.$message.error("重置失败，请稍后重试。");
      } finally {
        this.loading = false;
      }
    },
  },
};
</script>
