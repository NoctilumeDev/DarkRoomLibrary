<template>
  <section class="reader-page">
    <div class="page-title">
      <p>WAITING QUEUE</p>
      <h1>我的预约</h1>
      <span>当灯暂时不在架上，可以先排队等待它归来。</span>
    </div>

    <div v-loading="loading" class="table-card">
      <el-empty
        v-if="!loading && !tableData.length"
        description="还没有预约记录"
      />
      <el-table v-else :data="tableData">
        <el-table-column prop="bookName" label="图书名称" min-width="200" />
        <el-table-column prop="reserveTime" label="预约时间" width="168" />
        <el-table-column prop="notifyTime" label="通知时间" width="168">
          <template #default="scope">{{ scope.row.notifyTime || "--" }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="118">
          <template #default="scope">
            <el-tag
              :class="[
                'reader-status',
                `reader-status--${statusMeta(scope.row.status).tone}`,
              ]"
            >
              {{ statusMeta(scope.row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="取书时限" width="150">
          <template #default="scope">
            <span v-if="scope.row.status === 3" class="pickup-time">
              {{ pickupRemaining(scope.row.notifyTime) }}
            </span>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 0"
              text
              class="reader-action reader-action--danger"
              @click="handleCancel(scope.row)"
            >
              取消预约
            </el-button>
            <span v-else class="muted">--</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="totalItems > 0"
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
  name: "MyReservations",
  data() {
    return {
      tableData: [],
      loading: false,
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      reservationRequestId: 0,
    };
  },
  created() {
    this.fetchData();
  },
  beforeUnmount() {
    this.reservationRequestId += 1;
  },
  methods: {
    pickupRemaining(notifyTime) {
      if (!notifyTime) return "请尽快处理";
      const deadline = new Date(notifyTime).getTime() + 48 * 60 * 60 * 1000;
      const remaining = deadline - Date.now();
      if (remaining <= 0) return "即将释放";
      const hours = Math.ceil(remaining / (60 * 60 * 1000));
      return hours > 24 ? `剩余 ${Math.ceil(hours / 24)} 天` : `剩余 ${hours} 小时`;
    },
    statusMeta(status) {
      const map = {
        0: { label: "预约中", tone: "waiting" },
        1: { label: "已借阅", tone: "complete" },
        2: { label: "已取消", tone: "neutral" },
        3: { label: "已通知", tone: "notified" },
        4: { label: "已过期", tone: "danger" },
      };
      return map[status] || { label: "未知", tone: "neutral" };
    },
    async fetchData() {
      const requestId = ++this.reservationRequestId;
      this.loading = true;
      try {
        const response = await this.$axios.post("/bookReservation/query", {
          current: this.currentPage,
          size: this.pageSize,
        });
        if (requestId !== this.reservationRequestId) return;
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        } else {
          this.$message.error(data.msg || "预约记录加载失败。");
        }
      } catch (error) {
        if (requestId !== this.reservationRequestId) return;
        console.error("查询预约失败:", error);
        this.$message.error("预约记录加载失败。");
      } finally {
        if (requestId === this.reservationRequestId) this.loading = false;
      }
    },
    async handleCancel(row) {
      const confirmed = await this.$swalConfirm({
        title: "取消预约",
        text: `确认取消《${row.bookName}》的预约？`,
        icon: "warning",
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.post(`/bookReservation/cancel/${row.id}`);
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.fetchData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("取消预约失败。");
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

.pager {
  margin-top: 18px;
  justify-content: flex-end;
}

.muted {
  color: rgba(239, 229, 213, 0.5);
}

.pickup-time { color: var(--seal); font-weight: 600; }
</style>
