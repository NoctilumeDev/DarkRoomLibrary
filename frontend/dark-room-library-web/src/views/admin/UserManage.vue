<template>
  <el-row class="admin-table-page user-manage-page">
    <el-row class="user-toolbar">
      <el-row class="user-toolbar__controls">
        <span class="top-bar">用户名</span>
        <el-input
          v-model="filters.userName"
          class="user-name-filter"
          size="small"
          placeholder="用户名"
          clearable
          @clear="applyFilters"
          @keyup.enter="applyFilters"
        />

        <span class="top-bar">注册时间</span>
        <el-date-picker
          v-model="registeredRange"
          class="user-date-filter"
          size="small"
          type="daterange"
          range-separator="至"
          start-placeholder="起始时间"
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
          class="customer admin-add-button"
          size="small"
          type="info"
          @click="openCreateDialog"
        >
          新增用户
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
        <el-button
          class="customer reset"
          size="small"
          type="info"
          @click="resetFilters"
        >
          条件重置
        </el-button>
      </el-row>
    </el-row>

    <el-row class="user-table-section">
      <el-table
        v-loading="loading"
        :data="users"
        row-key="id"
        class="user-table"
        @selection-change="selectedRows = $event"
      >
        <el-table-column
          type="selection"
          :width="isCompactViewport ? 42 : 55"
          :selectable="canDeleteUser"
        />
        <el-table-column v-if="!isCompactViewport" prop="userAvatar" width="68" label="头像">
          <template #default="{ row }">
            <el-avatar
              :size="30"
              :src="getFileUrl(row.userAvatar)"
              class="user-table-avatar"
            />
          </template>
        </el-table-column>
        <el-table-column prop="userName" :width="isCompactViewport ? 82 : 148" label="名称" />
        <el-table-column v-if="!isCompactViewport" prop="userAccount" width="128" label="账号" />
        <el-table-column v-if="!isCompactViewport" prop="userEmail" width="168" label="用户邮箱" />
        <el-table-column prop="userRole" :width="isCompactViewport ? 68 : 88" label="角色">
          <template #default="{ row }">
            <span>{{ roleName(row.userRole) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactViewport" prop="isCoordinatorAdmin" width="96" label="馆务协调">
          <template #default="{ row }">
            <span
              v-if="
                row.userRole === userRoles.ADMIN && row.isCoordinatorAdmin
              "
            >
              协调员
            </span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompactViewport" prop="isLogin" width="108" label="冻结">
          <template #default="{ row }">
            <el-switch
              v-model="row.isLogin"
              class="user-freeze-switch"
              :disabled="!canChangeLoginStatus(row)"
              @change="updateLoginStatus(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="!isCompactViewport"
          prop="createTime"
          width="168"
          label="注册时间"
          sortable
        />
        <el-table-column label="操作" :width="isCompactViewport ? 96 : 140" fixed="right">
          <template #default="{ row }">
            <span
              v-if="canEditUser(row)"
              class="text-button"
              @click="openEditDialog(row)"
            >
              编辑
            </span>
            <span
              v-if="canDeleteUser(row)"
              class="text-button"
              @click="deleteOne(row)"
            >
              删除
            </span>
            <span
              v-if="!canEditUser(row) && !canDeleteUser(row)"
              class="muted"
            >
              -
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="user-pagination"
        :page-sizes="[5, 7]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalItems"
        @size-change="handlePageSizeChange"
        @current-change="loadUsers"
      />
    </el-row>

    <el-dialog
      v-model="dialogVisible"
      :show-close="false"
      class="admin-editor-dialog admin-editor-dialog--user"
      width="min(680px, 92vw)"
      top="5vh"
      append-to-body
      @closed="resetEditor"
    >
      <template #header>
        <p class="dialog-title">
          {{ isEditing ? "编辑用户信息" : "新增新用户" }}
        </p>
      </template>

      <div class="admin-form-scroll user-form-scroll">
        <el-row class="user-avatar-row">
          <el-upload
            class="avatar-uploader"
            :action="uploadUrl"
            :headers="uploadHeaders"
            :disabled="demoMode"
            :show-file-list="false"
            :on-success="handleAvatarSuccess"
          >
            <img
              v-if="form.userAvatar"
              :src="getFileUrl(form.userAvatar)"
              class="dialog-avatar"
            />
            <el-icon v-else class="avatar-uploader-icon">
              <Plus />
            </el-icon>
          </el-upload>
        </el-row>

        <div class="user-form-fields">
          <span class="dialog-hover">用户名</span>
          <input
            v-model="form.userName"
            class="dialog-input"
            placeholder="用户名"
          />

          <span class="dialog-hover">账号</span>
          <input
            v-model="form.userAccount"
            class="dialog-input"
            placeholder="账号"
            maxlength="32"
          />

          <span class="dialog-hover">邮箱</span>
          <input
            v-model="form.userEmail"
            class="dialog-input"
            placeholder="邮箱"
          />

          <template v-if="!isEditing || isSuperAdmin">
            <span class="dialog-hover">
              {{ isEditing ? "重置密码" : "密码" }}
            </span>
            <input
              v-model="password"
              class="dialog-input"
              type="password"
              :placeholder="
                isEditing ? '留空则不修改，需满足密码强度' : '请输入密码'
              "
              autocomplete="new-password"
            />
          </template>

          <span class="dialog-hover">角色</span>
          <el-select
            v-model="form.userRole"
            placeholder="选择角色"
            class="user-role-select"
            :disabled="!isSuperAdmin"
            @change="handleRoleChange"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.value"
              :label="role.label"
              :value="role.value"
            />
          </el-select>

          <template v-if="form.userRole === userRoles.ADMIN">
            <span class="dialog-hover">馆务协调权限</span>
            <el-switch
              v-model="form.isCoordinatorAdmin"
              class="coordinator-admin-switch"
              :disabled="!isSuperAdmin"
              active-text="协调员"
              inactive-text="普通"
            />
          </template>
        </div>
      </div>

      <template #footer>
        <el-button
          class="admin-dialog-submit"
          size="small"
          :loading="submitting"
          @click="submitUser"
        >
          {{ isEditing ? "修改" : "新增" }}
        </el-button>
        <el-button
          class="admin-dialog-cancel"
          size="small"
          @click="dialogVisible = false"
        >
          取消
        </el-button>
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import { DEMO_MODE } from "@/demo/runtime.js";
import compactViewport from "@/mixins/compactViewport.js";
import { Plus } from "@element-plus/icons-vue";
import { buildApiUrl, resolveFileUrl } from "@/utils/fileUrl.js";
import { toDayRange } from "@/utils/pageQuery.js";
import { getSessionUserRole, getToken, getUserProfile } from "@/utils/storage.js";
import {
  getUserRoleName,
  USER_ROLE,
  USER_ROLE_OPTIONS,
} from "@/utils/userRoles.js";

function createEmptyUser() {
  return {
    id: null,
    userAvatar: "",
    userName: "",
    userAccount: "",
    userEmail: "",
    userRole: USER_ROLE.READER,
    isCoordinatorAdmin: false,
  };
}

export default {
  name: "UserManage",
  mixins: [compactViewport],
  components: { Plus },
  data() {
    return {
      currentPage: 1,
      pageSize: 7,
      totalItems: 0,
      users: [],
      selectedRows: [],
      registeredRange: [],
      filters: {
        userName: "",
      },
      dialogVisible: false,
      isEditing: false,
      form: createEmptyUser(),
      password: "",
      loading: false,
      submitting: false,
      userRoles: USER_ROLE,
      roleOptions: USER_ROLE_OPTIONS,
    };
  },
  computed: {
    demoMode() {
      return DEMO_MODE;
    },
    uploadUrl() {
      return buildApiUrl("/file/upload");
    },
    uploadHeaders() {
      const token = getToken();
      return token
        ? {
            Authorization: `Bearer ${token}`,
            token,
          }
        : {};
    },
    isSuperAdmin() {
      return getSessionUserRole() === USER_ROLE.SUPER_ADMIN;
    },
    currentUserId() {
      return Number(getUserProfile()?.id) || null;
    },
  },
  created() {
    this.loadUsers();
  },
  methods: {
    getFileUrl(path) {
      return resolveFileUrl(path);
    },
    roleName(role) {
      return getUserRoleName(role) || "未知";
    },
    buildQuery() {
      return {
        current: this.currentPage,
        size: this.pageSize,
        ...toDayRange(this.registeredRange),
        userName: this.filters.userName?.trim() || null,
      };
    },
    async loadUsers() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/user/query", this.buildQuery());
        const result = response.data;
        if (result.code !== 200) {
          this.$message.error(result.msg || "用户查询失败");
          return;
        }
        this.users = result.data || [];
        this.totalItems = result.total || 0;
      } catch (error) {
        console.error("查询用户信息异常:", error);
        this.$message.error(error.response?.data?.msg || "用户查询失败");
      } finally {
        this.loading = false;
      }
    },
    applyFilters() {
      this.currentPage = 1;
      this.loadUsers();
    },
    resetFilters() {
      this.filters = { userName: "" };
      this.registeredRange = [];
      this.currentPage = 1;
      this.loadUsers();
    },
    handlePageSizeChange() {
      this.currentPage = 1;
      this.loadUsers();
    },
    openCreateDialog() {
      this.resetEditor();
      this.dialogVisible = true;
    },
    openEditDialog(user) {
      this.form = {
        id: user.id,
        userAvatar: user.userAvatar || "",
        userName: user.userName || "",
        userAccount: user.userAccount || "",
        userEmail: user.userEmail || "",
        userRole: user.userRole,
        isCoordinatorAdmin: Boolean(user.isCoordinatorAdmin),
      };
      this.password = "";
      this.isEditing = true;
      this.dialogVisible = true;
    },
    resetEditor() {
      this.form = createEmptyUser();
      this.password = "";
      this.isEditing = false;
      this.submitting = false;
    },
    handleRoleChange(role) {
      if (role !== USER_ROLE.ADMIN) {
        this.form.isCoordinatorAdmin = false;
      }
    },
    handleAvatarSuccess(result) {
      if (result.code !== 200) {
        this.$message.error(result.msg || "用户头像上传异常");
        return;
      }
      this.form.userAvatar = result.data;
      this.$message.success("用户头像上传成功");
    },
    async updateLoginStatus(user, status) {
      try {
        const response = await this.$axios.put("/user/backUpdate", {
          id: user.id,
          isLogin: status,
        });
        const result = response.data;
        if (result.code !== 200) {
          user.isLogin = !status;
          this.$message.error(result.msg || "冻结状态更新失败");
          return;
        }
        this.$message.success("冻结状态操作成功");
      } catch (error) {
        user.isLogin = !status;
        console.error("更新用户冻结状态异常:", error);
        this.$message.error(error.response?.data?.msg || "冻结状态更新失败");
      }
    },
    submitUser() {
      return this.isEditing ? this.updateUser() : this.createUser();
    },
    buildCreatePayload() {
      return {
        userAvatar: this.form.userAvatar || null,
        userName: this.form.userName,
        userAccount: this.form.userAccount,
        userEmail: this.form.userEmail,
        userPwd: this.password,
        userRole: this.isSuperAdmin ? this.form.userRole : USER_ROLE.READER,
        isCoordinatorAdmin: this.isSuperAdmin
          ? this.form.isCoordinatorAdmin
          : false,
      };
    },
    buildUpdatePayload() {
      const payload = {
        id: this.form.id,
        userAvatar: this.form.userAvatar || null,
        userName: this.form.userName,
        userAccount: this.form.userAccount,
        userEmail: this.form.userEmail,
      };
      if (this.isSuperAdmin) {
        payload.userRole = this.form.userRole;
        payload.isCoordinatorAdmin = this.form.isCoordinatorAdmin;
        payload.userPwd = this.password || null;
      }
      return payload;
    },
    async createUser() {
      await this.saveUser({
        request: () =>
          this.$axios.post("/user/insert", this.buildCreatePayload()),
        fallbackMessage: "新增用户失败",
      });
    },
    async updateUser() {
      await this.saveUser({
        request: () =>
          this.$axios.put("/user/backUpdate", this.buildUpdatePayload()),
        fallbackMessage: "修改用户失败",
      });
    },
    async saveUser({ request, fallbackMessage }) {
      this.submitting = true;
      try {
        const response = await request();
        const result = response.data;
        if (result.code !== 200) {
          this.$message.error(result.msg || fallbackMessage);
          return;
        }
        this.$message.success(result.msg);
        this.dialogVisible = false;
        await this.loadUsers();
      } catch (error) {
        console.error("提交用户表单异常:", error);
        this.$message.error(error.response?.data?.msg || fallbackMessage);
      } finally {
        this.submitting = false;
      }
    },
    deleteOne(user) {
      this.deleteUsers([user.id]);
    },
    deleteSelected() {
      this.deleteUsers(this.selectedRows.map(({ id }) => id));
    },
    async deleteUsers(ids) {
      if (ids.length === 0) {
        this.$message.warning("未选中任何数据");
        return;
      }

      const confirmed = await this.$swalConfirm({
        title: ids.length === 1 ? "删除用户" : "批量删除用户",
        text: "删除后不可恢复，是否继续？",
        icon: "warning",
      });
      if (!confirmed) return;

      try {
        const response = await this.$axios.post("/user/batchDelete", ids);
        const result = response.data;
        if (result.code !== 200) {
          this.$message.error(result.msg || "删除用户失败");
          return;
        }
        this.$message.success(result.msg || "删除用户成功");
        this.selectedRows = [];
        await this.loadUsers();
        if (
          this.users.length === 0 &&
          this.currentPage > 1 &&
          this.totalItems > 0
        ) {
          this.currentPage -= 1;
          await this.loadUsers();
        }
      } catch (error) {
        console.error("删除用户信息异常:", error);
        this.$message.error(error.response?.data?.msg || "删除用户失败");
      }
    },
    canEditUser(user) {
      return (
        this.isSuperAdmin ||
        user.id === this.currentUserId ||
        user.userRole === USER_ROLE.READER
      );
    },
    canDeleteUser(user) {
      if (user.id === this.currentUserId) return false;
      return this.isSuperAdmin || user.userRole === USER_ROLE.READER;
    },
    canChangeLoginStatus(user) {
      if (user.id === this.currentUserId) return false;
      if (user.userRole === USER_ROLE.SUPER_ADMIN) return false;
      return this.isSuperAdmin || user.userRole === USER_ROLE.READER;
    },
  },
};
</script>

