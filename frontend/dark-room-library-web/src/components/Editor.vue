<template>
  <section class="notice-rich-editor">
    <div class="editor-toolbar" role="toolbar" aria-label="公告正文格式工具">
      <label class="format-select">
        <span class="sr-only">段落格式</span>
        <select :value="blockType" aria-label="段落格式" @change="formatBlock">
          <option value="p">正文</option>
          <option value="h2">二级标题</option>
          <option value="h3">三级标题</option>
          <option value="blockquote">引用</option>
        </select>
      </label>
      <span class="toolbar-divider" aria-hidden="true"></span>
      <button type="button" title="粗体" aria-label="粗体" @mousedown.prevent @click="execute('bold')">
        <strong>B</strong>
      </button>
      <button type="button" title="斜体" aria-label="斜体" @mousedown.prevent @click="execute('italic')">
        <em>I</em>
      </button>
      <button type="button" title="下划线" aria-label="下划线" @mousedown.prevent @click="execute('underline')">
        <u>U</u>
      </button>
      <span class="toolbar-divider" aria-hidden="true"></span>
      <button type="button" title="无序列表" aria-label="无序列表" @mousedown.prevent @click="execute('insertUnorderedList')">
        • 列表
      </button>
      <button type="button" title="有序列表" aria-label="有序列表" @mousedown.prevent @click="execute('insertOrderedList')">
        1. 列表
      </button>
      <button type="button" title="插入链接" aria-label="插入链接" @mousedown.prevent @click="insertLink">
        链接
      </button>
      <button type="button" title="上传图片" aria-label="上传图片" @mousedown.prevent @click="openImagePicker">
        图片
      </button>
      <span class="toolbar-divider" aria-hidden="true"></span>
      <button type="button" title="撤销" aria-label="撤销" @mousedown.prevent @click="execute('undo')">
        ↶
      </button>
      <button type="button" title="重做" aria-label="重做" @mousedown.prevent @click="execute('redo')">
        ↷
      </button>
      <button type="button" title="清除格式" aria-label="清除格式" @mousedown.prevent @click="execute('removeFormat')">
        清除
      </button>
      <input
        ref="imageInput"
        class="image-input"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        @change="uploadImage"
      />
    </div>

    <div
      ref="editor"
      class="editor-content html-content"
      :style="{ height }"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      data-placeholder="写下公告正文……"
      @focus="rememberSelection"
      @keyup="rememberSelection"
      @mouseup="rememberSelection"
      @input="emitContent"
      @blur="emitContent"
    ></div>
  </section>
</template>

<script>
import { resolveFileUrl } from "@/utils/fileUrl.js";

