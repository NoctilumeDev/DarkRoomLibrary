<template>
  <section class="procurement-page">
    <header class="page-head">
      <div>
        <p>{{ sectionLabel }}</p>
        <h1>{{ pageTitle }}</h1>
        <span>{{ roleDescription }}</span>
      </div>
      <div class="head-actions">
        <el-button v-if="canCreate" type="primary" @click="openCreate">新建采购单</el-button>
        <el-button :loading="loading" @click="loadOrders">刷新</el-button>
      </div>
    </header>

    <div class="summary-strip">
      <div><span>当前列表</span><strong>{{ totalItems }}</strong></div>
      <div><span>待采购</span><strong>{{ countStatus(0) }}</strong></div>
      <div><span>处理中</span><strong>{{ activeCount }}</strong></div>
      <div><span>未读消息</span><strong>{{ unreadTotal }}</strong></div>
    </div>

    <div class="filter-bar">
      <div class="filter-field book-filter">
        <span>图书名称</span>
        <el-input v-model.trim="filters.bookName" placeholder="输入书名" clearable @keyup.enter="search" />
      </div>
      <div class="filter-field status-filter">
        <span>流转状态</span>
        <el-select v-model="filters.status" placeholder="全部状态" clearable>
          <el-option v-for="item in orderStatuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
      <div class="filter-actions">
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="orders" row-key="id" class="order-table">
      <el-table-column prop="id" label="单号" width="72" />
      <el-table-column prop="bookName" label="图书" min-width="150" show-overflow-tooltip />
      <el-table-column prop="requestCount" label="数量" width="72" />
      <el-table-column label="采购状态" width="105">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)">{{ statusName(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="采购员" min-width="105">
        <template #default="scope">{{ scope.row.purchaserName || "未指派" }}</template>
      </el-table-column>
      <el-table-column label="物流员" min-width="105">
        <template #default="scope">{{ scope.row.logisticsName || "未指派" }}</template>
      </el-table-column>
      <el-table-column label="物流状态" width="100">
        <template #default="scope">{{ logisticsName(scope.row.logisticsStatus) }}</template>
      </el-table-column>
      <el-table-column prop="trackingNo" label="运单号" min-width="110" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="165" />
      <el-table-column label="操作" width="380" fixed="right" class-name="operation-column">
        <template #default="scope">
          <el-button v-if="canAssignPurchaser(scope.row)" link type="primary" @click="openAssign(scope.row, 'purchaser')">指派采购</el-button>
          <el-button v-if="canClaim(scope.row)" link type="primary" @click="claim(scope.row)">认领</el-button>
          <el-button v-if="nextOrderStatus(scope.row) !== null" link type="primary" @click="advanceOrder(scope.row)">{{ orderActionName(scope.row) }}</el-button>
          <el-button v-if="canAssignLogistics(scope.row)" link type="primary" @click="openAssign(scope.row, 'logistics')">分配物流</el-button>
          <el-button v-if="nextLogisticsStatus(scope.row) !== null" link type="primary" @click="advanceLogistics(scope.row)">{{ logisticsActionName(scope.row) }}</el-button>
          <el-button v-if="canCancel(scope.row)" link type="danger" @click="cancelOrder(scope.row)">取消</el-button>
          <el-badge v-if="canMessage(scope.row)" :value="scope.row.unreadCount || 0" :hidden="!scope.row.unreadCount">
            <el-button link type="primary" @click="openMessages(scope.row)">沟通</el-button>
          </el-badge>
        </template>
      </el-table-column>
    </el-table>

    <div v-loading="loading" class="mobile-order-list">
      <article v-for="order in orders" :key="order.id" class="mobile-order-item">
        <header>
          <div>
            <small>采购单 #{{ order.id }}</small>
            <strong>{{ order.bookName }}</strong>
          </div>
          <el-tag :type="statusType(order.status)">{{ statusName(order.status) }}</el-tag>
        </header>
        <dl>
          <div><dt>数量</dt><dd>{{ order.requestCount }}</dd></div>
          <div><dt>采购员</dt><dd>{{ order.purchaserName || "未指派" }}</dd></div>
          <div><dt>物流员</dt><dd>{{ order.logisticsName || "未指派" }}</dd></div>
          <div><dt>物流状态</dt><dd>{{ logisticsName(order.logisticsStatus) }}</dd></div>
          <div><dt>运单号</dt><dd>{{ order.trackingNo || "暂无" }}</dd></div>
          <div><dt>更新时间</dt><dd>{{ order.updateTime || "暂无" }}</dd></div>
        </dl>
        <footer>
          <el-button v-if="canAssignPurchaser(order)" link type="primary" @click="openAssign(order, 'purchaser')">指派采购</el-button>
          <el-button v-if="canClaim(order)" link type="primary" @click="claim(order)">认领</el-button>
          <el-button v-if="nextOrderStatus(order) !== null" link type="primary" @click="advanceOrder(order)">{{ orderActionName(order) }}</el-button>
          <el-button v-if="canAssignLogistics(order)" link type="primary" @click="openAssign(order, 'logistics')">分配物流</el-button>
          <el-button v-if="nextLogisticsStatus(order) !== null" link type="primary" @click="advanceLogistics(order)">{{ logisticsActionName(order) }}</el-button>
          <el-button v-if="canCancel(order)" link type="danger" @click="cancelOrder(order)">取消</el-button>
          <el-badge v-if="canMessage(order)" :value="order.unreadCount || 0" :hidden="!order.unreadCount">
            <el-button link type="primary" @click="openMessages(order)">沟通</el-button>
          </el-badge>
        </footer>
      </article>
      <el-empty v-if="!orders.length && !loading" description="暂无采购单" />
    </div>

    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="totalItems"
      :page-sizes="[10, 20, 50]"
      class="order-pagination"
      layout="total, sizes, prev, pager, next"
      @size-change="loadOrders"
      @current-change="loadOrders"
    />

    <el-dialog
      v-model="createVisible"
      class="admin-editor-dialog procurement-dialog procurement-create-dialog"
      width="min(620px, calc(100vw - 32px))"
      top="8vh"
      append-to-body
    >
      <template #header>
        <h2 class="dialog-title">新建采购单</h2>
      </template>
      <div class="admin-form-scroll procurement-form-scroll">
        <el-form label-width="90px">
          <el-form-item label="图书">
            <el-select
              v-model="createForm.bookId"
              filterable
              remote
              reserve-keyword
              :remote-method="queueBookSearch"
              :loading="bookLoading"
              placeholder="输入书名搜索"
              style="width: 100%"
              @visible-change="handleBookSelectVisible"
            >
              <el-option v-for="book in books" :key="book.id" :label="`${book.name}（可借 ${book.availableCount}）`" :value="book.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="采购数量"><el-input-number v-model="createForm.requestCount" :min="1" :max="9999" /></el-form-item>
          <el-form-item label="采购员">
            <el-select v-model="createForm.purchaserId" clearable placeholder="可稍后指派" style="width: 100%">
              <el-option v-for="user in purchasers" :key="user.id" :label="user.userName" :value="user.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="申请说明">
            <el-input v-model="createForm.requestNote" type="textarea" :rows="8" maxlength="1000" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button class="admin-dialog-cancel" @click="createVisible=false">取消</el-button>
        <el-button class="admin-dialog-submit" type="primary" @click="createOrder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" class="procurement-dialog" :title="assignMode === 'purchaser' ? '指派采购员' : '分配物流员'" width="min(440px, calc(100vw - 32px))" append-to-body>
      <el-select v-model="assignUserId" filterable placeholder="选择人员" style="width: 100%">
        <el-option v-for="user in assignCandidates" :key="user.id" :label="user.userName" :value="user.id" />
      </el-select>
      <template #footer><el-button @click="assignVisible=false">取消</el-button><el-button type="primary" @click="submitAssign">确认</el-button></template>
    </el-dialog>

    <el-dialog v-model="logisticsVisible" class="procurement-dialog" :title="logisticsDialogTitle" width="min(520px, calc(100vw - 32px))" append-to-body>
      <el-form label-width="86px">
        <el-form-item label="承运方"><el-input v-model="logisticsForm.carrier" maxlength="100" /></el-form-item>
        <el-form-item label="运单号"><el-input v-model="logisticsForm.trackingNo" maxlength="100" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="logisticsForm.remark" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="logisticsVisible=false">取消</el-button><el-button type="primary" @click="submitLogistics">确认流转</el-button></template>
    </el-dialog>

    <el-dialog v-model="messageVisible" title="采购协作消息" class="procurement-dialog procurement-message-dialog" width="min(680px, calc(100vw - 32px))" append-to-body @closed="messageText=''">
      <el-radio-group v-if="messageChannels.length > 1" v-model="messageChannel" @change="loadMessages(false)">
        <el-radio-button v-for="channel in messageChannels" :key="channel.value" :value="channel.value">{{ channel.label }}</el-radio-button>
      </el-radio-group>
      <div class="message-list">
        <el-button
          v-if="messageHasOlder"
          class="message-history-button"
          link
          :loading="messageLoading"
          @click="loadMessages(true)"
        >
          查看更早消息
        </el-button>
        <div v-for="message in messages" :key="message.id" :class="['message-row', { mine: message.senderId === userInfo.id }]">
          <div><strong>{{ message.senderName }}</strong><span>{{ message.createTime }}</span></div>
          <p>{{ message.content }}</p>
        </div>
        <el-empty v-if="!messages.length" description="暂无消息" />
      </div>
      <div class="message-compose">
        <el-input v-model="messageText" type="textarea" maxlength="1000" show-word-limit placeholder="输入协作消息" />
        <el-button type="primary" @click="sendMessage">发送</el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import {
  isAdministratorRole,
  USER_ROLE,
} from "@/utils/userRoles.js";
import { hasOlderMessages } from "@/utils/messagePagination.js";

