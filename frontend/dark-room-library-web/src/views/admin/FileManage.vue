<template>
  <section class="file-page">
    <header class="page-head">
      <div>
        <p>文件流转</p>
        <h2>文件管理</h2>
      </div>
      <el-button
        type="warning"
        :disabled="demoMode"
        :loading="cleaning"
        @click="runCleanup"
      >
        <el-icon><Delete /></el-icon>
        清理孤儿文件
      </el-button>
    </header>

    <div class="toolbar">
      <el-input
        v-model="queryForm.originalName"
        placeholder="原始文件名"
        clearable
        @keyup.enter="search"
      />
      <el-select v-model="queryForm.status" placeholder="文件状态" clearable>
        <el-option label="临时" :value="0" />
        <el-option label="已绑定" :value="1" />
        <el-option label="待删除" :value="2" />
        <el-option label="删除中" :value="3" />
      </el-select>
      <el-select v-model="queryForm.refType" placeholder="引用类型" clearable>
        <el-option label="图书封面" value="book_cover" />
        <el-option label="用户头像" value="user_avatar" />
        <el-option label="留言附件" value="msg_attachment" />
        <el-option label="公告资源" value="notice_asset" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="tableData" row-key="fileName" v-loading="loading">
      <el-table-column prop="originalName" label="原始文件名" :min-width="isCompactViewport ? 130 : 190" show-overflow-tooltip />
      <el-table-column v-if="!isCompactViewport" prop="extension" label="类型" width="72" />
      <el-table-column v-if="!isCompactViewport" label="大小" width="96">
        <template #default="scope">{{ formatSize(scope.row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="状态" :width="isCompactViewport ? 80 : 96">
        <template #default="scope">
          <el-tag :type="statusInfo(scope.row.status).type" size="small">
            {{ statusInfo(scope.row.status).label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" label="业务引用" min-width="150">
        <template #default="scope">
          <span v-if="scope.row.refType">
            {{ refTypeName(scope.row.refType) }} #{{ scope.row.refId }}
          </span>
          <span v-else class="muted">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" label="上传人" width="120">
        <template #default="scope">
          {{ scope.row.uploaderName || (scope.row.uploaderId ? `#${scope.row.uploaderId}` : "已删除用户") }}
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" label="磁盘" width="82">
        <template #default="scope">
          <el-tag :type="scope.row.diskExists ? 'success' : 'danger'" size="small">
            {{ scope.row.diskExists ? "正常" : "缺失" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" prop="createTime" label="上传时间" width="170" />
      <el-table-column v-if="tableData.length" label="操作" :width="isCompactViewport ? 112 : 150" fixed="right">
        <template #default="scope">
          <el-button
            text
            type="primary"
            :disabled="demoMode || !scope.row.diskExists"
            @click="openFile(scope.row)"
          >
            查看
          </el-button>
          <el-button
            text
            type="danger"
            :disabled="demoMode || scope.row.status === 1"
            @click="deleteFile(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :total="totalItems"
      layout="total, sizes, prev, pager, next"
      @size-change="search"
      @current-change="fetchData"
    />
  </section>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import compactViewport from "@/mixins/compactViewport.js";
import { Delete } from "@element-plus/icons-vue";
import { resolveFileUrl, toApiRequestPath } from "@/utils/fileUrl.js";

export default {
  name: "FileManage",
  mixins: [compactViewport],
  components: { Delete },
  data() {
    return {
      tableData: [],
      queryForm: {
        originalName: "",
        status: null,
        refType: "",
      },
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      loading: false,
      cleaning: false,
    };
  },
  created() {
    this.fetchData();
  },
  computed: {
    demoMode() {
      return DEMO_MODE;
    },
  },
  methods: {
    async fetchData() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/file/query", {
          ...this.queryForm,
          current: this.currentPage,
          size: this.pageSize,
        });
        if (response.data.code === 200) {
          this.tableData = response.data.data || [];
          this.totalItems = response.data.total || 0;
        } else {
          this.$message.error(response.data.msg || "文件列表加载失败");
        }
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "文件列表加载失败");
      } finally {
        this.loading = false;
      }
    },
    search() {
      this.currentPage = 1;
      this.fetchData();
    },
    reset() {
      this.queryForm = { originalName: "", status: null, refType: "" };
      this.search();
    },
    formatSize(size) {
      if (!Number.isFinite(Number(size))) return "-";
      if (size < 1024) return `${size} B`;
      if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
      return `${(size / 1024 / 1024).toFixed(1)} MB`;
    },
    statusInfo(status) {
      return {
        0: { label: "临时", type: "warning" },
        1: { label: "已绑定", type: "success" },
        2: { label: "待删除", type: "danger" },
        3: { label: "删除中", type: "info" },
      }[status] || { label: "未知", type: "info" };
    },
    refTypeName(type) {
      return {
        book_cover: "图书封面",
        user_avatar: "用户头像",
        msg_attachment: "留言附件",
        notice_asset: "公告资源",
      }[type] || type;
    },
    async openFile(row) {
      if (this.demoMode) {
        this.$message.info("在线演示不读取真实文件。");
        return;
      }
      if (!row.accessUrl.includes("/file/download")) {
        window.open(resolveFileUrl(row.accessUrl), "_blank", "noopener,noreferrer");
        return;
      }
      await this.downloadBlob(row, false);
    },
    async downloadBlob(row, forceDownload) {
      try {
        const response = await this.$axios.get(toApiRequestPath(row.accessUrl), {
          responseType: "blob",
        });
        const objectUrl = URL.createObjectURL(response.data);
        const link = document.createElement("a");
        link.href = objectUrl;
        link.target = forceDownload ? "_self" : "_blank";
        if (forceDownload) link.download = row.originalName || row.fileName;
        link.click();
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "文件读取失败");
      }
    },
    async deleteFile(row) {
      if (this.demoMode) {
        this.$message.info("在线演示不删除真实文件。");
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "删除未绑定文件？",
        text: row.originalName,
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.delete("/file/unbound", {
          params: { fileName: row.fileName },
        });
        this.$message[response.data.code === 200 ? "success" : "error"](response.data.msg);
        if (response.data.code === 200) this.fetchData();
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "文件删除失败");
      }
    },
    async runCleanup() {
      if (this.demoMode) {
        this.$message.info("在线演示不执行真实文件清理。");
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "执行文件清理？",
        text: "将删除超过保留期的临时文件和无业务引用的孤儿文件。",
        icon: "warning",
      });
      if (!confirmed) return;
      this.cleaning = true;
      try {
        const response = await this.$axios.post("/file/cleanup");
        if (response.data.code === 200) {
          const result = response.data.data || {};
          this.$message.success(
            `清理完成：记录 ${result.metadataDeleted || 0}，孤儿文件 ${result.diskOrphansDeleted || 0}`
          );
          this.fetchData();
        } else {
          this.$message.error(response.data.msg || "清理失败");
        }
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "清理失败");
      } finally {
        this.cleaning = false;
      }
    },
  },
};
</script>

<style scoped lang="scss">
.file-page {
  padding: 0;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  p {
    margin: 0 0 4px;
    color: var(--admin-gold);
    font-size: 11px;
    font-weight: 700;
  }

  h2 {
    margin: 0;
    color: var(--admin-text);
    font-size: 22px;
  }
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) 140px 160px auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.muted {
  color: var(--admin-muted);
}

.pagination {
  justify-content: flex-end;
  margin-top: 18px;
}

@media (max-width: 900px) {
  .toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .toolbar > :nth-child(-n + 3) {
    grid-column: 1 / -1;
  }

  .toolbar :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
