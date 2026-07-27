<template>
  <section
    class="reader-page"
    :class="{ 'review-dialog-open': reviewDialog }"
  >
    <div class="page-hero">
      <div>
        <p class="eyebrow">THE CATALOGUE DESK</p>
        <h1>检索台</h1>
        <p>按书名、作者或分类寻找图书。打开书页后，再决定借阅、预约或收藏。</p>
      </div>
      <div class="hero-stats">
        <span>{{ totalItems }}</span>
        <em>本书可检索</em>
      </div>
    </div>

    <div class="search-panel">
      <el-input
        v-model.trim="queryDto.name"
        size="large"
        placeholder="书名"
        clearable
        @clear="handleFilter"
        @keyup.enter="handleFilter"
      />
      <el-input
        v-model.trim="queryDto.author"
        size="large"
        placeholder="作者"
        clearable
        @clear="handleFilter"
        @keyup.enter="handleFilter"
      />
      <el-select
        v-model="queryDto.category"
        size="large"
        placeholder="分类"
        clearable
        filterable
        @clear="handleFilter"
        @change="handleFilter"
      >
        <el-option
          v-for="cat in categories"
          :key="cat.id"
          :label="cat.name"
          :value="cat.name"
        />
      </el-select>
      <el-button size="large" class="warm-button" @click="handleFilter">
        搜索
      </el-button>
      <el-button size="large" class="ghost-button" @click="resetCondition">
        重置
      </el-button>
    </div>

    <div v-loading="bookLoading" class="book-grid">
      <article v-for="book in bookTableData" :key="book.id" class="book-card">
        <div class="cover" :style="coverTransitionStyle(book)">
          <el-image
            v-if="book.cover"
            :src="getImageUrl(book.cover)"
            fit="cover"
            lazy
          >
            <template #error><span>无封面</span></template>
          </el-image>
          <span v-else>无封面</span>
        </div>
        <div class="book-info">
          <div class="book-title-row">
            <h2>{{ book.name || "未命名图书" }}</h2>
            <span :class="['stock', book.availableCount > 0 ? 'ok' : 'wait']">
              {{ book.availableCount > 0 ? `可借 ${book.availableCount}` : "需预约" }}
            </span>
          </div>
          <p class="meta">
            {{ book.author || "未知作者" }} · {{ book.category || "未分类" }}
          </p>
          <p class="publisher">{{ book.publisher || "出版社未填写" }}</p>
          <p class="desc">{{ book.description || "这盏灯还没有留下简介。" }}</p>
          <div class="actions">
            <el-button
              v-if="book._borrowed"
              class="reader-action reader-action--state"
              size="small"
              disabled
            >
              借阅中
            </el-button>
            <el-button
              v-else-if="book.availableCount > 0"
              class="reader-action reader-action--primary"
              size="small"
              @click="handleBorrow(book)"
            >
              借阅
            </el-button>
            <el-button
              v-else
              size="small"
              :class="[
                'reader-action',
                book._reserved ? 'reader-action--state' : 'reader-action--reserve',
              ]"
              :disabled="book._reserved"
              @click="handleReserve(book)"
            >
              {{ book._reserved ? "已预约" : "预约" }}
            </el-button>
            <el-button
              size="small"
              :class="[
                'reader-action',
                'reader-action--favorite',
                { 'is-active': book._favorited },
              ]"
              @click="handleFavorite(book)"
            >
              {{ book._favorited ? "已收藏" : "收藏" }}
            </el-button>
            <el-button
              size="small"
              class="reader-action reader-action--quiet"
              @click="openReviewDialog(book)"
            >
              开卷
            </el-button>
          </div>
        </div>
      </article>

      <el-empty
        v-if="!bookLoading && bookTableData.length === 0"
        description="这里暂时没有与你同频的灯。"
      />
    </div>

    <el-pagination
      class="pager"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      :current-page="currentPage"
      :page-sizes="[6, 10, 18]"
      :page-size="pageSize"
      layout="total, sizes, prev, pager, next"
      :total="totalItems"
    />

    <el-dialog
      v-model="reviewDialog"
      :teleported="false"
      :title="selectedBook ? `开卷 · ${selectedBook.name}` : '图书详情'"
      width="min(920px, calc(100vw - 28px))"
      class="review-dialog"
      @closed="clearOpenQuery"
    >
      <section v-if="selectedBook" class="book-detail-sheet">
        <div class="detail-cover" :style="coverTransitionStyle(selectedBook, true)">
          <el-image v-if="selectedBook.cover" :src="getImageUrl(selectedBook.cover)" fit="cover">
            <template #error><span>{{ (selectedBook.name || "书").slice(0, 4) }}</span></template>
          </el-image>
          <span v-else>{{ (selectedBook.name || "书").slice(0, 4) }}</span>
        </div>
        <div class="detail-copy">
          <p class="detail-category">{{ selectedBook.category || "未分类" }}</p>
          <h2>{{ selectedBook.name }}</h2>
          <p class="detail-meta">
            {{ selectedBook.author || "未知作者" }} · {{ selectedBook.publisher || "出版社未填写" }}
          </p>
          <p class="detail-isbn">ISBN {{ selectedBook.isbn || "未填写" }}</p>
          <p class="detail-description">{{ selectedBook.description || "这本书还没有留下简介。" }}</p>
          <div class="detail-stock">
            <strong>{{ selectedBook.availableCount || 0 }}</strong>
            <span>可借 / 馆藏 {{ selectedBook.totalCount || 0 }}</span>
          </div>
          <div class="detail-actions">
            <el-button
              v-if="selectedBook._borrowed"
              class="reader-action reader-action--state"
              disabled
            >借阅中</el-button>
            <el-button
              v-else-if="selectedBook.availableCount > 0"
              class="reader-action reader-action--primary"
              @click="handleBorrow(selectedBook)"
            >借阅</el-button>
            <el-button
              v-else
              :class="[
                'reader-action',
                selectedBook._reserved ? 'reader-action--state' : 'reader-action--reserve',
              ]"
              :disabled="selectedBook._reserved"
              @click="handleReserve(selectedBook)"
            >{{ selectedBook._reserved ? "已预约" : "预约" }}</el-button>
            <el-button
              :class="[
                'reader-action',
                'reader-action--favorite',
                { 'is-active': selectedBook._favorited },
              ]"
              @click="handleFavorite(selectedBook)"
            >
              {{ selectedBook._favorited ? "已收藏" : "收藏" }}
            </el-button>
          </div>
        </div>
      </section>

      <div class="review-composer">
        <div class="rating-row">
          <span>评分</span>
          <el-rate v-model="reviewForm.rating" />
        </div>
        <el-input
          v-model="reviewForm.content"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 5 }"
          maxlength="1000"
          show-word-limit
          placeholder="写下你对这本书的感受。"
        />
        <div class="composer-actions">
          <el-radio-group
            v-model="reviewSort"
            class="reader-segmented"
            size="small"
            @change="handleReviewSortChange"
          >
            <el-radio-button value="latest">最新</el-radio-button>
            <el-radio-button value="hot">最热</el-radio-button>
          </el-radio-group>
          <el-button class="warm-button" @click="submitReview">发表书评</el-button>
        </div>
      </div>

      <div v-loading="reviewLoading" class="review-list">
        <article v-for="review in reviewList" :key="review.id" class="review-item">
          <div class="review-head">
            <div>
              <strong>{{ review.userName || "匿名读者" }}</strong>
              <span>{{ review.createTime }}</span>
            </div>
            <el-rate :model-value="review.rating" disabled size="small" />
          </div>
          <p>{{ review.content }}</p>
          <div class="review-actions">
            <button type="button" :class="{ liked: review.liked }" @click="toggleReviewLike(review)">
              {{ review.liked ? "已点赞" : "点赞" }} · {{ review.likeCount || 0 }}
            </button>
            <button type="button" @click="toggleReplyBox(review)">
              回复
            </button>
            <button
              type="button"
              class="danger-link"
              :disabled="review.reported"
              @click="reportReview(review)"
            >
              {{ review.reported ? "已举报" : "举报" }}<span v-if="review.reportCount"> · {{ review.reportCount }}</span>
            </button>
          </div>

          <div v-if="review.replies && review.replies.length" class="reply-list">
            <div v-for="reply in review.replies" :key="reply.id" class="reply-item">
              <strong>{{ reply.userName || "匿名读者" }}</strong>
              <span v-if="reply.replyToUserName"> 回复 {{ reply.replyToUserName }}</span>
              <em>{{ reply.createTime }}</em>
              <p>{{ reply.content }}</p>
            </div>
          </div>

          <div v-if="replyBoxId === review.id" class="reply-composer">
            <el-input
              v-model="replyDrafts[review.id]"
              size="small"
              placeholder="回复这条书评"
              maxlength="500"
              show-word-limit
              @keyup.enter="submitReply(review)"
            />
            <el-button size="small" class="warm-button" @click="submitReply(review)">
              发送
            </el-button>
          </div>
        </article>

        <el-empty
          v-if="!reviewLoading && reviewList.length === 0"
          description="还没有书评，写下第一句吧。"
        />
      </div>

      <el-pagination
        v-if="reviewTotal > reviewPageSize"
        class="review-pager"
        layout="total, prev, pager, next"
        :current-page="reviewCurrentPage"
        :page-size="reviewPageSize"
        :total="reviewTotal"
        @current-change="fetchReviews"
      />
    </el-dialog>
  </section>
