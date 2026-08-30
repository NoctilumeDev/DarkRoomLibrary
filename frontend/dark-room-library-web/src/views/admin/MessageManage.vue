<template>
  <div class="admin-page message-manage-page">
    <el-row class="message-toolbar">
      <el-input
        v-model="filterKeyword"
        placeholder="搜索留言内容"
        style="width: 240px; margin-right: 10px"
        clearable
        @clear="fetchData"
        @keyup.enter="fetchData"
      />
      <el-button type="primary" @click="fetchData">搜索</el-button>
      <el-button type="danger" :disabled="!selectedRows.length" @click="batchDelete">批量删除</el-button>
    </el-row>

    <el-table :data="tableData" @selection-change="handleSelectionChange" row-key="id" style="width: 100%">
      <el-table-column type="selection" :width="isCompactViewport ? 42 : 50"></el-table-column>
      <el-table-column prop="userName" :width="isCompactViewport ? 72 : 100" label="用户"></el-table-column>
      <el-table-column prop="content" label="留言内容" show-overflow-tooltip></el-table-column>
      <el-table-column v-if="!isCompactViewport" width="130" label="附件">
        <template #default="scope">
          <el-button v-if="scope.row.attachmentUrl" text type="warning" @click="downloadAttachment(scope.row)">
            {{ scope.row.attachmentName || '下载' }}
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" prop="reply" label="回复" show-overflow-tooltip></el-table-column>
      <el-table-column v-if="!isCompactViewport" width="160" label="留言时间">
        <template #default="scope">{{ scope.row.createTime }}</template>
      </el-table-column>
      <el-table-column :width="isCompactViewport ? 108 : 140" label="操作" fixed="right">
        <template #default="scope">
          <el-button text size="small" @click="openReply(scope.row)">回复</el-button>
          <el-button text size="small" type="danger" @click="deleteOne(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-row style="margin-top: 16px; justify-content: flex-end">
      <el-pagination v-model:current-page="currentPage" :page-size="pageSize" :total="totalItems"
        layout="total, prev, pager, next" @current-change="fetchData"></el-pagination>
    </el-row>

    <!-- 回复弹窗 -->
    <el-dialog
      v-model="replyDialog"
      title="回复留言"
      class="admin-editor-dialog admin-editor-dialog--compact admin-message-reply-dialog"
      width="min(460px, calc(100vw - 24px))"
      top="16vh"
      append-to-body
    >
      <div class="admin-form-scroll">
        <div class="reply-context">
          <div><strong>{{ replyTarget.userName }}：</strong></div>
          <div>{{ replyTarget.content }}</div>
        </div>
        <el-input
          v-model="replyText"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="输入回复内容"
        ></el-input>
      </div>
      <template #footer>
        <el-button class="admin-dialog-cancel" @click="replyDialog = false">取消</el-button>
        <el-button class="admin-dialog-submit" type="primary" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import compactViewport from "@/mixins/compactViewport.js";
import { toApiRequestPath } from "@/utils/fileUrl.js";

export default {
  name: "MessageManage",
  mixins: [compactViewport],
  data() {
    return {
      tableData: [],
      selectedRows: [],
      filterKeyword: "",
      currentPage: 1,
      pageSize: 7,
      totalItems: 0,
      replyDialog: false,
      replyText: "",
      replyTarget: {},
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    async downloadAttachment(row) {
      if (DEMO_MODE) {
        this.$message.info("在线演示不下载真实附件。");
        return;
      }
      try {
        const response = await this.$axios.get(
          toApiRequestPath(row.attachmentUrl),
          { responseType: "blob" }
        );
        const objectUrl = URL.createObjectURL(response.data);
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = row.attachmentName || "attachment";
        link.click();
        URL.revokeObjectURL(objectUrl);
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "附件下载失败");
      }
    },
    async fetchData() {
      try {
        const params = { current: this.currentPage, size: this.pageSize };
        if (this.filterKeyword) params.content = this.filterKeyword;
        const response = await this.$axios.post("/messageBoard/query", params);
        if (response.data.code === 200) {
          this.tableData = response.data.data || [];
          this.totalItems = response.data.total || 0;
        }
      } catch (error) {
        console.error("查询留言异常:", error);
      }
    },
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    openReply(row) {
      this.replyTarget = row;
      this.replyText = row.reply || "";
      this.replyDialog = true;
    },
    async submitReply() {
      try {
        const response = await this.$axios.put("/messageBoard/reply", {
          id: this.replyTarget.id,
          reply: this.replyText,
        });
        if (response.data.code === 200) {
          this.$message.success("回复成功");
          this.replyDialog = false;
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (e) {
        this.$message.error(e.response?.data?.msg || "回复失败");
      }
    },
    async deleteOne(id) {
      const confirmed = await this.$swal.fire({
        title: "确认删除",
        text: "删除后不可恢复",
        icon: "warning",
        showCancelButton: true,
        confirmButtonText: "确认",
        cancelButtonText: "取消",
      });
      if (!confirmed.isConfirmed) return;
      try {
        const response = await this.$axios.post("/messageBoard/batchDelete", [id]);
        if (response.data.code === 200) {
          this.$message.success("删除成功");
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (e) {
        this.$message.error(e.response?.data?.msg || "删除失败");
      }
    },
    async batchDelete() {
      const confirmed = await this.$swal.fire({
        title: "确认批量删除",
        text: "删除后不可恢复",
        icon: "warning",
        showCancelButton: true,
        confirmButtonText: "确认",
        cancelButtonText: "取消",
      });
      if (!confirmed.isConfirmed) return;
      const ids = this.selectedRows.map((r) => r.id);
      try {
        const response = await this.$axios.post("/messageBoard/batchDelete", ids);
        if (response.data.code === 200) {
          this.$message.success("删除成功");
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (e) {
        this.$message.error(e.response?.data?.msg || "删除失败");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.message-manage-page {
  padding: 0;
}

.message-toolbar {
  margin-bottom: 16px;
}

.reply-context {
  margin-bottom: 12px;
  padding: 10px 12px;
  color: var(--admin-text-secondary);
  border-left: 3px solid var(--admin-gold);
  background: var(--admin-surface-muted);
  line-height: 1.6;
}

@media (max-width: 760px) {
  .message-toolbar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
  }

  .message-toolbar :deep(.el-input) {
    grid-column: 1 / -1;
    width: 100% !important;
    margin-right: 0 !important;
  }

  .message-toolbar :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
