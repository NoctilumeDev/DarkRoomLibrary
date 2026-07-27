<template>
  <el-dialog
    class="profile-dialog"
    :model-value="modelValue"
    :teleported="false"
    width="min(420px, calc(100vw - 24px))"
    :show-close="false"
    @close="close"
  >
    <template #header>
      <div class="dialog-title">
        <span>个人资料</span>
        <button type="button" title="关闭" aria-label="关闭" @click="close">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </template>

    <div class="profile-form">
      <label>头像</label>
      <el-upload
        class="avatar-uploader"
        :action="uploadUrl"
        :headers="uploadHeaders"
        :disabled="demoMode"
        :show-file-list="false"
        :on-success="handleAvatarSuccess"
      >
        <img v-if="form.url" class="avatar-preview" :src="getAvatarUrl(form.url)" />
        <div v-else class="avatar-placeholder">上传头像</div>
      </el-upload>

      <p class="profile-hint">登录账号不可在此修改，仅更新对外显示名称与联系邮箱。</p>

      <label>显示名称</label>
      <el-input
        v-model.trim="form.name"
        size="large"
        placeholder="显示名称"
        maxlength="50"
        autocomplete="name"
      />

      <label>联系邮箱</label>
      <el-input
        v-model.trim="form.email"
        size="large"
        placeholder="邮箱"
        maxlength="100"
        autocomplete="email"
      />
      <small class="profile-email-help">
        同一邮箱最多关联 3 个账号；系统不会显示关联数量或账号信息。
      </small>

      <template v-if="emailChanged && !demoMode">
        <label>新邮箱验证码</label>
        <div class="email-code-row">
          <el-input
            v-model.trim="verificationCode"
            size="large"
            placeholder="6 位验证码"
            maxlength="6"
            inputmode="numeric"
            autocomplete="one-time-code"
          />
          <el-button
            class="email-code-button"
            size="large"
            :loading="codeSending"
            :disabled="codeCountdown > 0"
            @click="sendEmailChangeCode"
          >
            {{ codeCountdown > 0 ? `${codeCountdown} 秒后重发` : "发送验证码" }}
          </el-button>
        </div>
        <small class="profile-email-help">验证通过后才会检查并更新邮箱关联名额。</small>
      </template>

      <small v-else-if="emailChanged" class="profile-email-help">
        在线演示仅在当前浏览器会话中修改，不发送真实邮件。
      </small>

      <div v-if="canCancelAccount" class="danger-zone">
        <div>
          <strong>注销账号</strong>
          <span>注销后账号不可登录，借阅与留言记录会保留。</span>
        </div>
        <el-button type="danger" plain :disabled="demoMode" @click="cancelAccount">
          注销账号
        </el-button>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button
        class="profile-save-button"
        :loading="saving"
        @click="save"
      >
        保存修改
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import { buildApiUrl, resolveFileUrl } from "@/utils/fileUrl.js";
import { clearAuthSession, getToken } from "@/utils/storage";
import { USER_ROLE } from "@/utils/userRoles.js";
import { Close } from "@element-plus/icons-vue";

