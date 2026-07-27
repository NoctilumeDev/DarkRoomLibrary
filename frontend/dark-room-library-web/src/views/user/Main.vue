<template>
  <section class="reader-page">
    <div class="page-title">
      <p>LEAVE A NOTE</p>
      <h1>留言处</h1>
      <span>写给守夜人，也可以附上 PDF、Word、图片或 HTML 文件。</span>
    </div>

    <div class="composer">
      <el-input
        v-model="content"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 7 }"
        placeholder="写点什么吧..."
        maxlength="1000"
        show-word-limit
      />
      <div class="message-actions">
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :disabled="demoMode"
          :show-file-list="false"
          :accept="attachmentAccept"
          :before-upload="beforeAttachmentUpload"
          :on-success="handleAttachmentSuccess"
        >
          <el-button class="ghost-button">上传附件</el-button>
        </el-upload>
        <div v-if="attachment.url" class="selected-attachment">
          <span>{{ attachment.name }}</span>
          <el-button text type="warning" @click="removeAttachment">移除</el-button>
        </div>
        <el-button class="warm-button" @click="handleSubmit">发表留言</el-button>
      </div>
    </div>

    <div class="message-list">
      <article v-for="item in tableData" :key="item.id" class="message-card">
        <div class="message-head">
          <el-avatar :size="38" :src="getAvatarUrl(item.userAvatar)">
            {{ item.userName ? item.userName.charAt(0) : "?" }}
          </el-avatar>
          <div>
            <strong>{{ item.userName || "匿名读者" }}</strong>
            <span>{{ item.createTime }}</span>
          </div>
        </div>
        <p>{{ item.content }}</p>
        <div v-if="item.reply" class="reply-box">
          <span class="reply-tag">守夜人回复</span>
          {{ item.reply }}
        </div>
        <div v-if="item.attachmentUrl" class="message-attachment">
          <el-image
            v-if="isImageAttachment(item) && item._previewUrl"
            class="message-image"
            :src="item._previewUrl"
            fit="cover"
            :preview-src-list="[item._previewUrl]"
          />
          <button
            v-else
            type="button"
            class="attachment-link"
            @click="downloadAttachment(item)"
          >
            {{ item.attachmentName || "下载附件" }}
          </button>
        </div>
      </article>

      <el-empty
        v-if="!tableData.length"
        description="这里还没有留言。"
      />
    </div>

    <el-pagination
      class="pager"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      :current-page="currentPage"
      :page-sizes="[5, 10]"
      :page-size="pageSize"
      layout="total, prev, pager, next"
      :total="totalItems"
    />
  </section>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import {
  buildApiUrl,
  resolveFileUrl,
  toApiRequestPath,
} from "@/utils/fileUrl.js";
import { getToken } from "@/utils/storage.js";