export default {
  name: "ProcurementWorkbench",
  data() {
    return {
      userInfo: { id: null, role: null, name: "" }, loading: false,
      orders: [], totalItems: 0, currentPage: 1, pageSize: 10,
      filters: { bookName: "", status: null },
      books: [], bookLoading: false, bookSearchTimer: null, bookSearchSequence: 0,
      purchasers: [], logisticsUsers: [],
      createVisible: false, createForm: { bookId: null, requestCount: 1, purchaserId: null, requestNote: "" },
      assignVisible: false, assignMode: "purchaser", assignOrder: null, assignUserId: null,
      logisticsVisible: false, logisticsOrder: null, logisticsTargetStatus: null,
      logisticsForm: { carrier: "", trackingNo: "", remark: "" },
      messageVisible: false, messageOrder: null, messageChannel: 0,
      messages: [], messageText: "", messageBeforeId: null,
      messageHasOlder: false, messageLoading: false, messagePageSize: 50,
      orderStatuses: [
        { value: 0, label: "待采购" }, { value: 1, label: "采购中" }, { value: 2, label: "已下单" },
        { value: 3, label: "已发货" }, { value: 4, label: "已到货" }, { value: 5, label: "已入库" },
        { value: 6, label: "已完成" }, { value: 7, label: "已取消" },
      ],
    };
  },
  computed: {
    role() { return this.userInfo.role; },
    isAdmin() { return isAdministratorRole(this.role); },
    isSuperAdmin() { return this.role === USER_ROLE.SUPER_ADMIN; },
    isPurchaser() { return this.role === USER_ROLE.ACQUISITIONS; },
    isLogistics() { return this.role === USER_ROLE.LOGISTICS; },
    canCreate() { return this.isAdmin; },
    pageTitle() { return this.isLogistics ? "物流入库工作台" : this.isPurchaser ? "采购协作工作台" : "采购物流管理"; },
    sectionLabel() { return this.isLogistics ? "LOGISTICS" : this.isPurchaser ? "PROCUREMENT" : "WORKFLOW"; },
    roleDescription() { return this.isLogistics ? "接收已分配任务，登记运输、到馆与入库进度" : this.isPurchaser ? "接下馆内需求，推进采购并将图书交接给物流人员" : "发起采购需求、指派协作人员，并查看每一步流转留痕"; },
    activeCount() { return this.orders.filter(item => item.status > 0 && item.status < 6).length; },
    unreadTotal() { return this.orders.reduce((sum, item) => sum + (item.unreadCount || 0), 0); },
    assignCandidates() { return this.assignMode === "purchaser" ? this.purchasers : this.logisticsUsers; },
    logisticsDialogTitle() { return `物流流转：${this.logisticsName(this.logisticsTargetStatus)}`; },
    messageChannels() {
      if (!this.isPurchaser) return [{ label: this.isLogistics ? "采购沟通" : "采购沟通", value: this.isLogistics ? 1 : 0 }];
      const channels = [{ label: "管理员沟通", value: 0 }];
      if (this.messageOrder?.logisticsId) channels.push({ label: "物流沟通", value: 1 });
      return channels;
    },
  },
  async created() {
    await this.loadAuth();
    await Promise.all([this.loadOrders(), this.loadReferenceData()]);
  },
  beforeUnmount() {
    clearTimeout(this.bookSearchTimer);
  },
  methods: {
    async loadAuth() {
      const response = await this.$axios.get("/user/auth");
      const user = response.data.data || {};
      this.userInfo = { id: user.id, role: user.userRole, name: user.userName };
    },
    async loadReferenceData() {
      const tasks = [];
      if (this.isAdmin) {
        tasks.push(this.loadPeople(USER_ROLE.ACQUISITIONS));
      }
      if (this.isPurchaser || this.isSuperAdmin) {
        tasks.push(this.loadPeople(USER_ROLE.LOGISTICS));
      }
      await Promise.all(tasks);
    },
    handleBookSelectVisible(visible) {
      if (visible && !this.books.length && !this.bookLoading) this.searchBooks("");
    },
    queueBookSearch(query) {
      clearTimeout(this.bookSearchTimer);
      this.bookSearchTimer = setTimeout(() => this.searchBooks(query), 220);
    },
    async searchBooks(query) {
      const requestSequence = ++this.bookSearchSequence;
      this.bookLoading = true;
      try {
        const response = await this.$axios.post("/book/query", {
          current: 1,
          size: 20,
          name: String(query || "").trim() || null,
        });
        if (requestSequence !== this.bookSearchSequence) return;
        if (response.data.code === 200) this.books = response.data.data || [];
        else this.$message.error(response.data.msg || "图书搜索失败");
      } catch (error) {
        if (requestSequence === this.bookSearchSequence && error.response?.status !== 401) {
          this.$message.error("图书搜索失败，请稍后重试");
        }
      } finally {
        if (requestSequence === this.bookSearchSequence) this.bookLoading = false;
      }
    },
    async loadPeople(role) {
      const response = await this.$axios.get("/user/collaborationUsers", { params: { role } });
      if (response.data.code !== 200) return;
      if (role === USER_ROLE.ACQUISITIONS) {
        this.purchasers = response.data.data || [];
      }
      if (role === USER_ROLE.LOGISTICS) {
        this.logisticsUsers = response.data.data || [];
      }
    },
    async loadOrders() {
      this.loading = true;
      try {
        const response = await this.$axios.post("/procurement/query", { ...this.filters, current: this.currentPage, size: this.pageSize });
        if (response.data.code === 200) { this.orders = response.data.data || []; this.totalItems = response.data.total || 0; }
        else this.$message.error(response.data.msg || "采购单加载失败");
      } finally { this.loading = false; }
    },
    search() { this.currentPage = 1; this.loadOrders(); },
    resetFilters() { this.filters = { bookName: "", status: null }; this.search(); },
    countStatus(status) { return this.orders.filter(item => item.status === status).length; },
    statusName(status) { return this.orderStatuses.find(item => item.value === status)?.label || "未知"; },
    statusType(status) { return status === 6 ? "success" : status === 7 ? "info" : status === 0 ? "warning" : undefined; },
    logisticsName(status) { return ({ 0: "待接收", 1: "运输中", 2: "已到馆", 3: "已入库" })[status] || "未分配"; },
    openCreate() {
      this.createForm = { bookId: null, requestCount: 1, purchaserId: null, requestNote: "" };
      this.createVisible = true;
      this.searchBooks("");
    },
    async createOrder() {
      if (!this.createForm.bookId) return this.$message.warning("请选择图书");
      const response = await this.$axios.post("/procurement/save", this.createForm);
      this.$message[response.data.code === 200 ? "success" : "error"](response.data.msg);
      if (response.data.code === 200) { this.createVisible = false; this.loadOrders(); }
    },
    canAssignPurchaser(row) { return this.isAdmin && row.status < 6 && row.status !== 7; },
    canClaim(row) { return this.isPurchaser && !row.purchaserId && row.status < 6; },
    async claim(row) { await this.callAndRefresh(this.$axios.put(`/procurement/claim/${row.id}`)); },
    openAssign(row, mode) { this.assignOrder = row; this.assignMode = mode; this.assignUserId = mode === "purchaser" ? row.purchaserId : row.logisticsId; this.assignVisible = true; },
    async submitAssign() {
      if (!this.assignUserId) return this.$message.warning("请选择人员");
      const url = this.assignMode === "purchaser" ? "/procurement/assignPurchaser" : "/procurement/assignLogistics";
      const response = await this.$axios.put(url, { orderId: this.assignOrder.id, userId: this.assignUserId });
      this.$message[response.data.code === 200 ? "success" : "error"](response.data.msg);
      if (response.data.code === 200) { this.assignVisible = false; this.loadOrders(); }
    },
    nextOrderStatus(row) {
      if (!this.isAdmin && !this.isPurchaser) return null;
      return ({ 0: 1, 1: 2, 5: 6 })[row.status] ?? null;
    },
    orderActionName(row) { return ({ 0: "开始采购", 1: "确认下单", 5: "完成采购" })[row.status] || "推进"; },
    async advanceOrder(row) { await this.callAndRefresh(this.$axios.put("/procurement/updateStatus", { id: row.id, status: this.nextOrderStatus(row) })); },
    canCancel(row) { return (this.isAdmin || this.isPurchaser) && row.status < 5; },
    async cancelOrder(row) {
      const confirmed = await this.$swalConfirm({ title: "取消采购单？", text: `采购单 #${row.id} 取消后不可继续流转。`, icon: "warning" });
      if (confirmed) await this.callAndRefresh(this.$axios.put("/procurement/updateStatus", { id: row.id, status: 7 }));
    },
    canAssignLogistics(row) {
      return (this.isPurchaser || this.isSuperAdmin) && row.status >= 2 && row.status < 5;
    },
    nextLogisticsStatus(row) {
      if ((!this.isLogistics && !this.isPurchaser && !this.isSuperAdmin) || !row.logisticsId || row.status >= 6 || row.status === 7) return null;
      const current = row.logisticsStatus == null ? 0 : row.logisticsStatus;
      return current < 3 ? current + 1 : null;
    },
    logisticsActionName(row) { return ({ 0: "开始运输", 1: "确认到馆", 2: "确认入库" })[row.logisticsStatus == null ? 0 : row.logisticsStatus] || "更新物流"; },
    advanceLogistics(row) {
      this.logisticsOrder = row; this.logisticsTargetStatus = this.nextLogisticsStatus(row);
      this.logisticsForm = { carrier: row.carrier || "", trackingNo: row.trackingNo || "", remark: row.logisticsRemark || "" };
      this.logisticsVisible = true;
    },
    async submitLogistics() {
      const response = await this.$axios.put("/procurement/updateLogistics", { orderId: this.logisticsOrder.id, status: this.logisticsTargetStatus, ...this.logisticsForm });
      this.$message[response.data.code === 200 ? "success" : "error"](response.data.msg);
      if (response.data.code === 200) { this.logisticsVisible = false; this.loadOrders(); }
    },
    openMessages(row) {
      this.messageOrder = row;
      this.messageChannel = this.isLogistics ? 1 : 0;
      this.messageVisible = true;
      this.loadMessages(false);
    },
    canMessage(row) {
      if (this.isLogistics) return Boolean(row.purchaserId);
      if (this.isPurchaser) return Boolean(row.requesterId || row.logisticsId);
      return Boolean(row.purchaserId);
    },
    async loadMessages(loadOlder = false) {
      if (!this.messageOrder) return;
      if (!loadOlder) {
        this.messages = [];
        this.messageBeforeId = null;
        this.messageHasOlder = false;
      }
      this.messageLoading = true;
      try {
        const response = await this.$axios.post("/procurement/message/query", {
          orderId: this.messageOrder.id,
          channelType: this.messageChannel,
          current: 1,
          size: this.messagePageSize,
          beforeId: loadOlder ? this.messageBeforeId : null,
        });
        if (response.data.code !== 200) return this.$message.error(response.data.msg);
        const descendingBatch = response.data.data || [];
        const chronologicalBatch = [...descendingBatch].reverse();
        this.messages = loadOlder
          ? [...chronologicalBatch, ...this.messages]
          : chronologicalBatch;
        this.messageBeforeId = descendingBatch.length
          ? Math.min(...descendingBatch.map(message => Number(message.id)))
          : this.messageBeforeId;
        this.messageHasOlder = hasOlderMessages(
          response.data.total,
          descendingBatch.length
        );
        const unreadIds = descendingBatch
          .filter(message => message.receiverId === this.userInfo.id && !message.readStatus)
          .map(message => message.id);
        if (unreadIds.length) {
          const readResponse = await this.$axios.put("/procurement/message/read", {
            orderId: this.messageOrder.id,
            channelType: this.messageChannel,
            messageIds: unreadIds,
          });
          if (readResponse.data.code === 200) {
            this.messages = this.messages.map(message =>
              unreadIds.includes(message.id) ? { ...message, readStatus: true } : message
            );
            const order = this.orders.find(item => item.id === this.messageOrder.id);
            if (order) order.unreadCount = Math.max(0, (order.unreadCount || 0) - unreadIds.length);
          }
        }
      } catch (error) {
        if (error.response?.status !== 401) this.$message.error("消息加载失败，请稍后重试");
      } finally {
        this.messageLoading = false;
      }
    },
    messageReceiverId() {
      if (this.isLogistics) return this.messageOrder.purchaserId;
      if (this.isPurchaser) return this.messageChannel === 0 ? this.messageOrder.requesterId : this.messageOrder.logisticsId;
      return this.messageOrder.purchaserId;
    },
    async sendMessage() {
      if (!this.messageText.trim()) return this.$message.warning("请输入消息");
      const receiverId = this.messageReceiverId();
      if (!receiverId) return this.$message.warning("请先完成相关人员指派");
      const response = await this.$axios.post("/procurement/message/send", { orderId: this.messageOrder.id, channelType: this.messageChannel, receiverId, content: this.messageText });
      if (response.data.code === 200) { this.messageText = ""; await this.loadMessages(false); }
      else this.$message.error(response.data.msg);
    },
    async callAndRefresh(promise) {
      const response = await promise;
      this.$message[response.data.code === 200 ? "success" : "error"](response.data.msg);
      if (response.data.code === 200) await this.loadOrders();
    },
  },
};
</script>

