<template>
  <section class="content-audit">
    <header class="page-head">
      <div>
        <p>内容复核</p>
        <h1>内容审核</h1>
      </div>
      <el-button class="refresh-button" :loading="loading" @click="fetchData">
        刷新
      </el-button>
    </header>

    <section class="filter-bar">
      <el-select
        v-model="query.status"
        clearable
        placeholder="举报状态"
        style="width: 150px"
        @change="handleFilter"
        @clear="handleFilter"
      >
        <el-option label="待处理" :value="0" />
        <el-option label="已处理" :value="1" />
        <el-option label="已忽略" :value="2" />
      </el-select>
      <el-input
        v-model="query.bookName"
        clearable
        placeholder="搜索书名"
        style="width: 220px"
        @clear="handleFilter"
        @keyup.enter="handleFilter"
      />
      <el-input
        v-model="query.reviewContent"
        clearable
        placeholder="搜索书评内容"
        style="width: 260px"
        @clear="handleFilter"
        @keyup.enter="handleFilter"
      />
      <el-button type="primary" @click="handleFilter">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
    </section>

    <el-table v-loading="loading" :data="tableData" row-key="id" class="audit-table">
      <el-table-column :width="isCompactViewport ? 64 : 92" label="状态">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" effect="light">
            {{ statusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="bookName" :min-width="isCompactViewport ? 72 : 150" label="图书" show-overflow-tooltip />
      <el-table-column v-if="!isCompactViewport" prop="reviewUserName" width="110" label="评论人" />
      <el-table-column :min-width="isCompactViewport ? 92 : 220" label="书评内容" show-overflow-tooltip>
        <template #default="scope">
          <span :class="{ muted: scope.row.reviewStatus === 1 }">
            {{ scope.row.reviewContent }}
          </span>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompactViewport" prop="reason" min-width="180" label="举报原因" show-overflow-tooltip />
      <el-table-column v-if="!isCompactViewport" prop="reportUserName" width="110" label="举报人" />
      <el-table-column v-if="!isCompactViewport" prop="createTime" width="168" label="举报时间" />
      <el-table-column v-if="!isCompactViewport" prop="handleTime" width="168" label="处理时间">
        <template #default="scope">
          {{ scope.row.handleTime || "-" }}
        </template>
      </el-table-column>
      <el-table-column :width="isCompactViewport ? 108 : 180" fixed="right" label="操作">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 0"
            size="small"
            type="warning"
            plain
            @click="ignoreReport(scope.row)"
          >
            忽略
          </el-button>
          <el-button
            v-if="scope.row.status === 0"
            size="small"
            type="danger"
            @click="hideReview(scope.row)"
          >
            隐藏书评
          </el-button>
          <span v-else class="muted">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-row">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="totalItems"
        layout="total, prev, pager, next"
        @current-change="fetchData"
      />
    </div>
  </section>
</template>

<script>
import compactViewport from "@/mixins/compactViewport.js";

export default {
  name: "ContentAudit",
  mixins: [compactViewport],
  data() {
    return {
      loading: false,
      tableData: [],
      query: {
        status: 0,
        bookName: "",
        reviewContent: "",
      },
      currentPage: 1,
      pageSize: 8,
      totalItems: 0,
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    statusText(status) {
      const map = {
        0: "待处理",
        1: "已处理",
        2: "已忽略",
      };
      return map[status] || "未知";
    },
    statusTag(status) {
      const map = {
        0: "warning",
        1: "success",
        2: "info",
      };
      return map[status] || "info";
    },
    async fetchData() {
      this.loading = true;
      try {
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          ...this.query,
        };
        if (params.status === "" || params.status === undefined) {
          delete params.status;
        }
        if (params.bookName) params.bookName = params.bookName.trim();
        if (params.reviewContent) params.reviewContent = params.reviewContent.trim();
        const response = await this.$axios.post("/bookReviewReport/query", params);
        if (response.data.code === 200) {
          this.tableData = response.data.data || [];
          this.totalItems = response.data.total || 0;
        } else {
          this.$message.error(response.data.msg || "举报列表加载失败。");
        }
      } catch (error) {
        console.error("举报列表加载失败:", error);
        this.$message.error("举报列表加载失败。");
      } finally {
        this.loading = false;
      }
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchData();
    },
    resetFilter() {
      this.query = {
        status: 0,
        bookName: "",
        reviewContent: "",
      };
      this.handleFilter();
    },
    async ignoreReport(row) {
      const confirmed = await this.$swalConfirm({
        title: "忽略举报",
        text: "确认将这条举报标记为已忽略？",
        icon: "warning",
      });
      if (!confirmed) return;
      await this.handleAction(`/bookReviewReport/ignore/${row.id}`, "已忽略举报。");
    },
    async hideReview(row) {
      const confirmed = await this.$swalConfirm({
        title: "隐藏书评",
        text: "隐藏后读者端将不再展示这条书评。",
        icon: "warning",
      });
      if (!confirmed) return;
      await this.handleAction(`/bookReviewReport/hideReview/${row.id}`, "已隐藏书评。");
    },
    async handleAction(url, successMessage) {
      try {
        const response = await this.$axios.post(url);
        if (response.data.code === 200) {
          this.$message.success(response.data.msg || successMessage);
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("操作失败。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.content-audit {
  display: grid;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;

  p {
    margin: 0;
    color: var(--admin-gold);
    font-size: 12px;
    font-weight: 700;
  }

  h1 {
    margin: 5px 0 0;
    color: var(--admin-text);
    font-size: 30px;
  }
}

.refresh-button,
.filter-bar,
.audit-table {
  border: 1px solid var(--admin-border);
  background: var(--admin-surface);
}

.refresh-button {
  color: var(--admin-text-secondary);
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px;
  border-radius: 6px;
}

@media (max-width: 760px) {
  .filter-bar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 12px;
  }

  .filter-bar :deep(.el-select),
  .filter-bar :deep(.el-input) {
    grid-column: 1 / -1;
    width: 100% !important;
  }

  .filter-bar :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}

.audit-table {
  border-radius: 6px;
}

.muted {
  color: var(--admin-muted);
}

.pager-row {
  display: flex;
  justify-content: flex-end;
}
</style>