export default {
  name: "ProfileDialog",
  components: { Close },
  props: {
    modelValue: {
      type: Boolean,
      required: true,
    },
    userInfo: {
      type: Object,
      required: true,
    },
  },
  emits: ["update:modelValue", "saved"],
  data() {
    return {
      form: {
        url: "",
        name: "",
        email: "",
      },
      originalEmail: "",
      verificationCode: "",
      codeCountdown: 0,
      countdownTimer: null,
      codeSending: false,
      saving: false,
    };
  },
  computed: {
    demoMode() {
      return DEMO_MODE;
    },
    uploadUrl() {
      return buildApiUrl("/file/upload");
    },
    uploadHeaders() {
      const token = getToken();
      return token
        ? {
            Authorization: `Bearer ${token}`,
            token,
          }
        : {};
    },
    canCancelAccount() {
      return this.userInfo?.role === USER_ROLE.READER;
    },
    emailChanged() {
      return this.normalizeEmail(this.form.email) !== this.normalizeEmail(this.originalEmail);
    },
  },
  watch: {
    userInfo: {
      immediate: true,
      deep: true,
      handler(value) {
        this.syncForm(value);
      },
    },
    modelValue(open) {
      if (open) this.syncForm(this.userInfo);
      else this.resetEmailVerification();
    },
    "form.email"(value, previous) {
      if (this.normalizeEmail(value) !== this.normalizeEmail(previous)) {
        this.resetEmailVerification();
      }
    },
  },
  beforeUnmount() {
    this.stopCountdown();
  },
  methods: {
    close() {
      this.resetEmailVerification();
      this.$emit("update:modelValue", false);
    },
    normalizeEmail(email) {
      return String(email || "").trim().toLowerCase();
    },
    syncForm(value) {
      this.form = {
        url: value?.url || "",
        name: value?.name || "",
        email: value?.email || "",
      };
      this.originalEmail = value?.email || "";
      this.resetEmailVerification();
    },
    stopCountdown() {
      if (this.countdownTimer) {
        clearInterval(this.countdownTimer);
        this.countdownTimer = null;
      }
      this.codeCountdown = 0;
    },
    resetEmailVerification() {
      this.verificationCode = "";
      this.stopCountdown();
    },
    startCountdown() {
      this.stopCountdown();
      this.codeCountdown = 60;
      this.countdownTimer = setInterval(() => {
        this.codeCountdown -= 1;
        if (this.codeCountdown <= 0) this.stopCountdown();
      }, 1000);
    },
    getAvatarUrl(url) {
      return resolveFileUrl(url);
    },
    handleAvatarSuccess(res) {
      if (res.code !== 200) {
        this.$message.error(res.msg || "头像上传失败。");
        return;
      }
      this.form.url = res.data;
      this.$message.success("头像上传成功。");
    },
    async sendEmailChangeCode() {
      const email = this.normalizeEmail(this.form.email);
      if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        this.$message.warning("请先输入正确的新邮箱地址。");
        return;
      }
      if (!this.emailChanged) {
        this.$message.info("联系邮箱没有变化。");
        return;
      }

      this.codeSending = true;
      try {
        const response = await this.$axios.post("/user/sendEmailChangeCode", { email });
        if (response.data.code === 200) {
          this.$message.success("验证码已发送，请查收新邮箱。");
          this.startCountdown();
        } else {
          this.$message.error(response.data.msg || "验证码发送失败。");
        }
      } catch (error) {
        console.error("发送换绑邮箱验证码失败:", error);
        this.$message.error("发送失败，请稍后重试。");
      } finally {
        this.codeSending = false;
      }
    },
    async save() {
      if (
        !this.form.email ||
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.form.email)
      ) {
        this.$message.warning("邮箱格式不正确。");
        return;
      }
      if (this.emailChanged && !this.demoMode && !/^\d{6}$/.test(this.verificationCode)) {
        this.$message.warning("请输入新邮箱收到的 6 位验证码。");
        return;
      }

      this.saving = true;
      try {
        const response = await this.$axios.put("/user/update", {
          userAvatar: this.form.url,
          userName: this.form.name,
          userEmail: this.form.email,
          verificationCode:
            this.emailChanged && !this.demoMode ? this.verificationCode : undefined,
        });
        if (response.data.code === 200) {
          this.$message.success(response.data.msg || "个人资料已更新。");
          this.$emit("saved");
          this.close();
        } else {
          this.$message.error(response.data.msg || "保存失败。");
        }
      } catch (error) {
        console.error("保存个人资料失败:", error);
        this.$message.error("保存失败，请稍后重试。");
      } finally {
        this.saving = false;
      }
    },
    async cancelAccount() {
      if (this.demoMode) {
        this.$message.info("在线演示不会注销固定体验账号。");
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "确认注销账号？",
        text: "注销前请确认没有未归还图书、未处理罚款或进行中的预约。",
        icon: "warning",
        confirmButtonText: "确认注销",
      });
      if (!confirmed) return;

      try {
        const response = await this.$axios.put("/user/cancelAccount");
        if (response.data.code === 200) {
          this.$message.success(response.data.msg || "账号已注销。");
          clearAuthSession();
          this.close();
          this.$router.push("/login");
        } else {
          this.$message.error(response.data.msg || "注销失败。");
        }
      } catch (error) {
        console.error("注销账号失败:", error);
        this.$message.error("注销失败，请稍后重试。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.dialog-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 700;
  color: #2f281f;

  button {
    width: 30px;
    height: 30px;
    display: grid;
    place-items: center;
    border: 0;
    padding: 0;
    color: #8b745a;
    background: transparent;
    cursor: pointer;
  }

  .el-icon {
    font-size: 16px;
  }
}

.profile-form {
  display: grid;
  gap: 10px;

  label {
    margin-top: 8px;
    color: #5d5042;
    font-size: 13px;
    font-weight: 700;
  }
}

.profile-hint {
  margin: 8px 0 0;
  color: #7d6c5b;
  font-size: 12px;
  line-height: 1.6;
}

.profile-email-help {
  color: #7d6c5b;
  font-size: 12px;
  line-height: 1.55;
}

.email-code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: 10px;
}

