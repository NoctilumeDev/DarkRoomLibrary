<template>
  <el-row class="admin-table-page">
    <el-row style="padding: 10px; margin: 0 10px">
      <el-row>
        <span class="top-bar">用户名</span>
        <el-input
          size="small"
          style="width: 188px; margin-right: 10px"
          v-model="userQueryDto.userName"
          placeholder="用户名"
          clearable
          @clear="handleFilterClear"
        >
        </el-input>
        <span class="top-bar">注册时间</span>
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
          class="customer admin-add-button"
          type="info"
          @click="add()"
          >新增用户</el-button
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
          @click="resetQueryCondition"
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
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="userAvatar" width="68" label="头像">
          <template #default="scope">
            <el-avatar
              :size="30"
              :src="getFileUrl(scope.row.userAvatar)"
              style="margin-top: 10px"
            ></el-avatar>
          </template>
        </el-table-column>
        <el-table-column
          prop="userName"
          width="148"
          label="名称"
        ></el-table-column>
        <el-table-column
          prop="userAccount"
          width="128"
          label="账号"
        ></el-table-column>
        <el-table-column
          prop="userEmail"
          width="168"
          label="用户邮箱"
        ></el-table-column>
        <el-table-column prop="userRole" width="88" label="角色">
          <template #default="scope">
            <span>{{ getRoleName(scope.row.userRole) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="isCoordinatorAdmin" width="96" label="馆务协调">
          <template #default="scope">
            <span v-if="scope.row.userRole === 1 && scope.row.isCoordinatorAdmin">协调员</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="isLogin" width="108" label="冻结">
          <template #default="scope">
            <el-switch
              @change="
                handleSwitchChange(scope.row.id, scope.row.isLogin, true, scope)
              "
              style="user-select: none"
              v-model="scope.row.isLogin"
              active-color="#f56c6c"
              inactive-color="rgb(226, 226, 226)"
            >
            </el-switch>
          </template>
        </el-table-column>
        <el-table-column
          :sortable="true"
          prop="createTime"
          width="168"
          label="注册时间"
        ></el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <span class="text-button" @click="handleEdit(scope.row)">编辑</span>
            <span class="text-button" @click="handleDelete(scope.row)"
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
        :page-sizes="[5, 7]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
      ></el-pagination>
    </el-row>
    <!-- 操作面板 -->
    <el-dialog
      :show-close="false"
      v-model="dialogUserOperaion"
      class="admin-editor-dialog admin-editor-dialog--user"
      width="min(680px, 92vw)"
      top="5vh"
      append-to-body
    >
      <template #header>
        <p class="dialog-title">
          {{ !isOperation ? "新增新用户" : "编辑用户信息" }}
        </p>
      </template>
      <div class="admin-form-scroll user-form-scroll">
        <el-row class="user-avatar-row">
          <el-upload
            class="avatar-uploader"
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img
              v-if="data.userAvatar"
              :src="getFileUrl(data.userAvatar)"
              class="dialog-avatar"
            />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <el-row class="user-form-fields">
          <span class="dialog-hover">用户名</span>
          <input
            class="dialog-input"
            v-model="data.userName"
            placeholder="用户名"
          />
          <span class="dialog-hover">账号</span>
          <input
            class="dialog-input"
            v-model="data.userAccount"
            placeholder="账号"
            maxlength="32"
          />
          <span class="dialog-hover">邮箱</span>
          <input
            class="dialog-input"
            v-model="data.userEmail"
            placeholder="邮箱"
          />
          <template v-if="!isOperation || isSuperAdmin">
            <span class="dialog-hover">{{ isOperation ? "重置密码" : "密码" }}</span>
            <input
              class="dialog-input"
              v-model="userPwd"
              type="password"
              :placeholder="isOperation ? '留空则不修改，需满足密码强度' : '请输入密码'"
              autocomplete="new-password"
            />
          </template>
          <span class="dialog-hover">角色</span>
          <el-select
            v-model="data.userRole"
            placeholder="选择角色"
            style="width: 100%"
            @change="handleRoleChange"
          >
            <el-option label="超级管理员" :value="0" />
            <el-option label="管理员" :value="1" />
            <el-option label="读者" :value="2" />
            <el-option label="采购员" :value="3" />
            <el-option label="物流员" :value="4" />
          </el-select>
          <template v-if="data.userRole === 1">
            <span class="dialog-hover">馆务协调权限</span>
            <el-switch
              class="coordinator-admin-switch"
              v-model="data.isCoordinatorAdmin"
              :disabled="!isSuperAdmin"
              active-text="协调员"
              inactive-text="普通"
            />
          </template>
        </el-row>
      </div>
      <template #footer class="dialog-footer">
        <el-button
          size="small"
          v-if="!isOperation"
          class="admin-dialog-submit"
          @click="addOperation"
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
          @click="dialogUserOperaion = false"
          >取消</el-button
        >
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import { buildApiUrl, resolveFileUrl } from "@/utils/fileUrl.js";
import { getToken, getUserProfile } from "@/utils/storage.js";
import { Plus } from "@element-plus/icons-vue";

export default {
  components: { Plus },
  data() {
    return {
      userPwd: "",
      data: { userAvatar: "" },
      currentPage: 1,
      pageSize: 7,
      totalItems: 0,
      dialogUserOperaion: false, // 开启/关闭弹窗
      isOperation: false, // 开启标识新增或修改
      tableData: [],
      searchTime: [],
      selectedRows: [],
      userQueryDto: {}, // 搜索条件
    };
  },
  watch: {
    dialogUserOperaion(isOpen, wasOpen) {
      if (!isOpen && wasOpen) {
        this.data = {};
      }
    },
  },
  created() {
    this.fetchFreshData();
  },
  computed: {
    uploadUrl() {
      return buildApiUrl("/file/upload");
    },
    uploadHeaders() {
      const token = getToken();
      return token ? { token } : {};
    },
    isSuperAdmin() {
      return Number(getUserProfile()?.role) === 0;
    },
  },
  methods: {
    getFileUrl(url) {
      return resolveFileUrl(url);
    },
    // 角色名称映射
    getRoleName(role) {
      const roleMap = {
        0: "超级管理员",
        1: "管理员",
        2: "读者",
        3: "采购员",
        4: "物流员",
      };
      return roleMap[role] || "未知";
    },
    handleRoleChange(role) {
      if (role !== 1) {
        this.data.isCoordinatorAdmin = false;
      }
    },
    handleAvatarSuccess(res) {
      if (res.code !== 200) {
        this.$message.error(`用户头像上传异常`);
        return;
      }
      this.$message.success(`用户头像上传成功`);
      this.data.userAvatar = res.data;
    },
    async handleSwitchChange(id, status, operation, scope) {
      try {
        let param = { id: id };
        if (operation) {
          param.isLogin = status;
        } else {
          param.isWord = status;
        }
        const response = await this.$axios.put(`/user/backUpdate`, param);
        if (response.data.code === 200) {
          this.$swal.fire({
            title: operation ? "冻结状态" : "评论状态",
            text: operation ? "冻结状态操作成功" : "评论状态操作成功",
            icon: "success",
            showConfirmButton: false,
            timer: 1000,
          });
        } else {
          this.$message.error(response.data.msg);
          setTimeout(() => { scope.row.isLogin = !status; }, 250);
        }
      } catch (e) {
        console.error(`更新用户状态异常：${e}`);
        const msg = e.response?.data?.msg || "状态更新失败";
        this.$message.error(msg);
      }
    },
    // 多选框选中
    handleSelectionChange(selection) {
      this.selectedRows = selection;
    },
    // 批量删除数据
    async batchDelete() {
      if (!this.selectedRows.length) {
        this.$message(`未选中任何数据`);
        return;
      }
      const confirmed = await this.$swalConfirm({
        title: "删除用户数据",
        text: `删除后不可恢复，是否继续？`,
        icon: "warning",
      });
      if (confirmed) {
        try {
          let ids = this.selectedRows.map((entity) => entity.id);
          const response = await this.$axios.post(`/user/batchDelete`, ids);
          if (response.data.code === 200) {
            this.$swal.fire({
              title: "删除提示",
              text: response.data.msg,
              icon: "success",
              showConfirmButton: false,
              timer: 2000,
            });
            await this.fetchFreshData();
            return;
          }
        } catch (e) {
          this.$swal.fire({
            title: "错误提示",
            text: e,
            icon: "error",
            showConfirmButton: false,
            timer: 2000,
          });
          console.error(`用户信息删除异常：`, e);
        }
      }
    },
    resetQueryCondition() {
      this.userQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    // 修改信息
    async updateOperation() {
      if (this.userPwd !== "") {
        this.data.userPwd = this.userPwd;
      } else {
        this.data.userPwd = null;
      }
      try {
        const response = await this.$axios.put("/user/backUpdate", this.data);
        this.$swal.fire({
          title: "用户信息修改",
          text: response.data.msg,
          icon: response.data.code === 200 ? "success" : "error",
          showConfirmButton: false,
          timer: 1000,
        });
        if (response.data.code === 200) {
          this.closeDialog();
          await this.fetchFreshData();
          this.clearFormData();
        }
      } catch (error) {
        console.error("提交表单时出错", error);
        const msg = error.response?.data?.msg || "提交失败，请稍后再试"; this.$message.error(msg);
      }
    },
    // 信息新增
    async addOperation() {
      if (this.userPwd !== "") {
        this.data.userPwd = this.userPwd;
      } else {
        this.data.userPwd = null;
      }
      try {
        const response = await this.$axios.post("/user/insert", this.data);
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg
        );
        if (response.data.code === 200) {
          this.closeDialog();
          await this.fetchFreshData();
          this.clearFormData();
        }
      } catch (error) {
        console.error("提交表单时出错", error);
        const msg = error.response?.data?.msg || "提交失败，请稍后再试"; this.$message.error(msg);
      }
    },
    closeDialog() {
      this.dialogUserOperaion = false;
    },
    clearFormData() {
      this.data = {};
    },
    async fetchFreshData() {
      try {
        this.tableData = [];
        let startTime = null;
        let endTime = null;
        if (this.searchTime != null && this.searchTime.length === 2) {
          const [startDate, endDate] = await Promise.all(
            this.searchTime.map((date) => date.toISOString())
          );
          startTime = `${startDate.split("T")[0]}T00:00:00`;
          endTime = `${endDate.split("T")[0]}T23:59:59`;
        }
        // 请求参数
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          startTime: startTime,
          endTime: endTime,
          ...this.userQueryDto,
        };
        const response = await this.$axios.post("/user/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error("查询用户信息异常:", error);
      }
    },
    add() {
      this.data = {};
      this.isOperation = false;
      this.userPwd = "";
      this.dialogUserOperaion = true;
    },
    handleFilter() {
      this.currentPage = 1;
      this.fetchFreshData();
    },
    handleFilterClear() {
      this.handleFilter();
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchFreshData();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchFreshData();
    },
    handleEdit(row) {
      this.dialogUserOperaion = true;
      this.isOperation = true;
      row.userPwd = null;
      this.data = { ...row };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
  },
};
</script>
<style scoped lang="scss">
.tag-tip {
  display: inline-block;
  padding: 5px 10px;
  border-radius: 5px;
  background-color: var(--admin-surface-muted);
  color: var(--admin-text-secondary);
}

.input-def {
  height: 40px;
  line-height: 40px;
  outline: none;
  border: none;
  font-size: 20px;
  color: var(--admin-text);
  font-weight: 900;
  width: 100%;
}

.dialog-footer {
  /* 使按钮水平居中*/
  display: flex;
  justify-content: center;
  align-items: center;
}

/* 如果需要调整按钮之间的间距 */
.customer {
  margin: 0 8px;
  /* 根据需要调整间距*/
}

.user-avatar-row {
  justify-content: center;
  padding: 4px 0 18px;
}

.user-form-fields {
  display: block;
}

:deep(.coordinator-admin-switch .el-switch__label) {
  color: var(--admin-muted) !important;
}

:deep(.coordinator-admin-switch .el-switch__label--left.is-active) {
  color: var(--admin-jade) !important;
}

:deep(.coordinator-admin-switch .el-switch__label--right.is-active) {
  color: var(--admin-accent) !important;
}

:deep(.coordinator-admin-switch .el-switch__core) {
  border-color: var(--admin-border-strong) !important;
  background-color: var(--admin-surface-muted) !important;
}

:deep(.coordinator-admin-switch.is-checked .el-switch__core) {
  border-color: var(--admin-accent-solid) !important;
  background-color: var(--admin-accent-solid) !important;
}
</style>
