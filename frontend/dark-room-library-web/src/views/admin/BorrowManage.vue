<template>
  <el-row class="admin-table-page">
    <el-row style="padding: 10px; margin: 0 10px">
      <el-row>
        <span class="top-bar">用户ID</span>
        <el-input
          size="small"
          style="width: 120px; margin-right: 10px"
          v-model="queryDto.userId"
          placeholder="用户ID"
          clearable
        >
        </el-input>
        <span class="top-bar">图书ID</span>
        <el-input
          size="small"
          style="width: 120px; margin-right: 10px"
          v-model="queryDto.bookId"
          placeholder="图书ID"
          clearable
        >
        </el-input>
        <span class="top-bar">状态</span>
        <el-select
          size="small"
          style="width: 120px; margin-right: 10px"
          v-model="queryDto.status"
          clearable
          placeholder="全部"
        >
          <el-option label="借阅中" :value="false"></el-option>
          <el-option label="已归还" :value="true"></el-option>
        </el-select>
        <el-button
          size="small"
          class="customer"
          type="primary"
          @click="handleFilter"
          >立即查询</el-button
        >
        <el-button
          size="small"
          class="customer reset"
          type="info"
          @click="resetCondition"
          >条件重置</el-button
        >
      </el-row>
    </el-row>
    <el-row style="margin: 10px 20px">
      <el-table :data="tableData" style="width: 100%" row-key="id">
        <el-table-column prop="id" label="ID" width="80"></el-table-column>
        <el-table-column
          prop="userName"
          label="用户名"
          width="120"
        ></el-table-column>
        <el-table-column
          prop="bookName"
          label="书名"
          min-width="180"
        ></el-table-column>
        <el-table-column
          prop="borrowTime"
          label="借阅时间"
          width="180"
        ></el-table-column>
        <el-table-column
          prop="dueDate"
          label="应还日期"
          width="180"
        ></el-table-column>
        <el-table-column
          prop="returnTime"
          label="归还时间"
          width="180"
        ></el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag
              :class="[
                'circulation-status',
                scope.row.status
                  ? 'circulation-status--complete'
                  : 'circulation-status--borrowing',
              ]"
              size="small"
            >
              {{ scope.row.status ? "已归还" : "借阅中" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="fineAmount"
          label="罚款(元)"
          width="100"
        ></el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button
              v-if="!scope.row.status"
              class="circulation-action circulation-action--return"
              size="small"
              @click="handleReturn(scope.row)"
              >归还</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin: 20px 0; float: right"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[5, 10]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
  </el-row>
</template>

<script>
export default {
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      tableData: [],
      queryDto: {},
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    async fetchData() {
      try {
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          ...this.queryDto,
        };
        const response = await this.$axios.post("/borrowRecord/query", params);
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        }
      } catch (error) {
        console.error("查询借阅记录异常:", error);
      }
    },
    async handleReturn(row) {
      const confirmed = await this.$swalConfirm({
        title: "确认归还",
        text: "确定要归还此图书吗？",
        icon: "question",
      });
      if (confirmed) {
        try {
          const response = await this.$axios.post(
            `/borrowRecord/return/${row.id}`
          );
          if (response.data.code === 200) {
            this.$message.success(response.data.msg);
            this.fetchData();
          } else {
            this.$message.error(response.data.msg);
          }
        } catch (error) {
          const msg = error.response?.data?.msg || "归还失败"; this.$message.error(msg);
        }
      }
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchData();
    },
    resetCondition() {
      this.queryDto = {};
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
.top-bar {
  font-size: 14px;
  margin-right: 5px;
  color: var(--admin-text-secondary);
}
</style>
