<template>
  <el-row class="admin-table-page">
    <el-row style="padding: 10px; margin: 0 10px">
      <el-row>
        <span class="top-bar">操作类型</span>
        <el-select
          size="small"
          style="width: 120px; margin-right: 10px"
          v-model="queryDto.operation"
          placeholder="全部"
          clearable
          @change="handleFilter"
        >
          <el-option label="新增" value="新增"></el-option>
          <el-option label="修改" value="修改"></el-option>
          <el-option label="删除" value="删除"></el-option>
          <el-option label="审核" value="审核"></el-option>
          <el-option label="指派" value="指派"></el-option>
          <el-option label="认领" value="认领"></el-option>
          <el-option label="状态流转" value="流转"></el-option>
          <el-option label="取消" value="取消"></el-option>
          <el-option label="入库" value="入库"></el-option>
          <el-option label="库存补充" value="库存补充"></el-option>
        </el-select>
        <span class="top-bar">操作时间</span>
        <el-date-picker
          size="small"
          style="width: 220px"
          v-model="searchTime"
          type="daterange"
          range-separator="至"
          start-placeholder="起始时间"
          end-placeholder="结束时间"
        >
        </el-date-picker>
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
      <el-table
        row-key="id"
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column v-if="!isCompactViewport" prop="id" label="ID" width="80"></el-table-column>
        <el-table-column
          prop="userName"
          label="操作者"
          :width="isCompactViewport ? 72 : 120"
        ></el-table-column>
        <el-table-column prop="operation" label="操作类型" :width="isCompactViewport ? 74 : 100">
          <template #default="scope">
            <el-tag
              v-if="scope.row.operation === '新增'"
              type="success"
              size="small"
              >新增</el-tag
            >
            <el-tag
              v-else-if="scope.row.operation === '修改'"
              type="warning"
              size="small"
              >修改</el-tag
            >
            <el-tag
              v-else-if="scope.row.operation === '删除'"
              type="danger"
              size="small"
              >删除</el-tag
            >
            <el-tag v-else type="info" size="small">{{
              scope.row.operation
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="target"
          label="操作目标"
          :width="isCompactViewport ? 80 : 140"
        ></el-table-column>
        <el-table-column
          prop="detail"
          label="操作详情"
          :min-width="isCompactViewport ? 90 : 280"
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column v-if="!isCompactViewport" prop="ip" label="IP地址" width="140"></el-table-column>
        <el-table-column
          v-if="!isCompactViewport"
          prop="createTime"
          label="操作时间"
          width="168"
        ></el-table-column>
      </el-table>
      <el-pagination
        style="margin: 20px 0; float: right"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[10, 20]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
  </el-row>
</template>

<script>
import { toDayRange } from "@/utils/pageQuery.js";
import compactViewport from "@/mixins/compactViewport.js";

export default {
  mixins: [compactViewport],
  data() {
    return {
      tableData: [],
      loading: false,
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      searchTime: [],
      queryDto: {},
    };
  },
  created() {
    this.fetchData();
  },
  methods: {
    async fetchData() {
      this.loading = true;
      try {
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          ...toDayRange(this.searchTime),
          ...this.queryDto,
        };
        const response = await this.$axios.post("/operationLog/query", params);
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        }
      } catch (error) {
        console.error("查询操作日志异常:", error);
      } finally {
        this.loading = false;
      }
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchData();
    },
    resetCondition() {
      this.queryDto = {};
      this.searchTime = [];
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
