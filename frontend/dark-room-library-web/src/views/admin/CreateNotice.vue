<template>
  <section class="notice-editor-page">
    <header class="page-intro">
      <div>
        <p>NOTICE EDITOR</p>
        <h1>{{ noticeOperation === "save" ? "发布公告" : "修改公告" }}</h1>
      </div>
      <div class="header-actions">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <el-button type="primary" @click="operation">
          <el-icon><Check /></el-icon>
          {{ noticeOperation === "save" ? "发布公告" : "保存修改" }}
        </el-button>
      </div>
    </header>

    <div class="title-field">
      <label for="notice-title">公告标题</label>
      <el-input
        id="notice-title"
        v-model="notice.name"
        maxlength="100"
        show-word-limit
        placeholder="输入清晰、简短的公告标题"
      />
    </div>

    <div class="editor-sheet">
      <div class="editor-label">
        <span>公告正文</span>
        <small>支持基础排版、图片与链接</small>
      </div>
      <Editor
        height="clamp(420px, calc(100vh - 390px), 720px)"
        :receive-content="notice.content"
        @on-receive="receiveData"
      />
    </div>
  </section>
</template>

<script>
import Editor from "@/components/Editor.vue";
import { ArrowLeft, Check } from "@element-plus/icons-vue";

export default {
  name: "CreateNotice",
  components: { Editor, ArrowLeft, Check },
  data() {
    return {
      notice: {
        name: "",
        content: "",
      },
      saveApi: "/notice/save",
      updateApi: "/notice/update",
      noticeOperation: "",
    };
  },
  created() {
    this.loadOperation();
  },
  methods: {
    goBack() {
      this.$router.back();
    },
    operation() {
      if (this.noticeOperation === "save") {
        this.save();
        return;
      }
      this.update();
    },
    loadOperation() {
      const operation = sessionStorage.getItem("noticeOperation");
      if (operation === "update") {
        const notice = sessionStorage.getItem("noticeInfo");
        this.notice = notice ? JSON.parse(notice) : { name: "", content: "" };
      }
      this.noticeOperation = operation || "save";
    },
    receiveData(html) {
      this.notice.content = html;
    },
    validateNotice() {
      if (!this.notice.name || !this.notice.name.trim()) {
        this.$message.warning("公告标题不能为空");
        return false;
      }
      if (this.isBlankHtml(this.notice.content)) {
        this.$message.warning("公告内容不能为空");
        return false;
      }
      if (this.notice.content.length > 20000) {
        this.$message.warning("公告内容不能超过 20000 个字符");
        return false;
      }
      return true;
    },
    isBlankHtml(html) {
      if (!html) return true;
      const container = document.createElement("div");
      container.innerHTML = html;
      return (
        !container.textContent.trim() &&
        container.querySelectorAll("img,video").length === 0
      );
    },
    getErrorMessage(error) {
      return (
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        "操作失败，请稍后重试"
      );
    },
    async update() {
      if (!this.validateNotice()) return;
      try {
        const response = await this.$axios.put(this.updateApi, this.notice);
        if (response.data.code === 200) {
          this.$message.success("公告修改成功");
          this.$router.back();
        } else {
          this.$message.error(response.data.msg || "修改失败");
        }
      } catch (error) {
        this.$message.error(this.getErrorMessage(error));
        console.error("CreateNotice.vue update:", error);
      }
    },
    async save() {
      if (!this.validateNotice()) return;
      try {
        const response = await this.$axios.post(this.saveApi, this.notice);
        if (response.data.code === 200) {
          this.$message.success("公告发布成功");
          this.$router.back();
        } else {
          this.$message.error(response.data.msg || "发布失败");
        }
      } catch (error) {
        this.$message.error(this.getErrorMessage(error));
        console.error("CreateNotice.vue save:", error);
      }
    },
  },
};
</script>

<style scoped lang="scss">
.notice-editor-page {
  display: grid;
  gap: 20px;
  max-width: 1080px;
  margin: 0 auto;
  color: var(--admin-text);
}

.page-intro {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: end;

  p {
    margin: 0;
    color: var(--admin-gold);
    font-size: 11px;
    font-weight: 700;
  }

  h1 {
    margin: 5px 0 0;
    color: var(--admin-text);
    font-size: 30px;
  }
}

.header-actions {
  display: flex;
  gap: 9px;
}

.title-field,
.editor-sheet {
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
}

.title-field {
  display: grid;
  grid-template-columns: 100px 1fr;
  gap: 14px;
  align-items: center;
  padding: 16px;

  label {
    color: var(--admin-text-secondary);
    font-weight: 700;
  }
}

.editor-sheet {
  padding: 16px;
}

.editor-label {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;

  span {
    color: var(--admin-text);
    font-weight: 700;
  }

  small {
    color: var(--admin-muted);
  }
}

@media (max-width: 620px) {
  .page-intro,
  .editor-label {
    align-items: flex-start;
    flex-direction: column;
  }

  .title-field {
    grid-template-columns: 1fr;
  }
}
</style>