:global(.email-code-button.el-button) {
  --el-button-text-color: var(--seal, var(--workbench-seal, var(--tone-seal)));
  --el-button-border-color: color-mix(in srgb, var(--tone-seal) 42%, transparent);
  --el-button-hover-text-color: var(--tone-seal-deep);
  --el-button-hover-border-color: var(--tone-seal-deep);
  --el-button-hover-bg-color: color-mix(in srgb, var(--tone-seal) 8%, transparent);
}

.avatar-uploader {
  width: 86px;
}

.avatar-preview,
.avatar-placeholder {
  width: 82px;
  height: 82px;
  border-radius: 50%;
}

.avatar-preview {
  object-fit: cover;
  display: block;
}

.avatar-placeholder {
  display: grid;
  place-items: center;
  border: 1px dashed #c6ad8c;
  color: #8b745a;
  background: #fbf5ea;
  font-size: 12px;
}

.danger-zone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 18px;
  padding: 14px;
  border: 1px solid rgba(191, 76, 65, 0.28);
  border-radius: var(--radius-surface, 6px);
  background: rgba(191, 76, 65, 0.06);

  strong,
  span {
    display: block;
  }

  strong {
    color: #8b2f25;
    font-size: 14px;
  }

  span {
    margin-top: 4px;
    color: #7d6c5b;
    font-size: 12px;
    line-height: 1.5;
  }
}

:global(.profile-save-button.el-button) {
  --el-button-text-color: #fffdf7;
  --el-button-bg-color: var(--seal, var(--workbench-seal, var(--tone-seal)));
  --el-button-border-color: var(--seal, var(--workbench-seal, var(--tone-seal)));
  --el-button-hover-text-color: #fffdf7;
  --el-button-hover-bg-color: var(--tone-seal-deep);
  --el-button-hover-border-color: var(--tone-seal-deep);
  --el-button-active-bg-color: color-mix(in srgb, var(--tone-seal-deep) 84%, black);
  --el-button-active-border-color: color-mix(in srgb, var(--tone-seal-deep) 84%, black);
}

:global(.profile-save-button.el-button.is-disabled) {
  color: rgba(255, 253, 247, 0.72);
  border-color: var(--paper-ink-faint, #777970);
  background: var(--paper-ink-faint, #777970);
}

@media (max-width: 480px) {
  .email-code-row {
    grid-template-columns: 1fr;

    :deep(.el-button) {
      width: 100%;
      margin-left: 0;
    }
  }

  .danger-zone {
    align-items: stretch;
    flex-direction: column;

    :deep(.el-button) {
      align-self: flex-start;
      margin-left: 0;
    }
  }
}
</style>
