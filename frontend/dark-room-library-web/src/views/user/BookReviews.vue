<template>
  <section class="reader-page">
    <header class="page-title review-title">
      <p>读者留墨</p>
      <h1>书评回廊</h1>
      <span>读完一本书之后，总有人愿意在这里留下一点墨迹。</span>
    </header>

    <div class="review-toolbar">
      <el-radio-group
        v-model="sortBy"
        class="reader-segmented"
        size="large"
        @change="fetchReviews(1)"
      >
        <el-radio-button value="latest">最新书评</el-radio-button>
        <el-radio-button value="hot">最热书评</el-radio-button>
      </el-radio-group>
      <button type="button" @click="$router.push('/bookSearch')">去检索台找一本书</button>
    </div>

    <div v-loading="loading" class="review-hall">
      <article v-for="review in reviews" :key="review.id" class="review-entry">
        <header>
          <div class="reviewer-mark">{{ initials(review.userName) }}</div>
          <div>
            <strong>{{ review.userName || "匿名读者" }}</strong>
            <button type="button" @click="openBook(review)">《{{ review.bookName || "未命名图书" }}》</button>
          </div>
          <el-rate :model-value="review.rating" disabled size="small" />
        </header>
        <p>{{ review.content }}</p>
        <footer>
          <time>{{ review.createTime }}</time>
          <div>
            <button :class="{ active: review.liked }" type="button" @click="toggleLike(review)">
              {{ review.liked ? "已点赞" : "点赞" }} · {{ review.likeCount || 0 }}
            </button>
            <button type="button" @click="toggleReply(review)">回复</button>
            <button class="report" type="button" :disabled="review.reported" @click="report(review)">
              {{ review.reported ? "已举报" : "举报" }}
            </button>
          </div>
        </footer>

        <div v-if="review.replies && review.replies.length" class="reply-ink">
          <p v-for="reply in review.replies" :key="reply.id">
            <strong>{{ reply.userName || "匿名读者" }}</strong>
            <span v-if="reply.replyToUserName"> 回复 {{ reply.replyToUserName }}</span>
            ：{{ reply.content }}
          </p>
        </div>
        <div v-if="replyId === review.id" class="reply-row">
          <el-input v-model="replyDrafts[review.id]" maxlength="500" placeholder="写下回复" @keyup.enter="submitReply(review)" />
          <el-button class="warm-button" @click="submitReply(review)">发送</el-button>
        </div>
      </article>

      <el-empty v-if="!loading && !reviews.length" description="回廊里还没有留下墨迹" />
      <el-pagination
        v-if="total > 0"
        class="review-pager"
        layout="total, prev, pager, next"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        @current-change="fetchReviews"
      />
    </div>
  </section>
</template>