</template>

<script>
import { resolveFileUrl } from "@/utils/fileUrl.js";
import { runViewTransition } from "@/utils/viewTransition.js";

export default {
  name: "BookBorrow",
  data() {
    return {
      bookTableData: [],
      bookLoading: false,
      queryDto: {},
      categories: [],
      currentPage: 1,
      pageSize: 6,
      totalItems: 0,
      selectedBook: null,
      reviewDialog: false,
      detailOpening: false,
      transitionBookId: null,
      reviewLoading: false,
      reviewList: [],
      reviewTotal: 0,
      reviewCurrentPage: 1,
      reviewPageSize: 5,
      reviewSort: "latest",
      reviewForm: {
        rating: 5,
        content: "",
      },
      replyBoxId: null,
      replyDrafts: {},
    };
  },
  created() {
    this.queryDto = {
      name: this.$route.query.name || "",
      author: this.$route.query.author || "",
      category: this.$route.query.category || "",
    };
    this.fetchCategories();
    this.fetchBooks();
  },
  methods: {
    getImageUrl(url) {
      return resolveFileUrl(url);
    },
    coverTransitionStyle(book, isDetail = false) {
      if (!book || this.transitionBookId !== book.id) return undefined;
      if (isDetail !== this.reviewDialog) return undefined;
      return { viewTransitionName: "reader-book-cover" };
    },
    async fetchBooks() {
      this.bookLoading = true;
      try {
        const [response, borrowResponse, reservationResponse] = await Promise.all([
          this.$axios.post("/book/query", {
            current: this.currentPage,
            size: this.pageSize,
            ...this.queryDto,
          }),
          this.$axios.post("/borrowRecord/query", {
            current: 1,
            size: 100,
            status: false,
          }),
          this.$axios.post("/bookReservation/query", {
            current: 1,
            size: 100,
          }),
        ]);
        const { data } = response;
        if (data.code === 200) {
          const books = data.data || [];
          const activeBorrowIds = new Set(
            (borrowResponse.data.data || []).map((record) => record.bookId)
          );
          const activeReservationIds = new Set(
            (reservationResponse.data.data || [])
              .filter((reservation) => [0, 3].includes(reservation.status))
              .map((reservation) => reservation.bookId)
          );
          await Promise.all(
            books.map(async (book) => {
              book._borrowed = activeBorrowIds.has(book.id);
              book._reserved = activeReservationIds.has(book.id);
              try {
                const favRes = await this.$axios.get(
                  `/bookFavorite/isFavorited/${book.id}`
                );
                book._favorited =
                  favRes.data.code === 200 ? favRes.data.data : false;
              } catch {
                book._favorited = false;
              }
            })
          );
          this.bookTableData = books;
          this.totalItems = data.total || books.length;
          const openId = Number(this.$route.query.open);
          if (openId && !this.reviewDialog) {
            const selected = books.find((book) => book.id === openId);
            if (selected) this.openReviewDialog(selected);
          }
        }
      } catch (error) {
        console.error("查询图书失败:", error);
        this.$message.error("图书加载失败，请稍后重试。");
      } finally {
        this.bookLoading = false;
      }
    },
    async fetchCategories() {
      try {
        const response = await this.$axios.get("/category/queryAll");
        if (response.data.code === 200) {
          this.categories = response.data.data || [];
        }
      } catch (error) {
        console.error("查询分类失败:", error);
      }
    },
    async handleBorrow(row) {
      const confirmed = await this.$swal.fire({
        title: "确认借阅",
        text: `确认借阅《${row.name}》？`,
        icon: "question",
        showCancelButton: true,
        confirmButtonText: "确认",
        cancelButtonText: "取消",
      });
      if (!confirmed.value) return;

      try {
        const response = await this.$axios.post(`/borrowRecord/borrow/${row.id}`);
        if (response.data.code === 200) {
          this.$swal.fire({
            title: "这盏灯，暂由你照看",
            text: response.data.msg,
            icon: "success",
            showConfirmButton: false,
            timer: 1500,
          });
          this.fetchBooks();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("借阅请求失败。");
      }
    },
    async handleReserve(row) {
      try {
        const response = await this.$axios.post(`/bookReservation/reserve/${row.id}`);
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.fetchBooks();
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("预约操作失败。");
      }
    },
    async handleFavorite(row) {
      const url = row._favorited
        ? `/bookFavorite/remove/${row.id}`
        : `/bookFavorite/add/${row.id}`;
      try {
        const response = await this.$axios.post(url);
        if (response.data.code === 200) {
          row._favorited = !row._favorited;
          this.$message.success(row._favorited ? "收藏成功。" : "已取消收藏。");
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("收藏操作失败。");
      }
    },
    async openReviewDialog(book) {
      if (this.detailOpening) return;
      this.detailOpening = true;
      this.transitionBookId = book.id;
      await this.$nextTick();

      try {
        await runViewTransition(async () => {
          this.prepareReviewDialog(book);
          await this.$nextTick();
        });
      } finally {
        this.transitionBookId = null;
        this.detailOpening = false;
      }
    },
    prepareReviewDialog(book) {
      this.selectedBook = book;
      this.reviewDialog = true;
      this.$router.replace({
        path: "/bookSearch",
        query: { ...this.$route.query, open: String(book.id) },
      });
      this.reviewSort = "latest";
      this.reviewForm = {
        rating: 5,
        content: "",
      };
      this.replyBoxId = null;
      this.replyDrafts = {};
      this.fetchReviews(1);
    },
    clearOpenQuery() {
      if (!this.$route.query.open) return;
      const query = { ...this.$route.query };
      delete query.open;
      this.$router.replace({ path: "/bookSearch", query });
    },
    async fetchReviews(page = this.reviewCurrentPage) {
      if (!this.selectedBook) return;
      this.reviewCurrentPage = page;
      this.reviewLoading = true;
      try {
        const response = await this.$axios.post("/bookReview/query", {
          current: this.reviewCurrentPage,
          size: this.reviewPageSize,
          bookId: this.selectedBook.id,
          sortBy: this.reviewSort,
        });
        const { data } = response;
        if (data.code === 200) {
          this.reviewList = data.data || [];
          this.reviewTotal = data.total || 0;
        } else {
          this.$message.error(data.msg);
        }
      } catch (error) {
        console.error("书评加载失败:", error);
        this.$message.error("书评加载失败。");
      } finally {
        this.reviewLoading = false;
      }
    },
    handleReviewSortChange() {
      this.fetchReviews(1);
    },
    async submitReview() {
      if (!this.selectedBook) return;
      if (!this.reviewForm.content.trim()) {
        this.$message.warning("请先写下书评内容。");
        return;
      }
      try {
        const response = await this.$axios.post("/bookReview/save", {
          bookId: this.selectedBook.id,
          rating: this.reviewForm.rating,
          content: this.reviewForm.content.trim(),
        });
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.reviewForm.content = "";
          this.fetchReviews(1);
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("书评发表失败。");
      }
    },
    async toggleReviewLike(review) {
      try {
        const response = await this.$axios.post(`/bookReview/like/${review.id}`);
        if (response.data.code === 200) {
          const liked = Boolean(response.data.data);
          review.liked = liked;
          review.likeCount = Math.max(0, (review.likeCount || 0) + (liked ? 1 : -1));
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("点赞操作失败。");
      }
    },
    async reportReview(review) {
      if (review.reported) return;
      const result = await this.$swal.fire({
        title: "举报书评",
        input: "textarea",
        inputPlaceholder: "请简要说明原因，管理员会进行审核。",
        inputAttributes: {
          maxlength: 200,
        },
        showCancelButton: true,
        confirmButtonText: "提交",
        cancelButtonText: "取消",
        inputValidator: (value) => {
          if (!value || !value.trim()) {
            return "请填写举报原因。";
          }
          if (value.trim().length > 200) {
            return "举报原因不能超过200字。";
          }
          return undefined;
        },
      });
      if (!result.value) return;
      try {
        const response = await this.$axios.post(`/bookReview/report/${review.id}`, {
          reason: result.value.trim(),
        });
        if (response.data.code === 200) {
          review.reported = true;
          review.reportCount = (review.reportCount || 0) + 1;
          this.$message.success(response.data.msg);
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("举报提交失败。");
      }
    },
    toggleReplyBox(review) {
      this.replyBoxId = this.replyBoxId === review.id ? null : review.id;
      if (this.replyBoxId && this.replyDrafts[review.id] === undefined) {
        this.replyDrafts[review.id] = "";
      }
    },
    async submitReply(review) {
      const content = (this.replyDrafts[review.id] || "").trim();
      if (!content) {
        this.$message.warning("回复内容不能为空。");
        return;
      }
      try {
        const response = await this.$axios.post(`/bookReview/reply/${review.id}`, {
          content,
          replyToUserId: review.userId,
        });
        if (response.data.code === 200) {
          this.$message.success(response.data.msg);
          this.replyDrafts[review.id] = "";
          this.replyBoxId = null;
          this.fetchReviews(this.reviewCurrentPage);
        } else {
          this.$message.error(response.data.msg);
        }
      } catch {
        this.$message.error("回复失败。");
      }
    },
    handleFilter() {
      this.currentPage = 1;
      this.syncSearchRoute();
      this.fetchBooks();
    },
    resetCondition() {
      this.queryDto = {};
      this.currentPage = 1;
      this.$router.replace({ path: "/bookSearch" });
      this.fetchBooks();
    },
    syncSearchRoute() {
      const query = {};
      if (this.queryDto.name) query.name = this.queryDto.name;
      if (this.queryDto.author) query.author = this.queryDto.author;
      if (this.queryDto.category) query.category = this.queryDto.category;
      this.$router.replace({ path: "/bookSearch", query });
    },
    handleSizeChange(val) {
      this.pageSize = val;
      this.currentPage = 1;
      this.fetchBooks();
    },
    handleCurrentChange(val) {
      this.currentPage = val;
      this.fetchBooks();
    },
  },
};
</script>

<style scoped lang="scss">
.reader-page {
  display: grid;
  gap: 22px;
}

.page-hero,
.search-panel,
.borrow-panel,
.book-card {
  border: 1px solid rgba(229, 185, 121, 0.24);
  border-radius: 8px;
  background: rgba(28, 24, 19, 0.92);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.18);
}

.page-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 30px;

  h1 {
    margin: 6px 0 10px;
    color: #fff5df;
    font-size: clamp(34px, 4vw, 58px);
    letter-spacing: 0;
  }

  p {
    max-width: 720px;
    margin: 0;
    color: rgba(255, 245, 223, 0.9);
    line-height: 1.8;
  }
}

.eyebrow {
  margin: 0;
  color: #d0a15e;
  font-size: 12px;
  font-weight: 700;
}

.hero-stats {
  min-width: 120px;
  align-self: end;
  text-align: right;

  span,
  em {
    display: block;
  }

  span {
    color: #f4cf88;
    font-size: 42px;
    font-weight: 800;
  }

  em {
    color: rgba(255, 245, 223, 0.78);
    font-style: normal;
  }
}

.search-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr)) auto auto;
  gap: 12px;
  padding: 16px;
}

