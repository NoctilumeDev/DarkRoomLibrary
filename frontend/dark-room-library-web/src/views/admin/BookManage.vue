<template>
  <el-row class="admin-table-page">
    <el-row class="book-toolbar">
      <div class="toolbar-field name-field">
        <span class="top-bar">图书名称</span>
        <el-input
          size="small"
          v-model="bookQueryDto.name"
          placeholder="图书名称"
          clearable
          @clear="handleFilterClear"
        >
        </el-input>
      </div>
      <div class="toolbar-field author-field">
        <span class="top-bar">作者</span>
        <el-input
          size="small"
          v-model="bookQueryDto.author"
          placeholder="作者"
          clearable
          @clear="handleFilterClear"
        >
        </el-input>
      </div>
      <div class="toolbar-field category-field">
        <span class="top-bar">分类</span>
        <el-select
          size="small"
          v-model="bookQueryDto.category"
          placeholder="分类"
          clearable
          @clear="handleFilterClear"
        >
          <el-option
            v-for="cat in categories"
            :key="cat.id"
            :label="cat.name"
            :value="cat.name"
          ></el-option>
        </el-select>
      </div>
      <div class="toolbar-field date-field">
        <span class="top-bar">入库时间</span>
        <el-date-picker
          size="small"
          v-model="searchTime"
          type="daterange"
          range-separator="至"
          start-placeholder="起始时间"
          end-placeholder="结束时间"
        >
        </el-date-picker>
      </div>
      <div class="toolbar-action">
        <el-button
          size="small"
          class="customer"
          type="primary"
          @click="handleFilter"
          >立即查询</el-button
        >
      </div>
      <div class="toolbar-action add-action">
        <el-button
          v-show="!showDeleted"
          size="small"
          class="customer admin-add-button"
          type="info"
          @click="add()"
          >新增图书</el-button
        >
      </div>
      <div class="toolbar-action view-action">
        <el-radio-group
          v-model="showDeleted"
          size="small"
          class="collection-view-toggle"
          @change="switchDeletedView"
        >
          <el-radio-button :value="false">正常馆藏</el-radio-button>
          <el-radio-button :value="true">已删除</el-radio-button>
        </el-radio-group>
      </div>
      <div class="toolbar-action">
        <el-button
          size="small"
          class="customer"
          type="danger"
          :disabled="!selectedRows.length"
          @click="showDeleted ? batchRestore() : batchDelete()"
          >{{ showDeleted ? "批量恢复" : "批量删除" }}</el-button
        >
      </div>
      <div class="toolbar-action">
        <el-button
          size="small"
          class="customer reset"
          type="info"
          @click="resetQueryCondition"
          >条件重置</el-button
        >
      </div>
    </el-row>
    <el-row style="margin: 10px 20px">
      <el-table
        row-key="id"
        @selection-change="handleSelectionChange"
        :data="tableData"
        style="width: 100%"
      >
        <el-table-column type="selection" width="55"></el-table-column>
        <el-table-column prop="cover" width="70" label="封面">
          <template #default="scope">
            <el-avatar
              :size="40"
              :src="getCoverUrl(scope.row.cover)"
              style="margin-top: 5px"
            >
              <template v-if="!scope.row.cover">
                <span style="font-size: 12px">无图</span>
              </template>
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column
          prop="name"
          width="150"
          label="图书名称"
        ></el-table-column>
        <el-table-column
          prop="author"
          width="100"
          label="作者"
        ></el-table-column>
        <el-table-column prop="isbn" width="130" label="ISBN"></el-table-column>
        <el-table-column
          prop="publisher"
          width="130"
          label="出版社"
        ></el-table-column>
        <el-table-column
          prop="category"
          width="80"
          label="分类"
        ></el-table-column>
        <el-table-column width="90" label="书架">
          <template #default="scope">
            <span>{{ getBookshelfName(scope.row.bookshelfId) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="totalCount"
          width="60"
          label="总量"
        ></el-table-column>
        <el-table-column prop="availableCount" width="60" label="可借">
          <template #default="scope">
            <span
              class="stock-count"
              :class="{ 'stock-count--empty': scope.row.availableCount <= 0 }"
            >
              {{ scope.row.availableCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          width="160"
          label="简介"
          show-overflow-tooltip
        ></el-table-column>
        <el-table-column
          :sortable="true"
          prop="createTime"
          width="160"
          label="入库时间"
        ></el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="scope">
            <span v-if="!showDeleted" class="text-button" @click="handleEdit(scope.row)">编辑</span>
            <span v-if="!showDeleted" class="text-button" @click="handleDelete(scope.row)"
              >删除</span
            >
            <span v-else class="text-button" @click="restoreRows([scope.row.id])">恢复</span>
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
    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :show-close="false"
      v-model="dialogBookOperation"
      class="admin-editor-dialog admin-editor-dialog--tall book-editor-dialog"
      width="min(720px, 92vw)"
      top="5vh"
      append-to-body
    >
      <template #header>
        <p class="dialog-title">
          {{ !isOperation ? "新增图书" : "编辑图书信息" }}
        </p>
      </template>
      <div class="admin-form-scroll book-form-scroll">
        <el-row>
          <el-upload
            class="avatar-uploader"
            :action="uploadUrl"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
          >
            <img
              v-if="data.cover"
              :src="getCoverUrl(data.cover)"
              class="dialog-avatar"
            />
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
          </el-upload>
        </el-row>
        <el-row>
          <span class="dialog-hover">图书名称</span>
          <input
            class="dialog-input"
            v-model="data.name"
            placeholder="图书名称"
          />
          <span class="dialog-hover">作者</span>
          <input
            class="dialog-input"
            v-model="data.author"
            placeholder="作者"
          />
          <span class="dialog-hover">ISBN</span>
          <input
            class="dialog-input"
            v-model="data.isbn"
            placeholder="ISBN号"
          />
          <span class="dialog-hover">出版社</span>
          <input
            class="dialog-input"
            v-model="data.publisher"
            placeholder="出版社"
          />
          <span class="dialog-hover">分类</span>
          <el-select
            size="small"
            style="width: 100%"
            v-model="data.category"
            placeholder="分类"
          >
            <el-option
              v-for="cat in categories"
              :key="cat.id"
              :label="cat.name"
              :value="cat.name"
            ></el-option>
          </el-select>
          <span class="dialog-hover">书架</span>
          <el-select
            size="small"
            style="width: 100%"
            v-model="data.bookshelfId"
            placeholder="选择或输入书架"
            filterable
            allow-create
            clearable
          >
            <el-option
              v-for="shelf in bookshelves"
              :key="shelf.id"
              :label="shelf.name"
              :value="shelf.id"
            ></el-option>
          </el-select>
          <span class="dialog-hover">总数量</span>
          <el-input-number
            size="small"
            style="width: 100%"
            v-model="data.totalCount"
            :min="0"
            :max="9999"
            placeholder="总数量"
          ></el-input-number>
          <span class="dialog-hover">可借数量</span>
          <el-input-number
            size="small"
            style="width: 100%"
            v-model="data.availableCount"
            :min="0"
            :max="9999"
            placeholder="可借数量"
          ></el-input-number>
          <span class="dialog-hover">简介</span>
          <el-input
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 4 }"
            placeholder="图书简介"
            v-model="data.description"
          >
          </el-input>
        </el-row>
      </div>
      <template #footer class="dialog-footer">
        <el-button
          size="small"
          v-if="!isOperation"
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
          @click="dialogBookOperation = false"
          >取消</el-button
        >
      </template>
    </el-dialog>
  </el-row>
</template>

<script>
import { buildApiUrl, resolveFileUrl } from "@/utils/fileUrl.js";
import { toDayRange } from "@/utils/pageQuery.js";
import { getToken } from "@/utils/storage.js";
import { Plus } from "@element-plus/icons-vue";

export default {
  components: { Plus },
  data() {
    return {
      data: {
        cover: "",
        totalCount: 1,
        availableCount: 1,
      },
      currentPage: 1,
      pageSize: 7,
      totalItems: 0,
      dialogBookOperation: false,
      isOperation: false,
      tableData: [],
      bookshelves: [],
      searchTime: [],
      selectedRows: [],
      showDeleted: false,
      bookQueryDto: {},
      categories: [],
    };
  },
  watch: {
    dialogBookOperation(v1) {
      if (!v1) {
        this.isOperation = false;
        this.data = this.createEmptyBook();
      }
    },
  },
  created() {
    this.fetchFreshData();
    this.fetchCategories();
    this.fetchBookshelves();
  },
  computed: {
    uploadUrl() {
      return buildApiUrl("/file/upload");
    },
    uploadHeaders() {
      const token = getToken();
      return token ? { token } : {};
    },
  },
  methods: {
    getBookshelfName(id) {
      if (!id) return "-";
      const shelf = this.bookshelves.find(s => s.id === id);
      return shelf ? shelf.name : id;
    },
    getCoverUrl(cover) {
      return resolveFileUrl(cover);
    },
    handleCoverSuccess(res) {
      if (res.code !== 200) {
        this.$message.error("图书封面上传异常");
        return;
      }
      this.$message.success("图书封面上传成功");
      this.data.cover = res.data;
    },
    createEmptyBook() {
      return {
        cover: "",
        name: "",
        author: "",
        isbn: "",
        publisher: "",
        category: "",
        totalCount: 1,
        availableCount: 1,
        description: "",
        bookshelfId: null,
      };
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
        title: "删除图书数据",
        text: "图书将进入已删除列表，之后仍可恢复。",
        icon: "warning",
      });
      if (confirmed) {
        try {
          let ids = this.selectedRows.map((entity) => entity.id);
          const response = await this.$axios.post("/book/batchDelete", ids);
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
          console.error("图书信息删除异常:", e);
        }
      }
    },
    async batchRestore() {
      if (!this.selectedRows.length) {
        this.$message("未选中任何数据");
        return;
      }
      await this.restoreRows(this.selectedRows.map((item) => item.id));
    },
    async restoreRows(ids) {
      const confirmed = await this.$swalConfirm({
        title: "恢复图书？",
        text: `将恢复 ${ids.length} 条图书记录。`,
        icon: "question",
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.post("/book/restore", ids);
        this.$message[response.data.code === 200 ? "success" : "error"](
          response.data.msg || (response.data.code === 200 ? "恢复成功" : "恢复失败")
        );
        if (response.data.code === 200) await this.fetchFreshData();
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "恢复失败");
      }
    },
    switchDeletedView() {
      this.currentPage = 1;
      this.selectedRows = [];
      this.fetchFreshData();
    },
    resetQueryCondition() {
      this.bookQueryDto = {};
      this.searchTime = [];
      this.fetchFreshData();
    },
    clearFormData() {
      this.data = this.createEmptyBook();
    },
    validateBookForm() {
      if (!this.data.name || !this.data.name.trim()) {
        this.$message.warning("图书名称不能为空");
        return false;
      }
      if (!this.data.author || !this.data.author.trim()) {
        this.$message.warning("作者不能为空");
        return false;
      }
      if (this.data.totalCount == null || this.data.totalCount < 0) {
        this.$message.warning("总数量不能小于 0");
        return false;
      }
      if (this.data.availableCount == null || this.data.availableCount < 0) {
        this.$message.warning("可借数量不能小于 0");
        return false;
      }
      if (this.data.availableCount > this.data.totalCount) {
        this.$message.warning("可借数量不能大于总数量");
        return false;
      }
      return true;
    },
    async saveOperation() {
      if (!this.validateBookForm()) return;
      try {
        const response = await this.$axios.post("/book/save", this.data);
        if (response.data.code === 200) {
          this.dialogBookOperation = false;
          await this.fetchFreshData();
          this.$message.success(response.data.msg);
          this.clearFormData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        console.error("提交表单时出错", error);
        const msg = error.response?.data?.msg || "提交失败，请稍后再试";
        this.$message.error(msg);
      }
    },
    async updateOperation() {
      if (!this.validateBookForm()) return;
      try {
        const response = await this.$axios.put("/book/update", this.data);
        if (response.data.code === 200) {
          this.dialogBookOperation = false;
          await this.fetchFreshData();
          this.$message.success(response.data.msg);
          this.clearFormData();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        console.error("提交表单时出错", error);
        const msg = error.response?.data?.msg || "提交失败，请稍后再试";
        this.$message.error(msg);
      }
    },
    async fetchFreshData() {
      try {
        this.tableData = [];
        const params = {
          current: this.currentPage,
          size: this.pageSize,
          ...toDayRange(this.searchTime),
          deleted: this.showDeleted,
          ...this.bookQueryDto,
        };
        const response = await this.$axios.post("/book/query", params);
        const { data } = response;
        this.tableData = data.data;
        this.totalItems = data.total;
      } catch (error) {
        console.error("查询图书信息异常:", error);
      }
    },
    add() {
      this.isOperation = false;
      this.data = this.createEmptyBook();
      this.dialogBookOperation = true;
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
      this.dialogBookOperation = true;
      this.isOperation = true;
      this.data = {
        ...row,
        originalTotalCount: row.totalCount,
        originalAvailableCount: row.availableCount,
      };
    },
    handleDelete(row) {
      this.selectedRows.push(row);
      this.batchDelete();
    },
    async fetchBookshelves() {
      try {
        const response = await this.$axios.get("/bookshelf/queryAll");
        if (response.data.code === 200) {
          this.bookshelves = response.data.data || [];
        }
      } catch (error) {
        console.error("查询书架异常:", error);
      }
    },
    async fetchCategories() {
      try {
        const response = await this.$axios.get("/category/queryAll");
        if (response.data.code === 200) {
          this.categories = response.data.data || [];
        }
      } catch (error) {
        console.error("查询分类异常:", error);
      }
    },
  },
};
</script>
<style scoped lang="scss">
.stock-count {
  color: var(--admin-jade);
  font-weight: 600;
}

.stock-count--empty {
  color: var(--admin-gold);
}

.book-toolbar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.toolbar-field {
  display: grid;
  flex: 0 0 auto;
  align-content: end;
  gap: 7px;

  .top-bar {
    margin: 0;
  }

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-editor) {
    width: 100% !important;
  }
}

.name-field {
  width: 176px;
}

.author-field {
  width: 132px;
}

.category-field {
  width: 120px;
}

.date-field {
  width: 220px;
}

.toolbar-action {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 24px;
}

.add-action {
  width: 72px;
}

.view-action {
  min-width: 152px;
}

.collection-view-toggle {
  display: inline-flex;
  overflow: hidden;
  height: 24px;
  border: 1px solid var(--admin-line-strong);
  border-radius: 4px;

  :deep(.el-radio-button__inner) {
    min-width: 76px;
    height: 24px;
    padding: 5px 11px;
    color: var(--admin-ink-soft);
    border: 0;
    border-radius: 0;
    outline: 0 !important;
    background: transparent;
    box-shadow: none !important;
  }

  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    color: var(--admin-paper-light) !important;
    background: var(--admin-jade) !important;
    box-shadow: none !important;
  }
}

.book-form-scroll {
  padding-top: 2px;
}

@media (max-width: 760px) {
  .book-toolbar {
    align-items: stretch;
  }

  .toolbar-field,
  .name-field,
  .author-field,
  .category-field,
  .date-field {
    width: min(100%, 280px);
  }

  .add-action {
    width: 72px;
  }

  .book-form-scroll {
    padding-inline: 18px;
  }
}
</style>
