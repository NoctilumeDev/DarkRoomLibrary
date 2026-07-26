<template>
  <Transition name="password-guide">
    <aside v-if="visible" class="password-guide" :class="{ valid: checks.valid }" aria-live="polite">
      <header>
        <strong>密码要求</strong>
        <span>{{ checks.valid ? "已经满足" : `已满足 ${checks.categoryCount}/4 类` }}</span>
      </header>

      <ul class="primary-rules">
        <li :class="{ done: checks.length }"><i aria-hidden="true"></i>长度为 8-20 位</li>
        <li :class="{ done: checks.categoryCount >= 3 }"><i aria-hidden="true"></i>下列四类至少满足三类</li>
      </ul>

      <ul class="category-rules">
        <li :class="{ done: checks.lower }"><i aria-hidden="true"></i>小写字母</li>
        <li :class="{ done: checks.upper }"><i aria-hidden="true"></i>大写字母</li>
        <li :class="{ done: checks.number }"><i aria-hidden="true"></i>数字</li>
        <li :class="{ done: checks.special }"><i aria-hidden="true"></i>特殊字符</li>
      </ul>

      <p v-if="showConfirmation && confirmPassword" :class="{ done: passwordsMatch }">
        {{ passwordsMatch ? "两次密码一致" : "两次密码尚未一致" }}
      </p>
    </aside>
  </Transition>
</template>

<script>
import { getPasswordChecks } from "@/utils/passwordRules.js";

export default {
  name: "PasswordGuide",
  props: {
    password: { type: String, default: "" },
    confirmPassword: { type: String, default: "" },
    visible: { type: Boolean, default: false },
    showConfirmation: { type: Boolean, default: false },
  },
  computed: {
    checks() {
      return getPasswordChecks(this.password);
    },
    passwordsMatch() {
      return Boolean(this.confirmPassword) && this.password === this.confirmPassword;
    },
  },
};
</script>

<style scoped lang="scss">
.password-guide {
  margin: -3px 0 16px;
  padding: 13px 15px 12px;
  border-left: 2px solid var(--accent);
  color: var(--paper-ink-soft, var(--admin-text-secondary));
  background: color-mix(in srgb, var(--paper-soft, var(--admin-surface-muted)) 58%, transparent);
  transform-origin: top center;
}

.password-guide.valid { border-left-color: var(--jade, var(--admin-jade)); }

header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

header strong { color: var(--paper-ink, var(--admin-text)); font-size: 12px; }
header span { color: var(--paper-ink-faint, var(--admin-muted)); font-size: 11px; }

ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.primary-rules { display: grid; gap: 7px; margin-bottom: 9px; }
.category-rules { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 14px; }

li {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--paper-ink-faint, var(--admin-muted));
  font-size: 11px;
  line-height: 1.5;
  transition: color 0.24s ease;
}

li i {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border: 1px solid currentColor;
  border-radius: 50%;
  transition: background-color 0.24s ease, transform 0.24s ease;
}

li.done { color: var(--jade, var(--admin-jade)); }
li.done i { background: currentColor; transform: scale(1.08); }

p {
  margin: 10px 0 0;
  padding-top: 9px;
  border-top: 1px solid var(--paper-line, var(--admin-border));
  color: var(--seal, var(--admin-danger));
  font-size: 11px;
}

p.done { color: var(--jade, var(--admin-jade)); }

.password-guide-enter-active,
.password-guide-leave-active {
  transition: opacity 0.24s ease, transform 0.3s ease, max-height 0.3s ease;
  overflow: hidden;
}

.password-guide-enter-from,
.password-guide-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-5px);
}

.password-guide-enter-to,
.password-guide-leave-from {
  max-height: 190px;
  opacity: 1;
  transform: translateY(0);
}
</style>