.book-grid {
  min-height: 260px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.book-card {
  display: grid;
  grid-template-columns: 108px 1fr;
  gap: 18px;
  padding: 16px;
  transition: transform 0.28s ease, border-color 0.28s ease, box-shadow 0.28s ease;

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(229, 185, 121, 0.34);
    box-shadow: 0 22px 56px rgba(0, 0, 0, 0.28);
  }
}

.cover {
  width: 108px;
  aspect-ratio: 3 / 4;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 6px;
  color: rgba(239, 229, 213, 0.52);
  background: linear-gradient(145deg, #30271c, #17130f);
  contain: paint;

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }

  :deep(.el-image__error) {
    color: inherit;
    background: transparent;
  }
}

.book-info {
  min-width: 0;
}

.book-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;

  h2 {
    margin: 0;
    color: #fff3da;
    font-size: 18px;
    line-height: 1.35;
  }
}

.stock {
  flex: 0 0 auto;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;

  &.ok {
    color: #29483e;
    border: 1px solid rgba(65, 105, 88, 0.28);
    background: rgba(88, 119, 109, 0.14);
  }

  &.wait {
    color: #70471f;
    border: 1px solid rgba(143, 94, 43, 0.25);
    background: rgba(181, 138, 80, 0.16);
  }
}