export default {
  name: "NoticeRichEditor",
  props: {
    receiveContent: {
      type: String,
      default: "",
      required: true,
    },
    height: {
      type: String,
      default: "calc(100vh - 100px)",
    },
  },
  emits: ["on-receive"],
  data() {
    return {
      blockType: "p",
      savedRange: null,
      uploading: false,
    };
  },
  mounted() {
    this.syncContent(this.receiveContent);
  },
  watch: {
    receiveContent(value) {
      if (value !== this.currentContent()) this.syncContent(value);
    },
  },
  methods: {
    currentContent() {
      return this.$refs.editor?.innerHTML || "";
    },
    syncContent(value) {
      if (this.$refs.editor) this.$refs.editor.innerHTML = value || "";
    },
    emitContent() {
      this.$emit("on-receive", this.currentContent());
      this.rememberSelection();
    },
    focusEditor() {
      this.$refs.editor?.focus({ preventScroll: true });
      if (!this.savedRange) return;
      const selection = window.getSelection();
      selection?.removeAllRanges();
      selection?.addRange(this.savedRange);
    },
    rememberSelection() {
      const selection = window.getSelection();
      if (!selection?.rangeCount || !this.$refs.editor?.contains(selection.anchorNode)) return;
      this.savedRange = selection.getRangeAt(0).cloneRange();
    },
    execute(command, value = null) {
      this.focusEditor();
      document.execCommand(command, false, value);
      this.emitContent();
    },
    formatBlock(event) {
      this.blockType = event.target.value;
      this.execute("formatBlock", this.blockType);
    },
    insertLink() {
      const url = window.prompt("请输入完整链接地址", "https://");
      if (!url) return;
      try {
        const parsed = new URL(url);
        if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("unsupported protocol");
        this.execute("createLink", parsed.href);
      } catch {
        this.$message.warning("请输入以 http:// 或 https:// 开头的有效链接");
      }
    },
    openImagePicker() {
      if (this.uploading) return;
      this.rememberSelection();
      this.$refs.imageInput?.click();
    },
    async uploadImage(event) {
      const file = event.target.files?.[0];
      event.target.value = "";
      if (!file) return;
      if (!file.type.startsWith("image/") || file.size > 10 * 1024 * 1024) {
        this.$message.warning("请选择不超过 10MB 的 JPG、PNG、GIF 或 WebP 图片");
        return;
      }
      this.uploading = true;
      try {
        const formData = new FormData();
        formData.append("file", file);
        const response = await this.$axios.post("/file/upload", formData);
        if (response.data.code !== 200 || !response.data.data) {
          this.$message.error(response.data.msg || "图片上传失败");
          return;
        }
        this.execute("insertImage", resolveFileUrl(response.data.data));
      } catch (error) {
        this.$message.error(error?.response?.data?.msg || "图片上传失败");
      } finally {
        this.uploading = false;
      }
    },
  },
};
</script>

<style scoped lang="scss">
.notice-rich-editor {
  overflow: hidden;
  color: var(--admin-text);
  border: 1px solid var(--admin-border);
  border-radius: 4px;
  background: var(--admin-surface-strong);
}

.editor-toolbar {
  min-height: 44px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--admin-border);
  background: var(--admin-surface-muted);
}

.editor-toolbar button,
.format-select select {
  height: 30px;
  padding: 0 9px;
  color: var(--admin-text-secondary);
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  font: 500 12px/1 var(--reader-ui-font, "Microsoft YaHei", sans-serif);
  cursor: pointer;
  transition: color 0.18s ease, border-color 0.18s ease, background-color 0.18s ease;
}

.editor-toolbar button:hover,
.editor-toolbar button:focus-visible,
.format-select select:hover,
.format-select select:focus-visible {
  color: var(--admin-text);
  border-color: var(--admin-border-strong);
  outline: none;
  background: var(--admin-accent-soft);
}

.format-select select {
  min-width: 96px;
  appearance: auto;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  margin-inline: 3px;
  background: var(--admin-border-strong);
}

.editor-content {
  min-height: 320px;
  padding: 24px 28px;
  overflow-y: auto;
  outline: none;
  box-sizing: border-box;
  font: 400 15px/1.9 "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
  caret-color: var(--admin-seal);
}

.editor-content:empty::before {
  content: attr(data-placeholder);
  color: var(--admin-muted);
  pointer-events: none;
}

.editor-content:focus {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--admin-jade) 12%, transparent) inset;
}

.editor-content :deep(h2),
.editor-content :deep(h3),
.editor-content :deep(p),
.editor-content :deep(blockquote) {
  margin: 0 0 0.9em;
}

.editor-content :deep(blockquote) {
  padding: 8px 14px;
  color: var(--admin-text-secondary);
  border-left: 3px solid var(--admin-jade);
  background: color-mix(in srgb, var(--admin-jade) 6%, transparent);
}

.editor-content :deep(img) {
  max-width: 100%;
  height: auto;
  display: block;
  margin: 16px auto;
}

.editor-content :deep(a) {
  color: var(--admin-river);
}

.image-input,
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 620px) {
  .editor-content {
    min-height: 360px;
    padding: 20px 18px;
  }

  .toolbar-divider {
    display: none;
  }
}
</style>
