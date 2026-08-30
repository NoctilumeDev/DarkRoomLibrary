<template>
  <section class="reader-page">
    <div class="page-title">
      <p>藏光</p>
      <h1>我的收藏</h1>
      <span>把暂时不借的书先藏起来，等合适的时候再接过它。</span>
    </div>

    <div class="table-card">
      <el-table class="desktop-table" :data="tableData" v-loading="loading">
        <el-table-column label="封面" width="100">
          <template #default="scope">
            <el-image
              v-if="scope.row.bookCover"
              class="cover"
              :src="getImageUrl(scope.row.bookCover)"
              fit="cover"
            />
            <span v-else class="muted">无封面</span>
          </template>
        </el-table-column>
        <el-table-column prop="bookName" label="书名" min-width="190" />
        <el-table-column prop="bookAuthor" label="作者" width="150" />
        <el-table-column prop="availableCount" label="可借数量" width="100">
          <template #default="scope">
            <span :class="scope.row.availableCount > 0 ? 'ok' : 'wait'">
              {{ scope.row.availableCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="收藏时间" width="168" />
        <el-table-column label="操作" width="250">
          <template #default="scope">
            <el-button
              v-if="scope.row.availableCount > 0"
              size="small"
              class="reader-action reader-action--primary"
              @click="handleBorrow(scope.row)"
            >
              借阅
            </el-button>
            <el-button
              v-if="scope.row.availableCount <= 0"
              size="small"
              class="reader-action reader-action--reserve"
              @click="handleReserve(scope.row)"
            >
              预约
            </el-button>
            <el-button
              text
              class="reader-action reader-action--quiet"
              @click="openBook(scope.row)"
            >详情</el-button>
            <el-button
              text
              class="reader-action reader-action--danger"
              @click="handleRemoveFavorite(scope.row)"
            >
              取消收藏
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-loading="loading" class="mobile-favorite-list">
        <article
          v-for="row in tableData"
          :key="row.id"
          class="mobile-favorite"
        >
          <div class="mobile-cover">
            <el-image
              v-if="row.bookCover"
              class="cover"
              :src="getImageUrl(row.bookCover)"
              fit="cover"
            />
            <span v-else>无封面</span>
          </div>
          <div class="mobile-favorite__copy">
            <header>
              <strong>{{ row.bookName }}</strong>
              <span :class="row.availableCount > 0 ? 'ok' : 'wait'">
                {{ row.availableCount > 0 ? `可借 ${row.availableCount}` : "需预约" }}
              </span>
            </header>
            <p>{{ row.bookAuthor || "未知作者" }}</p>
            <small>收藏于 {{ row.createTime || "--" }}</small>
          </div>
          <footer>
            <el-button
              v-if="row.availableCount > 0"
              size="small"
              class="reader-action reader-action--primary"
              @click="handleBorrow(row)"
            >
              借阅
            </el-button>
            <el-button
              v-else
              size="small"
              class="reader-action reader-action--reserve"
              @click="handleReserve(row)"
            >
              预约
            </el-button>
            <el-button
              text
              class="reader-action reader-action--quiet"
              @click="openBook(row)"
            >
              详情
            </el-button>
            <el-button
              text
              class="reader-action reader-action--danger"
              @click="handleRemoveFavorite(row)"
            >
              取消收藏
            </el-button>
          </footer>
        </article>
        <el-empty
          v-if="!loading && !tableData.length"
          description="还没有收藏记录"
        />
      </div>

      <el-pagination
        class="pager"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[5, 10]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next"
        :total="totalItems"
      />
    </div>
  </section>
</template>

<script>
import { resolveFileUrl } from "@/utils/fileUrl.js";

export default {
  name: "MyFavorites",
  data() {
    return {
      tableData: [],
      loading: false,
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    getImageUrl(url) {
      return resolveFileUrl(url);
    },
    openBook(row) {
      this.$router.push({ path: "/bookSearch", query: { name: row.bookName, open: String(row.bookId) } });
    },
    async fetchData() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/bookFavorite/query", {
          current: this.currentPage,
          size: this.pageSize,
        });
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        }
      } catch (error) {
        console.error("查询收藏失败:", error);
        this.$message.error("收藏记录加载失败。");
      } finally {
        this.loading = false;
      }
    },
    async handleBorrow(row) {
      const confirmed = await this.$swal.fire({
        title: "确认借阅",
        text: `确认借阅《${row.bookName}》？`,
        icon: "question",
        showCancelButton: true,
        confirmButtonText: "确认",
        cancelButtonText: "取消",
      });
      if (!confirmed.value) return;
      try {
        const response = await this.$axios.post(
          `/borrowRecord/borrow/${row.bookId}`
        );
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("借阅操作失败。");
      }
    },
    async handleReserve(row) {
      try {
        const response = await this.$axios.post(
          `/bookReservation/reserve/${row.bookId}`
        );
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("预约操作失败。");
      }
    },
    async handleRemoveFavorite(row) {
      try {
        const response = await this.$axios.post(
          `/bookFavorite/remove/${row.bookId}`
        );
        if (response.data.code === 200) {
          this.$message.success("已取消收藏。");
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("取消收藏失败。");
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
};
</script>

<style scoped lang="scss">
.reader-page {
  display: grid;
  gap: 18px;
}

.page-title,
.table-card {
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

.table-card {
  padding: 18px;
}

.cover {
  width: 58px;
  height: 78px;
  border-radius: 6px;
}

.mobile-favorite-list {
  display: none;
}

.pager {
  margin-top: 18px;
  justify-content: flex-end;
}

.ok {
  color: var(--status-positive-text);
}

.wait {
  color: var(--status-warning-text);
}

.muted {
  color: rgba(239, 229, 213, 0.5);
}

@media (max-width: 760px) {
  .desktop-table {
    display: none;
  }

  .mobile-favorite-list {
    display: grid;
  }

  .mobile-favorite {
    display: grid;
    grid-template-columns: 66px minmax(0, 1fr);
    gap: 14px;
    padding: 18px 0;
    border-bottom: 1px solid var(--paper-line);

    &:first-child {
      padding-top: 0;
    }

    footer {
      grid-column: 1 / -1;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
  }

  .mobile-cover {
    width: 66px;
    height: 88px;
    display: grid;
    place-items: center;
    overflow: hidden;
    color: var(--paper-ink-faint);
    background: var(--paper-soft);
    font-size: 11px;

    .cover {
      width: 100%;
      height: 100%;
      border-radius: 1px;
    }
  }

  .mobile-favorite__copy {
    min-width: 0;

    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 10px;
    }

    strong {
      min-width: 0;
      overflow-wrap: anywhere;
      color: var(--paper-ink);
      font-family: var(--reader-serif);
      font-size: 18px;
      font-weight: 600;
    }

    header span {
      flex: none;
      font-size: 12px;
    }

    p,
    small {
      display: block;
      color: var(--paper-ink-soft);
    }

    p {
      margin: 8px 0 0;
      font-size: 13px;
    }

    small {
      margin-top: 8px;
      color: var(--paper-ink-faint);
      font-size: 11px;
      line-height: 1.5;
    }
  }

  .pager {
    justify-content: flex-start;
    max-width: 100%;
    overflow-x: auto;

    :deep(.el-pagination__total),
    :deep(.el-pagination__sizes) {
      display: none;
    }
  }
}
</style>
