<template>
  <section class="reader-page">
    <div class="page-title">
      <p>LOOK BACK</p>
      <h1>我的借阅</h1>
      <span>查看当前借阅、历史归还、续借与逾期状态。</span>
    </div>

    <div class="filter-bar">
      <el-select
        v-model="statusFilter"
        size="large"
        placeholder="借阅状态"
        clearable
        @change="handleFilter"
      >
        <el-option label="全部" value="all" />
        <el-option label="借阅中" :value="false" />
        <el-option label="已归还" :value="true" />
      </el-select>
      <el-select
        v-model="overdueFilter"
        size="large"
        placeholder="逾期状态"
        clearable
        @change="handleFilter"
      >
        <el-option label="全部" value="all" />
        <el-option label="已逾期" :value="true" />
        <el-option label="未逾期" :value="false" />
      </el-select>
      <el-button size="large" class="ghost-button" @click="resetCondition">
        重置
      </el-button>
    </div>

    <div class="table-card">
      <el-table class="desktop-table" :data="tableData" v-loading="loading">
        <el-table-column prop="bookName" label="书名" min-width="190" />
        <el-table-column prop="borrowTime" label="借阅时间" width="168" />
        <el-table-column prop="dueDate" label="应还日期" width="168">
          <template #default="scope">
            <span :class="{ overdue: isOverdue(scope.row) }">
              {{ scope.row.dueDate || "--" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="returnTime" label="归还时间" width="168">
          <template #default="scope">
            {{ scope.row.returnTime || "--" }}
          </template>
        </el-table-column>
        <el-table-column prop="renewCount" label="续借" width="82">
          <template #default="scope">{{ scope.row.renewCount || 0 }}/1</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="112">
          <template #default="scope">
            <el-tag
              :class="[
                'reader-status',
                `reader-status--${statusMeta(scope.row).tone}`,
              ]"
            >
              {{ statusMeta(scope.row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170">
          <template #default="scope">
            <template v-if="!scope.row.status">
              <el-button
                text
                class="reader-action reader-action--return"
                @click="handleReturn(scope.row)"
              >
                还书
              </el-button>
              <el-button
                text
                class="reader-action reader-action--renew"
                @click="handleRenew(scope.row)"
              >
                续借
              </el-button>
            </template>
            <span v-else class="muted">已归还</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-loading="loading" class="mobile-record-list">
        <article v-for="row in tableData" :key="row.id" class="mobile-record">
          <header>
            <strong>{{ row.bookName }}</strong>
            <el-tag
              :class="[
                'reader-status',
                `reader-status--${statusMeta(row).tone}`,
              ]"
            >
              {{ statusMeta(row).label }}
            </el-tag>
          </header>
          <dl>
            <div>
              <dt>借阅时间</dt>
              <dd>{{ row.borrowTime || "--" }}</dd>
            </div>
            <div>
              <dt>应还日期</dt>
              <dd :class="{ overdue: isOverdue(row) }">
                {{ row.dueDate || "--" }}
              </dd>
            </div>
            <div>
              <dt>归还时间</dt>
              <dd>{{ row.returnTime || "--" }}</dd>
            </div>
            <div>
              <dt>续借次数</dt>
              <dd>{{ row.renewCount || 0 }}/1</dd>
            </div>
          </dl>
          <footer v-if="!row.status">
            <el-button
              text
              class="reader-action reader-action--return"
              @click="handleReturn(row)"
            >
              还书
            </el-button>
            <el-button
              text
              class="reader-action reader-action--renew"
              @click="handleRenew(row)"
            >
              续借
            </el-button>
          </footer>
        </article>
        <el-empty
          v-if="!loading && !tableData.length"
          description="还没有借阅记录"
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
export default {
  name: "MyBorrows",
  data() {
    return {
      tableData: [],
      loading: false,
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      statusFilter: "all",
      overdueFilter: "all",
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    isOverdue(row) {
      if (row.status) return false;
      if (!row.dueDate) return false;
      return new Date(row.dueDate) < new Date();
    },
    statusMeta(row) {
      if (row.status) return { label: "已归还", tone: "complete" };
      if (this.isOverdue(row)) return { label: "已逾期", tone: "danger" };
      return { label: "借阅中", tone: "waiting" };
    },
    async fetchData() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/borrowRecord/query", {
          current: this.currentPage,
          size: this.pageSize,
          status:
            typeof this.statusFilter === "boolean" ? this.statusFilter : null,
          overdue:
            typeof this.overdueFilter === "boolean" ? this.overdueFilter : null,
        });
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        }
      } catch (error) {
        console.error("查询借阅记录失败:", error);
        this.$message.error("借阅记录加载失败。");
      } finally {
        this.loading = false;
      }
    },
    async handleReturn(row) {
      const confirmed = await this.$swalConfirm({
        title: "归还图书",
        text: `确认归还《${row.bookName}》？`,
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.post(`/borrowRecord/return/${row.id}`);
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("归还操作失败。");
      }
    },
    async handleRenew(row) {
      const confirmed = await this.$swalConfirm({
        title: "续借图书",
        text: `确认续借《${row.bookName}》？`,
        icon: "question",
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.post(`/borrowRecord/renew/${row.id}`);
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("续借操作失败。");
      }
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchData();
    },
    resetCondition() {
      this.statusFilter = "all";
      this.overdueFilter = "all";
      this.currentPage = 1;
      this.fetchData();
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
.filter-bar,
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

.filter-bar {
  display: grid;
  grid-template-columns: repeat(2, minmax(180px, 220px)) auto;
  align-items: center;
  justify-content: start;
  gap: 12px;
  padding: 16px;
}

.table-card {
  padding: 18px;
}

.mobile-record-list {
  display: none;
}

.pager {
  margin-top: 18px;
  justify-content: flex-end;
}

.ghost-button {
  color: rgba(239, 229, 213, 0.74);
  border-color: rgba(239, 229, 213, 0.14);
  background: rgba(255, 255, 255, 0.04);
}

.overdue {
  color: #ff9b8f;
}

.muted {
  color: rgba(239, 229, 213, 0.5);
}

@media (max-width: 760px) {
  .filter-bar {
    grid-template-columns: 1fr;

    :deep(.el-button) {
      width: 100%;
      margin-left: 0;
    }
  }

  .desktop-table {
    display: none;
  }

  .mobile-record-list {
    display: grid;
  }

  .mobile-record {
    padding: 18px 0;
    border-bottom: 1px solid var(--paper-line);

    &:first-child {
      padding-top: 0;
    }

    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    header strong {
      min-width: 0;
      overflow-wrap: anywhere;
      color: var(--paper-ink);
      font-family: var(--reader-serif);
      font-size: 18px;
      font-weight: 600;
    }

    dl {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      margin: 14px 0 0;
    }

    dl div {
      min-width: 0;
      padding: 10px 10px 10px 0;
      border-top: 1px solid var(--paper-line);
    }

    dt {
      color: var(--paper-ink-faint);
      font-size: 11px;
    }

    dd {
      margin: 5px 0 0;
      overflow-wrap: anywhere;
      color: var(--paper-ink-soft);
      font-size: 12px;
      line-height: 1.55;
    }

    footer {
      display: flex;
      gap: 14px;
      margin-top: 8px;
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