export default {
  name: "MessageBoard",
  data() {
    return {
      content: "",
      attachment: {
        url: "",
        name: "",
        type: "",
      },
      attachmentAccept:
        ".pdf,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp,.html,.htm",
      tableData: [],
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      attachmentObjectUrls: [],
    };
  },
  created() {
    this.fetchData();
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
      return token ? { token } : {};
    },
  },
  methods: {
    getAvatarUrl(url) {
      return resolveFileUrl(url);
    },
    isImageAttachment(item) {
      const type = (item.attachmentType || "").toLowerCase();
      return ["jpg", "jpeg", "png", "gif", "webp"].includes(type);
    },
    beforeAttachmentUpload(file) {
      if (this.demoMode) {
        this.$message.info("在线演示不上传真实附件。");
        return false;
      }
      const extension = this.getFileExtension(file.name);
      const allowed = [
        "pdf",
        "doc",
        "docx",
        "jpg",
        "jpeg",
        "png",
        "gif",
        "webp",
        "html",
        "htm",
      ];
      if (!allowed.includes(extension)) {
        this.$message.error("仅支持 PDF、Word、图片和 HTML 附件。");
        return false;
      }
      if (file.size > 10 * 1024 * 1024) {
        this.$message.error("附件大小不能超过 10MB。");
        return false;
      }
      return true;
    },
    getFileExtension(fileName) {
      const index = fileName.lastIndexOf(".");
      return index === -1 ? "" : fileName.slice(index + 1).toLowerCase();
    },
    handleAttachmentSuccess(res, file) {
      if (res.code !== 200) {
        this.$message.error(res.msg || "附件上传失败。");
        return;
      }
      this.attachment = {
        url: res.data,
        name: file.name,
        type: this.getFileExtension(file.name),
      };
      this.$message.success("附件上传成功。");
    },
    removeAttachment() {
      this.attachment = { url: "", name: "", type: "" };
    },
    async fetchData() {
      try {
        const response = await this.$axios.post("/messageBoard/query", {
          current: this.currentPage,
          size: this.pageSize,
        });
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
          await this.loadAttachmentPreviews();
        }
      } catch (error) {
        console.error("查询留言失败:", error);
      }
    },
    async loadAttachmentPreviews() {
      this.revokeAttachmentPreviews();
      const imageRows = this.tableData.filter(
        (item) => item.attachmentUrl && this.isImageAttachment(item)
      );
      await Promise.all(
        imageRows.map(async (item) => {
          try {
            const response = await this.$axios.get(
              toApiRequestPath(item.attachmentUrl),
              { responseType: "blob" }
            );
            const objectUrl = URL.createObjectURL(response.data);
            item._previewUrl = objectUrl;
            this.attachmentObjectUrls.push(objectUrl);
          } catch {
            item._previewUrl = "";
          }
        })
      );
    },
    revokeAttachmentPreviews() {
      this.attachmentObjectUrls.forEach((url) => URL.revokeObjectURL(url));
      this.attachmentObjectUrls = [];
    },
    async downloadAttachment(item) {
      if (this.demoMode) {
        this.$message.info("在线演示不下载真实附件。");
        return;
      }
      try {
        const response = await this.$axios.get(
          toApiRequestPath(item.attachmentUrl),
          { responseType: "blob" }
        );
        const objectUrl = URL.createObjectURL(response.data);
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = item.attachmentName || "attachment";
        link.click();
        URL.revokeObjectURL(objectUrl);
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "附件下载失败。");
      }
    },
    async handleSubmit() {
      if (!this.content.trim() && !this.attachment.url) {
        this.$message.warning("请输入留言内容或上传附件。");
        return;
      }
      try {
        const response = await this.$axios.post("/messageBoard/save", {
          content: this.content.trim(),
          attachmentUrl: this.attachment.url,
          attachmentName: this.attachment.name,
          attachmentType: this.attachment.type,
        });
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.content = "";
          this.removeAttachment();
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("留言失败。");
      }
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchData();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchData();
    },
  },
  beforeUnmount() {
    this.revokeAttachmentPreviews();
  },
};
</script>

<style scoped lang="scss">
.reader-page {
  display: grid;
  gap: 18px;
}

.page-title,
.composer,
.message-card {
  border: 1px solid rgba(229, 185, 121, 0.14);
  border-radius: 8px;
  background: rgba(28, 24, 19, 0.76);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.18);
}

.page-title {
  padding: 26px;

  p {
    margin: 0;
    color: #d0a15e;
    font-size: 12px;
    font-weight: 700;
  }

  h1 {
    margin: 6px 0 8px;
    color: #fff5df;
    font-size: clamp(32px, 4vw, 52px);
  }

  span {
    color: rgba(239, 229, 213, 0.68);
  }
}

.composer {
  padding: 18px;
}

.message-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.selected-attachment {
  display: flex;
  align-items: center;
  gap: 8px;
  color: rgba(239, 229, 213, 0.72);
  font-size: 13px;
}

.message-list {
  display: grid;
  gap: 14px;
}

.message-card {
  padding: 18px;

  p {
    margin: 14px 0 0 48px;
    color: rgba(239, 229, 213, 0.76);
    line-height: 1.8;
  }
}

.message-head {
  display: flex;
  align-items: center;
  gap: 10px;

  strong,
  span {
    display: block;
  }

  strong {
    color: #fff3da;
  }

  span {
    margin-top: 3px;
    color: rgba(239, 229, 213, 0.52);
    font-size: 12px;
  }
}

.message-attachment {
  margin: 12px 0 0 48px;
}

.message-image {
  width: 132px;
  height: 96px;
  border-radius: 6px;
}

.attachment-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: #f0cb8c;
  text-decoration: none;
  cursor: pointer;
}

.warm-button {
  border: 0;
  color: #251a10;
  background: #d8a45f;
  font-weight: 700;
}

.ghost-button {
  color: rgba(239, 229, 213, 0.74);
  border-color: rgba(239, 229, 213, 0.14);
  background: rgba(255, 255, 255, 0.04);
}

.reply-box {
  margin-top: 10px;
  padding: 12px 14px;
  border-radius: 6px;
  background: rgba(95, 154, 119, 0.1);
  border-left: 3px solid #5f9a77;
  color: rgba(238, 228, 210, 0.8);
  font-size: 14px;
  line-height: 1.7;
}

.reply-tag {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 8px;
  border-radius: 4px;
  background: rgba(95, 154, 119, 0.25);
  color: #5f9a77;
  font-size: 12px;
  font-weight: 600;
}

.pager {
  justify-self: end;
}

@media (max-width: 760px) {
  .message-card p,
  .message-attachment {
    margin-left: 0;
  }
}
</style>