.meta,
.publisher,
.desc {
  margin: 8px 0 0;
  color: rgba(255, 245, 223, 0.84);
  line-height: 1.65;
}

.publisher {
  font-size: 13px;
}

.desc {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.warm-button {
  border: 0;
  color: #251a10;
  background: #d8a45f;
  font-weight: 700;

  &:hover,
  &:focus {
    color: #1d140b;
    background: #e5b979;
  }
}

.ghost-button {
  color: rgba(255, 245, 223, 0.92);
  border-color: rgba(255, 245, 223, 0.24);
  background: rgba(255, 255, 255, 0.08);
}

.pager {
  align-self: end;
  justify-self: end;
}

.borrow-panel {
  padding: 18px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;

  h2 {
    margin: 4px 0 0;
    color: #fff3da;
  }
}

.overdue {
  color: #ff9b8f;
}

.muted {
  color: rgba(255, 245, 223, 0.72);
}

.review-composer,
.review-item {
  border: 1px solid rgba(229, 185, 121, 0.18);
  border-radius: 8px;
  background: #241f19;
}

.review-composer {
  display: grid;
  gap: 12px;
  padding: 14px;
  margin-bottom: 14px;
}

.rating-row,
.composer-actions,
.review-head,
.review-actions,
.reply-composer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rating-row {
  color: #f8ead4;
}

.composer-actions {
  justify-content: space-between;
}

.review-list {
  display: grid;
  gap: 12px;
  min-height: 160px;
}

.review-item {
  padding: 14px;

  p {
    margin: 10px 0;
    color: rgba(255, 245, 223, 0.9);
    line-height: 1.75;
  }
}

.review-head {
  justify-content: space-between;

  strong,
  span {
    display: block;
  }

  strong {
    color: #fff3da;
  }

  span {
    margin-top: 3px;
    color: rgba(255, 245, 223, 0.64);
    font-size: 12px;
  }
}

.review-actions button {
  border: 0;
  padding: 0;
  color: rgba(255, 245, 223, 0.78);
  background: transparent;
  cursor: pointer;

  &.liked,
  &:hover {
    color: #f3c37a;
  }

  &.danger-link {
    color: rgba(255, 180, 160, 0.88);
  }

  &:disabled {
    color: rgba(255, 245, 223, 0.42);
    cursor: not-allowed;
  }
}

.reply-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding: 10px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
}

