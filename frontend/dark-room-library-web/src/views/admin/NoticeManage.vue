<template>
  <el-row class="admin-table-page notice-manage-page">
    <el-row class="notice-toolbar">
      <el-row class="notice-toolbar__controls">
        <span class="top-bar">公告标题</span>
        <el-input
          v-model="filters.name"
          class="notice-title-filter"
          size="small"
          placeholder="输入标题"
          clearable
          @clear="applyFilters"
          @keyup.enter="applyFilters"
        />

        <span class="top-bar">发布时间</span>
        <el-date-picker
          v-model="publishedRange"
          class="notice-date-filter"
          size="small"
          type="daterange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />

        <el-button
          class="customer"
          size="small"
          type="primary"
          @click="applyFilters"
        >
          立即查询
        </el-button>
        <el-button
          class="customer"
          size="small"
          type="info"
          @click="openCreatePage"
        >
          新增公告
        </el-button>
        <el-button
          class="customer reset"
          size="small"
          type="info"
          @click="resetFilters"
        >
          条件重置
        </el-button>
        <el-button
          class="customer"
          size="small"
          type="danger"
          :disabled="selectedRows.length === 0"
          @click="deleteSelected"
        >
          批量删除
        </el-button>
      </el-row>
    </el-row>

    <el-row class="notice-table-section">
      <el-table
        v-loading="loading"
        :data="notices"
        row-key="id"
        class="notice-table"
        @selection-change="selectedRows = $event"
      >
        <el-table-column type="selection" :width="isCompactViewport ? 42 : 55" />
        <el-table-column prop="name" :width="isCompactViewport ? 140 : 508" label="公告" />
        <el-table-column v-if="!isCompactViewport" prop="createTime" width="188" label="发布时间" />
        <el-table-column label="操作" :width="isCompactViewport ? 104 : 140" fixed="right">
          <template #default="{ row }">
            <span class="text-button" @click="openEditPage(row)">修改</span>
            <span class="text-button" @click="deleteOne(row)">删除</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="notice-pagination"
        :page-sizes="[8, 20]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
        @size-change="handlePageSizeChange"
        @current-change="loadNotices"
      />
    </el-row>
  </el-row>
</template>

<script>
import { toDayRange } from "@/utils/pageQuery.js";
import compactViewport from "@/mixins/compactViewport.js";

const NOTICE_OPERATION_KEY = "noticeOperation";
const NOTICE_DRAFT_KEY = "noticeInfo";

export default {
  name: "NoticeManage",
  mixins: [compactViewport],
  data() {
    return {
      currentPage: 1,
      pageSize: 8,
      totalItems: 0,
      notices: [],
      publishedRange: [],
      selectedRows: [],
      filters: {
        name: "",
      },
      loading: false,
    };
  },
  created() {
    this.loadNotices();
  },
  methods: {
    buildQuery() {
      return {
        current: this.currentPage,
        size: this.pageSize,
        ...toDayRange(this.publishedRange),
        name: this.filters.name?.trim() || null,
      };
    },
    async loadNotices() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/notice/query", this.buildQuery());
        const result = response.data;
        if (result.code !== 200) {
          this.$message.error(result.msg || "公告查询失败");
          return;
        }
        this.notices = result.data || [];
        this.totalItems = result.total || 0;
      } catch (error) {
        console.error("查询公告信息异常:", error);
        this.$message.error(error.response?.data?.msg || "公告查询失败");
      } finally {
        this.loading = false;
      }
    },
    applyFilters() {
      this.currentPage = 1;
      this.loadNotices();
    },
    resetFilters() {
      this.filters = { name: "" };
      this.publishedRange = [];
      this.currentPage = 1;
      this.loadNotices();
    },
    handlePageSizeChange() {
      this.currentPage = 1;
      this.loadNotices();
    },
    openCreatePage() {
      sessionStorage.removeItem(NOTICE_DRAFT_KEY);
      sessionStorage.setItem(NOTICE_OPERATION_KEY, "save");
      this.$router.push("/createNotice");
    },
    openEditPage(notice) {
      sessionStorage.setItem(NOTICE_DRAFT_KEY, JSON.stringify(notice));
      sessionStorage.setItem(NOTICE_OPERATION_KEY, "update");
      this.$router.push("/createNotice");
    },
    deleteOne(notice) {
      this.deleteNotices([notice.id]);
    },
    deleteSelected() {
      this.deleteNotices(this.selectedRows.map(({ id }) => id));
    },
    async deleteNotices(ids) {
      if (ids.length === 0) {
        this.$message.warning("未选中任何数据");
        return;
      }

      const confirmed = await this.$swalConfirm({
        title: ids.length === 1 ? "删除公告" : "批量删除公告",
        text: "删除后不可恢复，是否继续？",
        icon: "warning",
      });
      if (!confirmed) return;

      try {
        const response = await this.$axios.post("/notice/batchDelete", ids);
        const result = response.data;
        if (result.code !== 200) {
          this.$message.error(result.msg || "公告删除失败");
          return;
        }
        this.$message.success(result.msg || "公告删除成功");
        this.selectedRows = [];
        await this.loadNotices();
        if (
          this.notices.length === 0 &&
          this.currentPage > 1 &&
          this.totalItems > 0
        ) {
          this.currentPage -= 1;
          await this.loadNotices();
        }
      } catch (error) {
        console.error("删除公告信息异常:", error);
        this.$message.error(error.response?.data?.msg || "公告删除失败");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.notice-toolbar {
  padding: 10px;
}

.notice-toolbar__controls {
  align-items: center;
}

.notice-title-filter {
  width: 188px;
}

.notice-toolbar__controls :deep(.notice-date-filter) {
  width: 220px;
}

.notice-table-section {
  margin: 10px;
}

.notice-table {
  width: 100%;
}

.notice-pagination {
  margin: 20px 0;
  margin-left: auto;
}

@media (max-width: 760px) {
  .notice-toolbar {
    width: 100%;
    padding: 0;
  }

  .notice-toolbar__controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
  }

  .notice-toolbar__controls > .top-bar {
    display: none;
  }

  .notice-title-filter,
  .notice-toolbar__controls :deep(.notice-date-filter) {
    grid-column: 1 / -1;
    width: 100%;
  }

  .notice-toolbar__controls :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
