<template>
  <section class="export-page">
    <header class="page-intro">
      <div>
        <p>数据导出</p>
        <h1>数据导出</h1>
      </div>
      <span>按业务范围筛选并生成 Excel 文件</span>
    </header>

    <div class="export-list">
      <article class="export-row jade">
        <div class="export-identity">
          <span class="icon-frame"><DocumentCopy /></span>
          <div>
            <h2>借阅记录</h2>
            <p>流通明细与归还状态</p>
          </div>
        </div>
        <div class="export-filters three-fields">
          <el-input v-model="borrowFilter.userId" clearable placeholder="用户 ID" />
          <el-input v-model="borrowFilter.bookId" clearable placeholder="图书 ID" />
          <el-select v-model="borrowFilter.status" clearable placeholder="借阅状态">
            <el-option label="借阅中" :value="false" />
            <el-option label="已归还" :value="true" />
          </el-select>
        </div>
        <aside class="export-note">
          <strong>可选筛选</strong>
          <span>不填写条件时导出全部借阅记录。</span>
        </aside>
        <el-button type="primary" :disabled="demoMode" @click="exportBorrowRecords">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </article>

      <article class="export-row blue">
        <div class="export-identity">
          <span class="icon-frame"><Notebook /></span>
          <div>
            <h2>图书信息</h2>
            <p>馆藏目录与库存信息</p>
          </div>
        </div>
        <div class="export-filters">
          <el-input v-model="bookFilter.name" clearable placeholder="书名" />
          <el-input v-model="bookFilter.category" clearable placeholder="分类" />
        </div>
        <aside class="export-note">
          <strong>馆藏范围</strong>
          <span>可按书名或分类缩小导出范围。</span>
        </aside>
        <el-button type="primary" :disabled="demoMode" @click="exportBooks">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </article>

      <article class="export-row gold">
        <div class="export-identity">
          <span class="icon-frame"><UserFilled /></span>
          <div>
            <h2>用户信息</h2>
            <p>账号、角色与注册状态</p>
          </div>
        </div>
        <div class="export-filters no-filter">
          <span>此项无需筛选，导出字段由当前管理员权限决定。</span>
        </div>
        <aside class="export-note">
          <strong>权限保护</strong>
          <span>普通管理员不会获得超级管理员的敏感字段。</span>
        </aside>
        <el-button type="primary" :disabled="demoMode" @click="exportUsers">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </article>

      <article class="export-row danger">
        <div class="export-identity">
          <span class="icon-frame"><Warning /></span>
          <div>
            <h2>逾期记录</h2>
            <p>未归还记录与罚款金额</p>
          </div>
        </div>
        <div class="export-filters no-filter">
          <span>自动汇总当前处于逾期状态且尚未归还的记录。</span>
        </div>
        <aside class="export-note">
          <strong>风险数据</strong>
          <span>包含逾期天数和当前应计罚款。</span>
        </aside>
        <el-button type="danger" :disabled="demoMode" @click="exportOverdueRecords">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </article>
    </div>
  </section>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import {
  DocumentCopy,
  Download,
  Notebook,
  UserFilled,
  Warning,
} from "@element-plus/icons-vue";

export default {
  name: "DataExport",
  components: { DocumentCopy, Download, Notebook, UserFilled, Warning },
  data() {
    return {
      borrowFilter: { userId: "", bookId: "", status: null },
      bookFilter: { name: "", category: "" },
    };
  },
  computed: {
    demoMode() {
      return DEMO_MODE;
    },
  },
  methods: {
    async downloadFile(url, params) {
      if (this.demoMode) {
        this.$message.info("在线演示不生成真实导出文件。");
        return;
      }
      const query = Object.entries(params)
        .filter(([, value]) => value !== "" && value !== null && value !== undefined)
        .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
        .join("&");
      const fullUrl = query ? `${url}?${query}` : url;

      try {
        const response = await this.$axios.get(fullUrl, { responseType: "blob" });
        const blob = new Blob([response.data], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        const disposition = response.headers["content-disposition"];
        let fileName = "export.xlsx";
        if (disposition) {
          const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\n]+)/i);
          if (match) fileName = decodeURIComponent(match[1].replace(/^"|"$/g, ""));
        }
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(link.href);
        this.$message.success("导出成功");
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "导出失败，请稍后重试");
        console.error("export error:", error);
      }
    },
    exportBorrowRecords() {
      this.downloadFile("/export/borrowRecords", this.borrowFilter);
    },
    exportBooks() {
      this.downloadFile("/export/books", this.bookFilter);
    },
    exportUsers() {
      this.downloadFile("/export/users", {});
    },
    exportOverdueRecords() {
      this.downloadFile("/export/overdueRecords", {});
    },
  },
};
</script>

<style scoped lang="scss">
.export-page {
  display: grid;
  gap: 22px;
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
    font-size: 32px;
  }

  > span {
    color: var(--admin-muted);
    font-size: 13px;
  }
}

.export-list {
  display: grid;
  border-top: 1px solid var(--admin-border-strong);
}

.export-row {
  --row-accent: var(--admin-jade);
  display: grid;
  grid-template-columns: minmax(190px, 0.85fr) minmax(290px, 1.45fr) minmax(190px, 0.8fr) auto;
  gap: 18px;
  align-items: center;
  min-width: 0;
  padding: 20px 6px;
  border-bottom: 1px solid var(--admin-border-strong);

  &.blue {
    --row-accent: var(--admin-blue);
  }

  &.gold {
    --row-accent: var(--admin-gold);
  }

  &.danger {
    --row-accent: var(--admin-danger);
  }
}

.export-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  h2,
  p {
    margin: 0;
  }

  h2 {
    color: var(--admin-text);
    font-size: 17px;
  }

  p {
    margin-top: 5px;
    color: var(--admin-muted);
    font-size: 11px;
  }
}

.icon-frame {
  width: 40px;
  height: 40px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--row-accent) 42%, transparent);
  border-radius: 5px;
  color: var(--row-accent);
  background: color-mix(in srgb, var(--row-accent) 9%, transparent);

  svg {
    width: 20px;
    height: 20px;
  }
}

.export-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  min-width: 0;

  &.three-fields {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  &.no-filter {
    display: flex;
    align-items: center;
    min-height: 36px;
    color: var(--admin-text-secondary);
    font-size: 13px;
    line-height: 1.55;
  }
}

.export-note {
  min-height: 58px;
  padding: 9px 12px;
  border-left: 3px solid var(--row-accent);
  color: var(--admin-text-secondary);
  background: var(--admin-surface-muted);

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--admin-text);
    font-size: 12px;
  }

  span {
    margin-top: 4px;
    font-size: 11px;
    line-height: 1.5;
  }
}

.export-row > .el-button {
  min-width: 86px;
}

@media (max-width: 1180px) {
  .export-row {
    grid-template-columns: minmax(180px, 0.8fr) minmax(280px, 1.4fr) auto;
  }

  .export-note {
    grid-column: 2;
    grid-row: 2;
  }
}

@media (max-width: 840px) {
  .page-intro {
    align-items: flex-start;
    flex-direction: column;
  }

  .export-row {
    grid-template-columns: 1fr auto;
  }

  .export-filters,
  .export-note {
    grid-column: 1 / -1;
  }

  .export-note {
    grid-row: auto;
  }
}

@media (max-width: 560px) {
  .export-row,
  .export-filters,
  .export-filters.three-fields {
    grid-template-columns: 1fr;
  }

  .export-row > .el-button {
    width: 100%;
  }
}
</style>