<script>
export default {
  name: "BookReviews",
  data() {
    return {
      sortBy: "latest",
      reviews: [],
      loading: false,
      currentPage: 1,
      pageSize: 10,
      total: 0,
      replyId: null,
      replyDrafts: {},
      reviewRequestId: 0,
    };
  },
  created() {
    this.fetchReviews(1);
  },
  beforeUnmount() {
    this.reviewRequestId += 1;
  },
  methods: {
    initials(name) {
      return (name || "读").slice(0, 1);
    },
    async fetchReviews(page = this.currentPage) {
      const requestId = ++this.reviewRequestId;
      this.currentPage = page;
      this.loading = true;
      try {
        const response = await this.$axios.post("/bookReview/query", {
          current: this.currentPage,
          size: this.pageSize,
          sortBy: this.sortBy,
        });
        if (requestId !== this.reviewRequestId) return;
        if (response.data.code === 200) {
          this.reviews = response.data.data || [];
          this.total = response.data.total || 0;
        } else {
          this.$message.error(response.data.msg);
        }
      } catch (error) {
        if (requestId !== this.reviewRequestId) return;
        console.error("书评回廊加载失败:", error);
        this.$message.error("书评加载失败。");
      } finally {
        if (requestId === this.reviewRequestId) this.loading = false;
      }
    },
    openBook(review) {
      this.$router.push({ path: "/bookSearch", query: { name: review.bookName, open: String(review.bookId) } });
    },
    async toggleLike(review) {
      try {
        const response = await this.$axios.post(`/bookReview/like/${review.id}`);
        if (response.data.code === 200) {
          review.liked = response.data.data;
          review.likeCount = Math.max(0, (review.likeCount || 0) + (review.liked ? 1 : -1));
        } else this.$message.error(response.data.msg);
      } catch {
        this.$message.error("点赞失败。");
      }
    },
    toggleReply(review) {
      this.replyId = this.replyId === review.id ? null : review.id;
      if (this.replyDrafts[review.id] === undefined) this.replyDrafts[review.id] = "";
    },
    async submitReply(review) {
      const content = (this.replyDrafts[review.id] || "").trim();
      if (!content) return this.$message.warning("回复内容不能为空。");
      try {
        const response = await this.$axios.post(`/bookReview/reply/${review.id}`, {
          content,
          replyToUserId: review.userId,
        });
        if (response.data.code === 200) {
          this.replyDrafts[review.id] = "";
          this.replyId = null;
          this.fetchReviews(this.currentPage);
        } else this.$message.error(response.data.msg);
      } catch {
        this.$message.error("回复失败。");
      }
    },
    async report(review) {
      const result = await this.$swal.fire({
        title: "举报书评",
        input: "textarea",
        inputPlaceholder: "请简要说明原因",
        showCancelButton: true,
        confirmButtonText: "提交举报",
        cancelButtonText: "取消",
        inputValidator: (value) => (!value || !value.trim() ? "请填写举报原因" : undefined),
      });
      if (!result.value) return;
      try {
        const response = await this.$axios.post(`/bookReview/report/${review.id}`, { reason: result.value.trim() });
        if (response.data.code === 200) {
          review.reported = true;
          review.reportCount = (review.reportCount || 0) + 1;
          this.$message.success(response.data.msg);
        } else this.$message.error(response.data.msg);
      } catch {
        this.$message.error("举报提交失败。");
      }
    },
  },
};
</script>

<style scoped lang="scss">
.review-title { min-height: 190px; display: grid; align-content: center; }
.review-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.review-toolbar > button { border: 0; color: var(--room-text); background: transparent; cursor: pointer; }
.review-hall { padding: clamp(20px, 4vw, 42px); }
.review-entry { padding: 24px 0; border-bottom: 1px solid color-mix(in srgb, var(--ink) 12%, transparent); }
.review-entry:first-child { padding-top: 0; }
.review-entry > header { display: grid; grid-template-columns: 42px 1fr auto; align-items: center; gap: 12px; }
.reviewer-mark { width: 42px; height: 42px; display: grid; place-items: center; border: 1px solid color-mix(in srgb, var(--seal) 40%, transparent); border-radius: 50%; color: var(--seal); font-family: var(--reader-serif); }
.review-entry header strong, .review-entry header button { display: block; }
.review-entry header strong { color: var(--ink); }
.review-entry header button { margin-top: 3px; padding: 0; border: 0; color: var(--jade); background: transparent; cursor: pointer; }
.review-entry > p { margin: 18px 0 16px 54px; color: var(--ink); font: 400 16px/1.9 var(--reader-serif); }
.review-entry > footer { display: flex; justify-content: space-between; gap: 14px; margin-left: 54px; }
.review-entry time { color: var(--ink-faint); font-size: 11px; }
.review-entry footer button { margin-left: 14px; padding: 0; border: 0; color: var(--ink-soft); background: transparent; cursor: pointer; }
.review-entry footer button.active { color: var(--seal); }
.review-entry footer button.report { color: var(--seal); }
.reply-ink { margin: 16px 0 0 54px; padding: 12px 16px; border-left: 2px solid color-mix(in srgb, var(--jade) 55%, transparent); background: color-mix(in srgb, var(--jade) 7%, transparent); }
.reply-ink p { margin: 5px 0; color: var(--ink-soft); font-size: 13px; line-height: 1.7; }
.reply-row { display: grid; grid-template-columns: 1fr auto; gap: 10px; margin: 14px 0 0 54px; }
.review-pager { margin-top: 24px; justify-content: flex-end; }
@media (max-width: 620px) {
  .review-toolbar { align-items: stretch; flex-direction: column; }
  .review-entry > header { grid-template-columns: 40px 1fr; }
  .review-entry > header :deep(.el-rate) { grid-column: 2; }
  .review-entry > p, .review-entry > footer, .reply-ink, .reply-row { margin-left: 0; }
  .review-entry > footer { align-items: flex-start; flex-direction: column; }
}
</style>
