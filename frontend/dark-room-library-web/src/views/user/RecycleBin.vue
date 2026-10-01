<template>
  <section class="reader-page recycle-page">
    <header class="page-title">
      <p>暂存的墨迹</p>
      <h1>回收笺</h1>
      <span>你主动收起的留言与书评会在这里保留 30 天。管理员治理的内容不会出现在这里。</span>
    </header>

    <div class="recycle-tabs" role="tablist" aria-label="回收内容类型">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-loading="loading" class="recycle-list">
      <template v-if="activeTab === 'reviews'">
        <article v-for="item in reviewItems" :key="`review-${item.id}`" class="recycle-card">
          <div>
            <small>书评 · 《{{ item.bookName || "未命名图书" }}》</small>
            <h2>{{ item.content }}</h2>
            <p>
              {{ formatDate(item.deletedAt) }} 收起 ·
              {{ remainingText(item.restoreDeadline) }}
            </p>
          </div>
          <button type="button" @click="restoreReview(item)">恢复书评</button>
        </article>
      </template>

      <template v-else>
        <article v-for="item in messageItems" :key="`message-${item.id}`" class="recycle-card">
          <div>
            <small>留言<span v-if="item.attachmentName"> · 附件 {{ item.attachmentName }}</span></small>
            <h2>{{ item.content || "仅附件留言" }}</h2>
            <p>
              {{ formatDate(item.deletedAt) }} 收起 ·
              {{ remainingText(item.restoreDeadline) }}
            </p>
          </div>
          <button type="button" @click="restoreMessage(item)">恢复留言</button>
        </article>
      </template>

      <el-empty
        v-if="!loading && !currentItems.length"
        :description="activeTab === 'reviews' ? '没有待恢复的书评' : '没有待恢复的留言'"
      />
    </div>

    <el-pagination
      v-if="currentTotal > pageSize"
      class="recycle-pager"
      layout="total, prev, pager, next"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="currentTotal"
      @current-change="changePage"
    />
  </section>
</template>

<script>
import {
  formatAppMonthDayTime,
  remainingAppDays,
} from "@/utils/dateTime.js";

export default {
  name: "ReaderRecycleBin",
  data() {
    return {
      activeTab: "reviews",
      tabs: [
        { key: "reviews", label: "书评" },
        { key: "messages", label: "留言" },
      ],
      reviewItems: [],
      messageItems: [],
      reviewTotal: 0,
      messageTotal: 0,
      reviewPage: 1,
      messagePage: 1,
      pageSize: 8,
      loading: false,
      requestId: 0,
    };
  },
  computed: {
    currentItems() {
      return this.activeTab === "reviews" ? this.reviewItems : this.messageItems;
    },
    currentTotal() {
      return this.activeTab === "reviews" ? this.reviewTotal : this.messageTotal;
    },
    currentPage() {
      return this.activeTab === "reviews" ? this.reviewPage : this.messagePage;
    },
  },
  watch: {
    activeTab() {
      this.fetchCurrent();
    },
  },
  created() {
    this.fetchCurrent();
  },
  beforeUnmount() {
    this.requestId += 1;
  },
  methods: {
    endpoint(type = this.activeTab) {
      return type === "reviews" ? "/bookReview/recycle/query" : "/messageBoard/recycle/query";
    },
    async fetchCurrent() {
      const type = this.activeTab;
      const requestId = ++this.requestId;
      this.loading = true;
      try {
        const page = type === "reviews" ? this.reviewPage : this.messagePage;
        const response = await this.$axios.post(this.endpoint(type), {
          current: page,
          size: this.pageSize,
        });
        if (requestId !== this.requestId || type !== this.activeTab) return;
        if (response.data.code !== 200) {
          this.$message.error(response.data.msg || "回收站加载失败。");
          return;
        }
        if (type === "reviews") {
          this.reviewItems = response.data.data || [];
          this.reviewTotal = response.data.total || 0;
        } else {
          this.messageItems = response.data.data || [];
          this.messageTotal = response.data.total || 0;
        }
      } catch (error) {
        if (requestId === this.requestId) {
          this.$message.error(error.response?.data?.msg || "回收站加载失败。");
        }
      } finally {
        if (requestId === this.requestId) this.loading = false;
      }
    },
    changePage(page) {
      if (this.activeTab === "reviews") this.reviewPage = page;
      else this.messagePage = page;
      this.fetchCurrent();
    },
    remainingText(deadline) {
      const days = remainingAppDays(deadline);
      if (days === null) return "恢复期限未知";
      return `还可恢复 ${days} 天（北京时间 ${this.formatDate(deadline)} 截止）`;
    },
    formatDate(value) {
      return formatAppMonthDayTime(value);
    },
    async restoreReview(item) {
      await this.restore("/bookReview/restore", item.id, "书评");
    },
    async restoreMessage(item) {
      await this.restore("/messageBoard/restore", item.id, "留言");
    },
    async restore(path, id, label) {
      const confirmed = await this.$swalConfirm({
        title: `恢复${label}？`,
        text: `恢复后，这条${label}会重新出现在原来的公开位置。`,
        icon: "question",
        confirmButtonText: "恢复",
        cancelButtonText: "暂不",
        quiet: true,
      });
      if (!confirmed) return;
      try {
        const response = await this.$axios.post(path, [id]);
        if (response.data.code !== 200) {
          this.$message.error(response.data.msg || "恢复失败。");
          return;
        }
        this.$message.success(response.data.msg || "已恢复。");
        await this.fetchCurrent();
      } catch (error) {
        this.$message.error(error.response?.data?.msg || "恢复失败。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.recycle-page { display: grid; gap: 18px; }
.page-title { min-height: 176px; display: grid; align-content: center; }
.recycle-tabs { display: flex; gap: 8px; }
.recycle-tabs button {
  padding: 8px 20px;
  border: 1px solid var(--paper-line);
  border-radius: 999px;
  color: var(--ink-soft);
  background: var(--paper-raised);
  cursor: pointer;
}
.recycle-tabs button.active {
  color: var(--paper);
  border-color: var(--ink);
  background: var(--ink);
}
.recycle-list { min-height: 240px; }
.recycle-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 22px;
  padding: 22px 0;
  border-bottom: 1px solid var(--paper-line);
}
.recycle-card:first-child { padding-top: 0; }
.recycle-card small { color: var(--jade); }
.recycle-card h2 {
  margin: 8px 0;
  color: var(--ink);
  font: 400 17px/1.8 var(--reader-serif);
}
.recycle-card p { margin: 0; color: var(--ink-faint); font-size: 12px; }
.recycle-card > button {
  padding: 8px 14px;
  border: 1px solid color-mix(in srgb, var(--jade) 55%, var(--paper-line));
  border-radius: 4px;
  color: var(--jade);
  background: transparent;
  cursor: pointer;
}
.recycle-pager { justify-content: flex-end; }
@media (max-width: 620px) {
  .recycle-card { grid-template-columns: 1fr; gap: 12px; }
  .recycle-card > button { width: 100%; }
}
</style>