<style scoped lang="scss">
.user-toolbar {
  padding: 10px;
  margin: 0 10px;
}

.user-toolbar__controls {
  align-items: center;
}

.user-name-filter {
  width: 188px;
  margin-right: 10px;
}

.user-toolbar__controls :deep(.user-date-filter) {
  width: 220px;
}

.user-table-section {
  margin: 10px 20px;
}

.user-table {
  width: 100%;
}

.user-table-avatar {
  margin-top: 10px;
}

.user-freeze-switch {
  user-select: none;
}

:deep(.user-freeze-switch .el-switch__core) {
  border-color: var(--admin-border-strong) !important;
  background-color: var(--admin-surface-muted) !important;
}

:deep(.user-freeze-switch.is-checked .el-switch__core) {
  border-color: var(--admin-danger-solid) !important;
  background-color: var(--admin-danger-solid) !important;
}

.user-pagination {
  margin: 20px 0;
  margin-left: auto;
}

.user-avatar-row {
  justify-content: center;
  padding: 4px 0 18px;
}

.user-role-select {
  width: 100%;
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

@media (max-width: 760px) {
  .user-toolbar {
    width: 100%;
    padding: 0;
    margin: 0;
  }

  .user-toolbar__controls {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    width: 100%;
  }

  .user-toolbar__controls > .top-bar {
    display: none;
  }

  .user-name-filter,
  .user-toolbar__controls :deep(.user-date-filter) {
    grid-column: 1 / -1;
    width: 100%;
    margin-right: 0;
  }

  .user-toolbar__controls :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