<style scoped lang="scss">
.procurement-page {
  --workbench-ink: var(--admin-text, #2d2923);
  --workbench-ink-soft: var(--admin-text-secondary, #4f473c);
  --workbench-muted: var(--admin-muted, #665d51);
  --workbench-line: var(--admin-border, rgba(72, 58, 41, 0.17));
  --workbench-line-strong: var(--admin-border-strong, rgba(72, 58, 41, 0.28));
  --workbench-paper: var(--admin-surface, #f3eadb);
  --workbench-paper-light: var(--admin-surface-strong, #fbf5e9);
  --workbench-paper-deep: var(--admin-surface-muted, #e9ddca);
  --workbench-seal: var(--admin-accent, #93483a);
  --workbench-seal-soft: var(--admin-accent-soft, rgba(147, 72, 58, 0.09));
  --workbench-gold: var(--admin-gold, #72562f);
  display: grid;
  gap: 24px;
  min-width: 0;
  color: var(--workbench-ink);
  --el-color-primary: var(--workbench-seal);
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 28px;
  padding: 2px 0 24px;
  border-bottom: 1px solid var(--workbench-line-strong);
}

.page-head p {
  margin: 0;
  color: var(--workbench-gold);
  font-size: 12px;
  font-weight: 700;
}

.page-head h1 {
  margin: 6px 0 8px;
  color: var(--workbench-ink);
  font-family: "STKaiti", "KaiTi", "FangSong", SimSun, serif;
  font-size: clamp(28px, 3vw, 36px);
  font-weight: 500;
  letter-spacing: 0;
}

.page-head span {
  color: var(--workbench-ink-soft);
  line-height: 1.8;
}

.head-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  align-items: center;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-block: 1px solid var(--workbench-line-strong);
  background: transparent;
}

.summary-strip div {
  position: relative;
  min-height: 92px;
  padding: 18px 22px;
  border-right: 1px solid var(--workbench-line);
}

.summary-strip div::before {
  content: "";
  position: absolute;
  left: 0;
  top: 22px;
  bottom: 22px;
  width: 2px;
  background: var(--workbench-gold);
  opacity: 0.58;
}

.summary-strip div:last-child { border-right: 0; }
.summary-strip div:last-child::before { background: var(--workbench-seal); }
.summary-strip span { display: block; color: var(--workbench-muted); font-size: 13px; }
.summary-strip strong { display: block; margin-top: 7px; color: var(--workbench-ink); font-family: Georgia, "Times New Roman", serif; font-size: 28px; font-weight: 500; }

.filter-bar {
  display: grid;
  grid-template-columns: minmax(210px, 280px) minmax(150px, 180px) auto;
  gap: 12px;
  align-items: end;
  padding: 0 0 20px;
  border-bottom: 1px solid var(--workbench-line);
}

.filter-field {
  display: grid;
  gap: 7px;
}

.filter-field > span {
  color: var(--workbench-muted);
  font-size: 12px;
  font-weight: 600;
}

.filter-actions {
  display: flex;
  gap: 9px;
}

.order-table {
  width: 100%;
  border-block: 1px solid var(--workbench-line-strong);
}

.mobile-order-list { display: none; }

.order-pagination {
  justify-self: end;
  padding-top: 2px;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-textarea__inner),
:deep(.el-input-number) {
  color: var(--workbench-ink);
  background: color-mix(in srgb, var(--workbench-paper-light) 66%, transparent) !important;
  box-shadow: 0 0 0 1px var(--workbench-line) inset !important;
}

:deep(.el-input__inner),
:deep(.el-select__selected-item),
:deep(.el-textarea__inner) {
  color: var(--workbench-ink) !important;
}

:deep(.el-input__inner::placeholder),
:deep(.el-select__placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: var(--workbench-muted) !important;
}

:deep(.el-button:not(.is-link)) {
  border-radius: 4px;
  color: var(--workbench-ink-soft);
  border-color: var(--workbench-line-strong);
  background: color-mix(in srgb, var(--workbench-paper-light) 58%, transparent);
}

:deep(.el-button--primary:not(.is-link)) {
  color: #fffaf1;
  border-color: var(--workbench-seal);
  background: var(--workbench-seal);
}

:deep(.el-button.is-link) { color: var(--workbench-gold); }
:deep(.el-button.is-link.el-button--danger) { color: var(--workbench-seal); }

:deep(.el-button) {
  transition: color 0.2s ease, border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.24s ease, transform 0.18s ease;
}

@media (hover: hover) {
  :deep(.el-button:not(.is-disabled):not(.is-link):hover) {
    transform: translateY(-1px);
    box-shadow: 0 5px 14px color-mix(in srgb, var(--workbench-gold) 14%, transparent);
  }
}

:deep(.el-button:not(.is-disabled):not(.is-link):active) { transform: translateY(0) scale(0.985); }

:deep(td.operation-column .cell) {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px 11px;
}

:deep(th.operation-column.el-table__cell) {
  background: var(--workbench-paper-deep) !important;
}

:deep(td.operation-column.el-table__cell) {
  background: var(--workbench-paper) !important;
}

:deep(.el-table__body tr:hover > td.operation-column.el-table__cell) {
  background: color-mix(in srgb, var(--workbench-paper) 90%, var(--workbench-seal-soft)) !important;
}

:deep(td.operation-column .el-button) { margin: 0; line-height: 24px; }
:deep(td.operation-column .el-badge) { display: inline-flex; align-items: center; vertical-align: middle; line-height: 24px; }
:deep(td.operation-column .el-badge .el-button) { height: 24px; }
:deep(td.operation-column .el-badge__content.is-fixed) { top: 1px; right: 1px; transform: translate(58%, -42%); }

:deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: color-mix(in srgb, var(--workbench-paper-deep) 68%, transparent);
  --el-table-header-text-color: var(--workbench-ink-soft);
  --el-table-text-color: var(--workbench-ink);
  --el-table-border-color: var(--workbench-line);
  --el-table-row-hover-bg-color: var(--workbench-seal-soft);
  color: var(--workbench-ink);
  background: transparent;
}

:deep(.el-table th.el-table__cell) {
  color: var(--workbench-ink-soft);
  background: color-mix(in srgb, var(--workbench-paper-deep) 66%, transparent);
  font-weight: 600;
}

:deep(.el-table tr),
:deep(.el-table td.el-table__cell) {
  color: var(--workbench-ink);
  background: transparent;
}

:deep(.el-table__inner-wrapper::before) { background: var(--workbench-line); }
:deep(.el-table__fixed-right::before) { background: var(--workbench-line); }

:deep(.el-tag) {
  border-color: var(--workbench-line-strong);
  color: var(--workbench-ink-soft);
  background: color-mix(in srgb, var(--workbench-paper-deep) 58%, transparent);
}

:deep(.el-tag--success) { color: #3f6357; background: rgba(74, 111, 99, 0.1); }
:deep(.el-tag--warning) { color: #76572d; background: rgba(132, 94, 43, 0.1); }

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: transparent;
  --el-pagination-text-color: var(--workbench-ink-soft);
  --el-pagination-hover-color: var(--workbench-seal);
  color: var(--workbench-ink-soft);
}

:deep(.el-pagination__total),
:deep(.el-pagination__jump),
:deep(.el-pagination button),
:deep(.el-pager li) {
  color: var(--workbench-ink-soft) !important;
  background: transparent !important;
}

:deep(.el-pager li.is-active) {
  color: #fffaf1 !important;
  background: var(--workbench-seal) !important;
}

.message-list {
  min-height: 260px;
  max-height: 420px;
  overflow: auto;
  display: grid;
  gap: 10px;
  padding: 14px 0;
}

.message-history-button {
  justify-self: center;
  color: var(--workbench-gold) !important;
}

.message-row {
  max-width: 78%;
  padding: 12px 14px;
  border-left: 2px solid var(--workbench-gold);
  color: var(--workbench-ink);
  background: color-mix(in srgb, var(--workbench-paper-deep) 64%, transparent);
}

.message-row.mine {
  margin-left: auto;
  border-right: 2px solid var(--workbench-seal);
  border-left: 0;
  background: var(--workbench-seal-soft);
}

.message-row div { display: flex; justify-content: space-between; gap: 20px; color: var(--workbench-ink-soft); font-size: 12px; }
.message-row p { margin: 7px 0 0; color: var(--workbench-ink); line-height: 1.7; white-space: pre-wrap; }
.message-compose { display: grid; grid-template-columns: 1fr auto; gap: 10px; align-items: end; }

:global(.procurement-dialog) {
  --workbench-ink: var(--admin-ink, #2d2923);
  --workbench-ink-soft: var(--admin-ink-soft, #4f473c);
  --workbench-muted: var(--admin-ink-muted, #665d51);
  --workbench-line: var(--admin-line, rgba(72, 58, 41, 0.17));
  --workbench-line-strong: var(--admin-line-strong, rgba(72, 58, 41, 0.28));
  --workbench-paper: var(--admin-paper, #f3eadb);
  --workbench-paper-light: var(--admin-paper-light, #fbf5e9);
  --workbench-paper-deep: var(--admin-paper-wash, #e9ddca);
  --workbench-seal: var(--admin-seal-solid, #824034);
  --workbench-seal-soft: var(--admin-accent-soft, rgba(147, 72, 58, 0.09));
  --workbench-gold: var(--admin-gold, #72562f);
  --el-dialog-bg-color: var(--workbench-paper-light);
  max-height: min(82dvh, 700px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  color: var(--workbench-ink);
  border: 1px solid var(--workbench-line);
  border-radius: 4px;
  background: var(--workbench-paper-light);
}

:global(.procurement-dialog .el-dialog__header),
:global(.procurement-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  margin: 0;
  background: var(--workbench-paper-light) !important;
}

:global(.procurement-dialog .el-dialog__header) {
  padding: 20px 24px 14px !important;
  border-bottom: 1px solid var(--workbench-line);
}

:global(.procurement-dialog .el-dialog__body) {
  min-height: 0;
  flex: 1 1 auto;
  padding: 20px 24px !important;
  overflow-y: auto;
  color: var(--workbench-ink);
  background: var(--workbench-paper-light) !important;
}

:global(.procurement-dialog .el-dialog__footer) {
  padding: 14px 24px 18px;
  border-top: 1px solid var(--workbench-line);
}

:global(.procurement-dialog .el-dialog__title),
:global(.procurement-dialog .el-dialog__close),
:global(.procurement-dialog .el-form-item__label),
:global(.procurement-dialog .el-input__inner),
:global(.procurement-dialog .el-select__selected-item),
:global(.procurement-dialog .el-textarea__inner) {
  color: var(--workbench-ink) !important;
}

:global(.procurement-dialog .el-input__inner::placeholder),
:global(.procurement-dialog .el-select__placeholder),
:global(.procurement-dialog .el-textarea__inner::placeholder) {
  color: var(--workbench-muted) !important;
}

:global(.procurement-dialog .el-input__wrapper),
:global(.procurement-dialog .el-select__wrapper),
:global(.procurement-dialog .el-textarea__inner),
:global(.procurement-dialog .el-input-number) {
  background: color-mix(in srgb, var(--workbench-paper) 72%, transparent) !important;
  box-shadow: 0 0 0 1px var(--workbench-line-strong) inset !important;
}

:global(.procurement-dialog .el-input__count),
:global(.procurement-dialog .el-input__count-inner) {
  color: var(--workbench-muted) !important;
  background: transparent !important;
}

:global(.procurement-dialog .el-textarea__inner::-webkit-resizer) {
  background: linear-gradient(135deg, transparent 0 52%, var(--workbench-muted) 53% 59%, transparent 60% 69%, var(--workbench-muted) 70% 76%, transparent 77%);
}

:global(.procurement-dialog .el-radio-button__inner) {
  color: var(--workbench-ink-soft) !important;
  border-color: var(--workbench-line-strong) !important;
  background: transparent !important;
  box-shadow: none !important;
}

:global(.procurement-dialog .el-radio-button__original-radio:checked + .el-radio-button__inner) {
  color: #fffaf1 !important;
  border-color: var(--workbench-seal) !important;
  background: var(--workbench-seal) !important;
}

:global(.procurement-message-dialog .message-list) {
  min-height: 220px;
  max-height: min(42dvh, 420px);
}

:global(.procurement-message-dialog .message-row) {
  color: var(--workbench-ink);
  background: color-mix(in srgb, var(--workbench-paper-deep) 64%, transparent);
}

:global(.procurement-message-dialog .message-row.mine) {
  background: var(--workbench-seal-soft);
}

:global(.procurement-message-dialog .message-row div) { color: var(--workbench-ink-soft); }
:global(.procurement-message-dialog .message-row p) { color: var(--workbench-ink); }

:global(.procurement-create-dialog) {
  --procurement-dialog-ink: var(--admin-ink, #2d2923);
  --procurement-dialog-ink-soft: var(--admin-ink-soft, #4f473c);
  --procurement-dialog-line: var(--admin-line, rgba(72, 58, 41, 0.17));
  --procurement-dialog-line-strong: var(--admin-line-strong, rgba(72, 58, 41, 0.28));
  --procurement-dialog-paper: var(--admin-paper, #f3eadb);
  --procurement-dialog-paper-light: var(--admin-paper-light, #fbf5e9);
  --procurement-dialog-seal: var(--admin-seal-solid, #824034);
  height: min(72vh, 640px);
  max-height: min(72vh, 640px);
  display: flex;
  flex-direction: column;
  margin-bottom: 0;
  overflow: hidden;
  color: var(--procurement-dialog-ink);
  border: 1px solid var(--procurement-dialog-line);
  border-radius: 4px;
  background: var(--procurement-dialog-paper-light);
}

:global(.procurement-create-dialog .el-dialog__header),
:global(.procurement-create-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.procurement-create-dialog .el-dialog__header) {
  margin: 0;
  padding: 20px 24px 14px;
  border-bottom: 1px solid var(--procurement-dialog-line);
}

:global(.procurement-create-dialog .dialog-title) {
  margin: 0;
  color: var(--procurement-dialog-ink);
  font-family: "STKaiti", "KaiTi", serif;
  font-size: 20px;
  font-weight: 600;
}

:global(.procurement-create-dialog .el-dialog__body) {
  min-height: 0;
  flex: 1 1 auto;
  padding: 0;
  overflow: hidden;
}

:global(.procurement-create-dialog .procurement-form-scroll) {
  height: 100%;
  padding: 24px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  box-sizing: border-box;
}

:global(.procurement-create-dialog .el-dialog__footer) {
  padding: 14px 24px 18px;
  border-top: 1px solid var(--procurement-dialog-line);
}

:global(.procurement-create-dialog .el-form-item__label),
:global(.procurement-create-dialog .el-input__inner),
:global(.procurement-create-dialog .el-textarea__inner) {
  color: var(--procurement-dialog-ink);
}

:global(.procurement-create-dialog .el-input__wrapper),
:global(.procurement-create-dialog .el-select__wrapper),
:global(.procurement-create-dialog .el-textarea__inner),
:global(.procurement-create-dialog .el-input-number) {
  background: color-mix(in srgb, var(--procurement-dialog-paper) 72%, transparent) !important;
  box-shadow: 0 0 0 1px var(--procurement-dialog-line-strong) inset !important;
}

:global(.procurement-create-dialog .el-button) {
  border-radius: 4px;
}

:global(.procurement-create-dialog .admin-dialog-submit) {
  color: #fffaf2;
  border-color: var(--procurement-dialog-seal);
  background: var(--procurement-dialog-seal);
}

:global(.procurement-create-dialog .admin-dialog-cancel) {
  color: var(--procurement-dialog-ink-soft);
  border-color: var(--procurement-dialog-line-strong);
  background: transparent;
}

@media (max-width: 900px) {
  .page-head { align-items: flex-start; flex-direction: column; }
  .summary-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .summary-strip div:nth-child(2) { border-right: 0; }
  .summary-strip div:nth-child(-n + 2) { border-bottom: 1px solid var(--workbench-line); }
  .filter-bar { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; }
}

@media (max-width: 640px) {
  .filter-bar { grid-template-columns: 1fr; }
  .filter-actions, .head-actions { width: 100%; }
  .filter-actions :deep(.el-button), .head-actions :deep(.el-button) { flex: 1; }
  .summary-strip div { min-height: 82px; padding: 15px 14px; }
  .summary-strip strong { font-size: 24px; }
  .order-pagination { justify-self: start; max-width: 100%; overflow-x: auto; }
  .order-table { display: none; }
  .mobile-order-list { display: grid; gap: 0; }
  .mobile-order-item { padding: 18px 0; border-bottom: 1px solid var(--workbench-line-strong); }
  .mobile-order-item:first-child { border-top: 1px solid var(--workbench-line-strong); }
  .mobile-order-item header { display: flex; justify-content: space-between; gap: 14px; align-items: flex-start; }
  .mobile-order-item header div { display: grid; gap: 5px; min-width: 0; }
  .mobile-order-item small { color: var(--workbench-gold); }
  .mobile-order-item strong { overflow-wrap: anywhere; color: var(--workbench-ink); font-family: "STKaiti", "KaiTi", serif; font-size: 18px; font-weight: 600; }
  .mobile-order-item dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 16px 0 12px; }
  .mobile-order-item dl div { min-width: 0; padding: 9px 10px 9px 0; border-top: 1px solid var(--workbench-line); }
  .mobile-order-item dt { color: var(--workbench-muted); font-size: 11px; }
  .mobile-order-item dd { margin: 4px 0 0; overflow-wrap: anywhere; color: var(--workbench-ink-soft); line-height: 1.5; }
  .mobile-order-item footer { display: flex; flex-wrap: wrap; gap: 5px 12px; align-items: center; }
  .mobile-order-item footer :deep(.el-button) { margin: 0; }
  .mobile-order-item footer :deep(.el-badge) { display: inline-flex; align-items: center; line-height: 24px; }
  .mobile-order-item footer :deep(.el-badge__content.is-fixed) { top: 1px; right: 1px; transform: translate(58%, -42%); }
  .message-row { max-width: 92%; }
  .message-compose { grid-template-columns: 1fr; }

  :global(.procurement-dialog) {
    width: calc(100vw - 24px) !important;
    max-height: 90dvh;
    margin-top: 4dvh !important;
  }

  :global(.procurement-dialog .el-dialog__header) { padding: 16px 18px 12px !important; }
  :global(.procurement-dialog .el-dialog__body) { padding: 16px 18px !important; }
  :global(.procurement-dialog .el-dialog__footer) { padding: 12px 18px 16px; }
  :global(.procurement-message-dialog .message-list) { min-height: 180px; max-height: 36dvh; }
  :global(.procurement-message-dialog .message-compose) { grid-template-columns: 1fr; }
  :global(.procurement-message-dialog .message-row) { max-width: 92%; }

  :global(.procurement-create-dialog) {
    width: calc(100vw - 24px) !important;
    height: 90vh;
    max-height: 90vh;
    margin-top: 3vh !important;
  }

  :global(.procurement-create-dialog .procurement-form-scroll) { padding-inline: 18px; }
}
</style>