.reply-item {
  color: rgba(255, 245, 223, 0.86);

  strong {
    color: #f4cf88;
  }

  span,
  em {
    margin-left: 6px;
    color: rgba(255, 245, 223, 0.62);
    font-size: 12px;
    font-style: normal;
  }

  p {
    margin: 4px 0 0;
  }
}

.reply-composer {
  margin-top: 10px;
}

.review-pager {
  margin-top: 14px;
  justify-content: flex-end;
}

:global(.reader-shell:has(.review-dialog-open) .reader-stage) {
  z-index: 50;
}

:global(.review-dialog) {
  max-height: min(86vh, 900px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:global(.review-dialog .el-dialog__header) {
  flex: 0 0 auto;
  padding: 18px 22px !important;
}

:global(.review-dialog .el-dialog__body) {
  min-height: 0;
  flex: 1 1 auto;
  padding: 22px !important;
  overflow-y: auto;
}

.book-detail-sheet {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 28px;
  margin-bottom: 24px;
  padding-bottom: 24px;
  border-bottom: 1px solid color-mix(in srgb, var(--ink) 13%, transparent);
}

.detail-cover {
  width: 180px;
  aspect-ratio: 3 / 4;
  display: grid;
  place-items: center;
  overflow: hidden;
  color: var(--ink-soft);
  border: 1px solid color-mix(in srgb, var(--ink) 16%, transparent);
  background: var(--paper-soft);
  box-shadow: 8px 12px 28px color-mix(in srgb, var(--ink) 15%, transparent);
  font: 500 22px/1.6 var(--reader-serif);
  text-align: center;
  contain: paint;

  :deep(.el-image) { width: 100%; height: 100%; }
  :deep(.el-image__error) { color: inherit; background: transparent; }
}

.detail-copy h2 {
  margin: 4px 0 8px;
  color: var(--ink);
  font: 500 30px/1.3 var(--reader-serif);
}

.detail-category,
.detail-isbn {
  color: var(--seal);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.detail-meta,
.detail-description {
  color: var(--ink-soft);
}

.detail-description { max-width: 640px; line-height: 1.8; }
.detail-stock { display: flex; align-items: end; gap: 8px; margin: 16px 0; }
.detail-stock strong { color: var(--jade); font: 500 30px var(--reader-serif); }
.detail-stock span { padding-bottom: 4px; color: var(--ink-faint); font-size: 12px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 8px; }

.review-composer,
.review-item {
  border-color: color-mix(in srgb, var(--ink) 14%, transparent);
  color: var(--ink);
  background: var(--paper-raised);
}

.rating-row,
.review-head strong,
.review-item p,
.reply-item {
  color: var(--ink);
}

.review-head span,
.review-actions button,
.reply-item span,
.reply-item em {
  color: var(--ink-soft);
}

.reply-list { background: color-mix(in srgb, var(--jade) 7%, transparent); }
.reply-item strong { color: var(--jade); }

.review-composer,
.review-item {
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.review-composer {
  margin-bottom: 18px;
  padding: 18px 0;
  border: 0;
  border-top: 1px solid color-mix(in srgb, var(--ink) 11%, transparent);
  border-bottom: 1px solid color-mix(in srgb, var(--ink) 11%, transparent);
}

.review-item {
  padding: 16px 0;
  border: 0;
  border-bottom: 1px solid color-mix(in srgb, var(--ink) 11%, transparent);
}

.review-list :deep(.el-empty) { padding: 46px 0 34px; }
.review-list :deep(.el-empty__image) { display: none; }

.book-card {
  border-color: color-mix(in srgb, var(--ink) 14%, transparent);
  color: var(--ink);
  background: var(--paper);
  box-shadow: 0 14px 36px color-mix(in srgb, var(--ink) 12%, transparent);
}

.book-card:hover {
  border-color: color-mix(in srgb, var(--light) 48%, transparent);
  background: var(--paper-raised);
}

.cover {
  color: var(--ink-faint);
  border-color: color-mix(in srgb, var(--ink) 12%, transparent);
  background: var(--paper-soft);
}

.book-title-row h2,
.publisher,
.desc {
  color: var(--ink);
}

.meta { color: var(--ink-soft); }
.publisher { color: var(--ink-soft); }
.desc { color: var(--ink-soft); }
.hero-stats span { color: var(--light); }
.hero-stats em { color: var(--ink-soft); }

@media (max-width: 620px) {
  :global(.review-dialog) {
    max-height: calc(100vh - 24px);
    margin-top: 12px !important;
    margin-bottom: 12px;
  }

  :global(.review-dialog .el-dialog__header) {
    padding: 16px !important;
  }

  :global(.review-dialog .el-dialog__body) {
    padding: 16px !important;
  }

  .book-detail-sheet { grid-template-columns: 1fr; }
  .detail-cover { width: 132px; }
  .composer-actions { align-items: stretch; flex-direction: column; }
}

@media (max-width: 860px) {
  .page-hero {
    flex-direction: column;
    padding: 22px;
  }

  .hero-stats {
    text-align: left;
  }

  .search-panel {
    grid-template-columns: 1fr;
  }

  .book-card {
    grid-template-columns: 90px 1fr;
  }

  .cover {
    width: 90px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .book-card {
    transition: none;
  }
}
</style>
