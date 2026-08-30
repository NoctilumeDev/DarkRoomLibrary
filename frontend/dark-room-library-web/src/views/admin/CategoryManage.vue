<template>
  <el-row class="admin-table-page">
    <el-row style="padding: 10px; margin: 0 10px">
      <el-row>
        <span class="top-bar">分类名称</span>
        <el-input
          size="small"
          style="width: 188px; margin-right: 10px"
          v-model="queryDto.name"
          placeholder="分类名称"
          clearable
          @clear="handleFilter"
        >
        </el-input>
        <el-button
          size="small"
          class="customer"
          type="primary"
          @click="handleFilter"
          >立即查询</el-button
        >
        <el-button
          size="small"
          class="customer admin-add-button"
          type="info"
          @click="add()"
          >新增分类</el-button
        >
        <el-button
          size="small"
          class="customer"
          type="danger"
          :disabled="!selectedRows.length"
          @click="batchDelete()"
          >批量删除</el-button
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
        @selection-change="handleSelectionChange"
        :data="tableData"
        style="width: 100%"
      >
        <el-table-column type="selection" :width="isCompactViewport ? 42 : 55"></el-table-column>
        <el-table-column v-if="!isCompactViewport" prop="id" label="ID" width="80"></el-table-column>
        <el-table-column
          prop="name"
          label="分类名称"
          :width="isCompactViewport ? 116 : 200"
        ></el-table-column>
        <el-table-column
          v-if="!isCompactViewport"
          prop="createTime"
          label="创建时间"
          width="180"
        ></el-table-column>
        <el-table-column label="操作" :width="isCompactViewport ? 110 : 140" fixed="right">
          <template #default="scope">
            <span class="text-button" @click="handleEdit(scope.row)">编辑</span>
            <span
              class="text-button"
              style="margin-left: 10px"
              @click="handleDelete(scope.row)"
              >删除</span
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
    <!-- 弹窗 -->
    <el-dialog
      :show-close="false"
      v-model="dialogVisible"
      class="admin-editor-dialog admin-editor-dialog--compact"
      width="min(520px, 92vw)"
      top="14vh"
      append-to-body
    >
      <template #header>
        <p class="dialog-title">{{ !isEdit ? "新增分类" : "编辑分类" }}</p>
      </template>
      <div class="admin-form-scroll">
        <span class="dialog-hover">分类名称</span>
        <input
          class="dialog-input"
          v-model="form.name"
          placeholder="分类名称"
        />
      </div>
      <template #footer class="dialog-footer">
        <el-button
          size="small"
          v-if="!isEdit"
          class="admin-dialog-submit"
          @click="saveOperation"
          >新增</el-button
        >
        <el-button
          size="small"
          v-else
          class="admin-dialog-submit"
          @click="updateOperation"
          >修改</el-button
        >
        <el-button
          class="admin-dialog-cancel"
          size="small"
          @click="dialogVisible = false"
          >取消</el-button
        >
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import compactViewport from "@/mixins/compactViewport.js";

export default {
  mixins: [compactViewport],
  data() {
    return {
      form: { name: "" },
      currentPage: 1,
      pageSize: 10,
      totalItems: 0,
      dialogVisible: false,
      isEdit: false,
      tableData: [],
      selectedRows: [],
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
        const response = await this.$axios.post("/category/query", params);
        const { data } = response;
        if (data.code === 200) {
          this.tableData = data.data || [];
          this.totalItems = data.total || 0;
        }
      } catch (error) {
        console.error("查询分类异常:", error);
      }
    },
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    async batchDelete() {
      if (!this.selectedRows.length) {
        this.$message("未选中任何数据");
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "删除分类",
        text: "删除后不可恢复，是否继续?",
        icon: "warning",
      });
      if (confirmed) {
        try {
          let ids = this.selectedRows.map((e) => e.id);
          const response = await this.$axios.post("/category/batchDelete", ids);
          if (response.data.code === 200) {
            this.$swal.fire({
              title: "删除提示",
              text: response.data.msg,
              icon: "success",
              showConfirmButton: false,
              timer: 2000,
            });
            this.fetchData();
          }
        } catch {
          this.$message.error("删除失败");
        }
      }
    },
    async saveOperation() {
      try {
        const response = await this.$axios.post("/category/save", this.form);
        if (response.data.code === 200) {
          this.dialogVisible = false;
          this.$message.success(response.data.msg);
          this.fetchData();
          this.form = { name: "" };
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        const msg = error.response?.data?.msg || "新增失败"; this.$message.error(msg);
      }
    },
    async updateOperation() {
      try {
        const response = await this.$axios.put("/category/update", this.form);
        if (response.data.code === 200) {
          this.dialogVisible = false;
          this.$message.success(response.data.msg);
          this.fetchData();
          this.form = { name: "" };
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        const msg = error.response?.data?.msg || "修改失败"; this.$message.error(msg);
      }
    },
    add() {
      this.isEdit = false;
      this.form = { name: "" };
      this.dialogVisible = true;
    },
    handleEdit(row) {
      this.isEdit = true;
      this.form = { ...row };
      this.dialogVisible = true;
    },
    handleDelete(row) {
      this.selectedRows = [row];
      this.batchDelete();
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
.text-button {
  color: var(--admin-blue);
  cursor: pointer;
  font-size: 13px;
}
.text-button:hover {
  color: var(--admin-accent);
}
.top-bar {
  font-size: 14px;
  margin-right: 5px;
  color: var(--admin-text-secondary);
}
.dialog-title {
  font-size: 18px;
  font-weight: 800;
}
.dialog-hover {
  font-size: 12px;
  padding: 3px 0;
  display: block;
  color: var(--admin-muted);
}
.dialog-input {
  width: 100%;
  padding: 8px 10px;
  color: var(--admin-text);
  border: 1px solid var(--admin-border);
  background: var(--admin-surface-strong);
  border-radius: 4px;
  font-size: 14px;
  margin-bottom: 12px;
}
</style>
