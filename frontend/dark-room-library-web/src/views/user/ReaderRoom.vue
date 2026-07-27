<template>
  <section class="room-page" v-loading="loading">
    <div class="room-intro">
      <p>{{ themeLine }}</p>
      <h1>{{ greeting }}，{{ userName || "读者" }}</h1>
      <span>这里有一盏灯，也有尚未翻开的书。</span>
      <button type="button" @click="$router.push('/bookSearch')">
        <SearchIcon />
        <strong>查找藏书</strong>
      </button>
    </div>

    <aside class="quiet-status" aria-label="借阅提醒">
      <button v-if="dueSoonCount" type="button" @click="$router.push('/myBorrows')">
        <strong>{{ dueSoonCount }} 册</strong><span>将在三日内到期</span>
      </button>
      <button v-if="notifiedCount" type="button" @click="$router.push('/myReservations')">
        <strong>{{ notifiedCount }} 册</strong><span>预约已经到馆</span>
      </button>
      <button v-if="!dueSoonCount && !notifiedCount && activeBorrowCount" type="button" @click="$router.push('/myBorrows')">
        <strong>{{ activeBorrowCount }} 册</strong><span>正在你的案头</span>
      </button>
    </aside>

    <section v-if="recommendationItems.length" class="desk-selection">
      <header>
        <span>{{ recommendationTitle }}</span>
        <span class="selection-actions">
          <button type="button" title="推荐说明与设置" @click="openRecommendationSettings">
            <InfoFilled />
          </button>
          <button type="button" @click="$router.push('/bookSearch')">全部藏书</button>
        </span>
      </header>
      <TransitionGroup name="desk-book" tag="div" class="desk-books">
        <article
          v-for="book in recommendationItems"
          :key="book.itemId"
          class="desk-book"
        >
          <button type="button" class="desk-book-main" @click="openBook(book)">
            <span class="book-cover">
              <el-image v-if="book.cover" :src="fileUrl(book.cover)" fit="cover" lazy>
                <template #error><i>{{ shortTitle(book.name) }}</i></template>
              </el-image>
              <i v-else>{{ shortTitle(book.name) }}</i>
            </span>
            <span class="book-copy">
              <strong>{{ book.name }}</strong>
              <em>{{ book.author || "佚名" }}</em>
              <small>{{ book.reason }}</small>
            </span>
          </button>
          <button
            v-if="typeof book.itemId === 'number'"
            type="button"
            class="dismiss-recommendation"
            :disabled="dismissingRecommendationIds.length > 0"
            :aria-label="`不再推荐《${book.name}》`"
            title="对此书不感兴趣"
            @click.stop="dismissRecommendation(book)"
          >
            <Close />
          </button>
        </article>
      </TransitionGroup>
    </section>

    <nav v-if="categories.length" class="category-whisper" aria-label="图书分类">
      <button v-for="category in categories.slice(0, 5)" :key="category.id" type="button" @click="openCategory(category.name)">
        {{ category.name }}
      </button>
    </nav>

    <el-dialog
      v-model="recommendationSettingsVisible"
      title="沿着书签"
      class="reader-recommendation-dialog"
      width="min(500px, calc(100vw - 24px))"
      append-to-body
    >
      <div class="recommendation-setting-copy">
        <p>{{ recommendationSetting.dataScope }}</p>
        <div class="recommendation-setting-row">
          <span>
            <strong>个性化荐书</strong>
            <small>关闭后只展示新近入藏与公共荐书。</small>
          </span>
          <el-switch
            v-model="recommendationSetting.enabled"
            :loading="recommendationSettingSaving"
            @change="updateRecommendationSetting"
          />
        </div>
        <div class="recommendation-setting-row recommendation-history-row">
          <span>
            <strong>推荐记录</strong>
            <small>{{ recommendationSetting.clearEffect }}</small>
          </span>
          <el-button :loading="recommendationHistoryClearing" @click="clearRecommendationHistory">
            清除记录
          </el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { Close, InfoFilled, Search as SearchIcon } from "@element-plus/icons-vue";
import { resolveFileUrl } from "@/utils/fileUrl.js";

export default {
  name: "ReaderRoom",
  components: { Close, InfoFilled, SearchIcon },
  data() {
    return {
      loading: false,
      userId: null,
      userName: "",
      recommendation: {
        mode: "PUBLIC",
        personalized: false,
        enabled: true,
        signalCount: 0,
        items: [],
      },
      recommendationSettingsVisible: false,
      recommendationSettingSaving: false,
      recommendationHistoryClearing: false,
      dismissingRecommendationIds: [],
      recommendationSetting: {
        enabled: true,
        dataScope: "只使用你主动留下的收藏、借阅与评分，不记录无关浏览行为。",
        clearEffect: "清除曝光、点击与计算结果，不删除收藏、借阅或书评。",
      },
      categories: [],
      borrows: [],
      reservations: [],
    };
  },
  computed: {
    greeting() {
      const hour = new Date().getHours();
      if (hour < 6) return "夜深了";
      if (hour < 12) return "早安";
      if (hour < 18) return "午后好";
      return "晚上好";
    },
    themeLine() {
      return new Date().getHours() < 18 ? "山气未散，窗下宜读" : "夜色入室，灯火可亲";
    },
    recommendationItems() {
      return (this.recommendation.items || []).slice(0, 3);
    },
    recommendationTitle() {
      return this.recommendation.personalized ? "沿着书签" : "案上新书";
    },
    activeBorrowCount() {
      return this.borrows.filter((item) => !item.status).length;
    },
    dueSoonCount() {
      const now = Date.now();
      const threeDays = 3 * 24 * 60 * 60 * 1000;
      return this.borrows.filter((item) => {
        if (item.status || !item.dueDate) return false;
        const gap = new Date(item.dueDate).getTime() - now;
        return gap >= 0 && gap <= threeDays;
      }).length;
    },
    notifiedCount() {
      return this.reservations.filter((item) => item.status === 3).length;
    },
  },
  created() {
    this.loadRoom();
  },
  methods: {
    fileUrl(url) {
      return resolveFileUrl(url);
    },
    shortTitle(title) {
      return (title || "书").slice(0, 3);
    },
    async loadRoom() {
      this.loading = true;
      try {
        const auth = await this.$axios.get("/user/auth");
        if (auth.data.code !== 200) return;
        this.userId = auth.data.data.id;
        this.userName = auth.data.data.userName;
        const [recommendation, categories, borrows, reservations] = await Promise.all([
          this.loadRecommendation(),
          this.$axios.get("/category/queryAll"),
          this.$axios.post("/borrowRecord/query", { current: 1, size: 20, userId: this.userId }),
          this.$axios.post("/bookReservation/query", { current: 1, size: 20, userId: this.userId }),
        ]);
        this.recommendation = recommendation;
        if (categories.data.code === 200) this.categories = (categories.data.data || []).slice(0, 8);
        if (borrows.data.code === 200) this.borrows = borrows.data.data || [];
        if (reservations.data.code === 200) this.reservations = reservations.data.data || [];
      } catch (error) {
        console.error("藏书室加载失败:", error);
        this.$message.error("藏书室暂时没有回应，请稍后再试。");
      } finally {
        this.loading = false;
      }
    },
    async loadRecommendation() {
      try {
        const response = await this.$axios.get("/recommendation/feed", { params: { size: 6 } });
        if (response.data.code === 200 && response.data.data) return response.data.data;
      } catch (error) {
        console.warn("个性化荐书暂时不可用，改用公共馆藏:", error);
      }
      const fallback = await this.$axios.post("/book/query", { current: 1, size: 6 });
      const books = fallback.data.code === 200 ? fallback.data.data || [] : [];
      return {
        mode: "PUBLIC",
        personalized: false,
        enabled: false,
        signalCount: 0,
        items: books.map((book) => ({
          itemId: `fallback-${book.id}`,
          bookId: book.id,
          name: book.name,
          author: book.author,
          category: book.category,
          cover: book.cover,
          availableCount: book.availableCount,
          reason: "馆内仍有一册书在等人翻开。",
        })),
      };
    },
    async openBook(book) {
      if (typeof book.itemId === "number") {
        try {
          await this.$axios.post(`/recommendation/items/${book.itemId}/events`, {
            eventType: "CLICK",
          });
        } catch (error) {
          console.warn("推荐点击归因失败，不影响打开图书:", error);
        }
      }
      this.$router.push({
        path: "/bookSearch",
        query: { name: book.name, open: String(book.bookId) },
      });
    },
    async dismissRecommendation(book) {
      if (typeof book.itemId !== "number"
          || this.dismissingRecommendationIds.length > 0) return;
      this.dismissingRecommendationIds.push(book.itemId);
      try {
        const response = await this.$axios.post(`/recommendation/items/${book.itemId}/events`, {
          eventType: "DISMISS",
        });
        if (response.data.code !== 200) throw new Error(response.data.msg || "操作失败");
        this.recommendation.items = (this.recommendation.items || [])
          .filter((item) => item.itemId !== book.itemId);
        this.$message.success(response.data.msg || "已减少此类推荐");
        try {
          this.recommendation = await this.loadRecommendation();
        } catch (refreshError) {
          console.warn("推荐已关闭，但列表补位暂时失败:", refreshError);
        }
      } catch (error) {
        this.$message.error(error.response?.data?.msg || error.message || "操作失败");
      } finally {
        this.dismissingRecommendationIds = this.dismissingRecommendationIds
          .filter((itemId) => itemId !== book.itemId);
      }
    },
    openCategory(category) {
      this.$router.push({ path: "/bookSearch", query: { category } });
    },
    async openRecommendationSettings() {
      this.recommendationSettingsVisible = true;
      try {
        const response = await this.$axios.get("/recommendation/setting");
        if (response.data.code === 200) {
          this.recommendationSetting = response.data.data;
        }
      } catch (error) {
        console.error("推荐设置加载失败:", error);
        this.$message.error("推荐设置暂时无法读取。");
      }
    },
    async updateRecommendationSetting(enabled) {
      this.recommendationSettingSaving = true;
      try {
        const response = await this.$axios.put("/recommendation/setting", { enabled });
        if (response.data.code !== 200) throw new Error(response.data.msg || "设置保存失败");
        this.recommendationSetting = response.data.data;
        this.recommendation = await this.loadRecommendation();
        this.$message.success(response.data.msg);
      } catch (error) {
        this.recommendationSetting.enabled = !enabled;
        this.$message.error(error.response?.data?.msg || error.message || "设置保存失败");
      } finally {
        this.recommendationSettingSaving = false;
      }
    },
    async clearRecommendationHistory() {
      const confirmed = await this.$swalConfirm({
        title: "清除推荐记录？",
        text: "曝光、点击与计算结果会被删除，收藏、借阅和书评不受影响。",
        icon: "warning",
      });
      if (!confirmed) return;
      this.recommendationHistoryClearing = true;
      try {
        const response = await this.$axios.delete("/recommendation/history");
        if (response.data.code !== 200) throw new Error(response.data.msg || "清除失败");
        this.recommendation = await this.loadRecommendation();
        this.$message.success(response.data.msg);
      } catch (error) {
        this.$message.error(error.response?.data?.msg || error.message || "清除失败");
      } finally {
        this.recommendationHistoryClearing = false;
      }
    },
  },
};
</script>

<style scoped lang="scss">
.room-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: var(--scene-text);
}

.room-intro {
  position: absolute;
  top: 16vh;
  left: 50%;
  width: min(520px, calc(100% - 40px));
  text-align: center;
  text-shadow: 0 2px 24px rgba(0, 0, 0, 0.78);
  transform: translateX(-50%);
}

.room-intro > p {
  margin: 0 0 15px;
  font-family: var(--reader-serif);
  font-size: 13px;
  letter-spacing: 0.18em;
  opacity: 0.66;
}

.room-intro h1 {
  margin: 0;
  font-family: var(--reader-serif);
  font-size: clamp(30px, 4vw, 52px);
  font-weight: 400;
  letter-spacing: 0;
}

.room-intro > span {
  display: block;
  margin-top: 13px;
  font-size: 14px;
  opacity: 0.68;
}

.room-intro > button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: 28px;
  padding: 10px 3px 9px;
  border: 0;
  border-bottom: 1px solid currentColor;
  color: inherit;
  background: transparent;
  cursor: pointer;
  opacity: 0.82;
  transition: gap 0.22s ease, opacity 0.22s ease;
}

.room-intro > button:hover { gap: 16px; opacity: 1; }
.room-intro svg { width: 17px; }
.room-intro strong { font-family: var(--reader-serif); font-size: 15px; font-weight: 500; }

.quiet-status {
  position: absolute;
  bottom: 48px;
  left: 42px;
  display: grid;
  gap: 10px;
}

.quiet-status button {
  display: flex;
  align-items: baseline;
  gap: 9px;
  padding: 0;
  border: 0;
  color: var(--scene-text);
  background: transparent;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.9);
  cursor: pointer;
}

.quiet-status strong { font-family: var(--reader-serif); font-size: 20px; font-weight: 400; }
.quiet-status span { font-size: 12px; opacity: 0.65; }

.desk-selection {
  position: absolute;
  right: 76px;
  bottom: 38px;
  width: min(680px, calc(100vw - 150px));
  color: var(--scene-text);
  text-shadow: 0 1px 14px rgba(0, 0, 0, 0.95);
}

.desk-selection header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(238, 233, 221, 0.2);
  font-family: var(--reader-serif);
}

.desk-selection header button {
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  font-size: 11px;
  opacity: 0.6;
  cursor: pointer;
}

.selection-actions { display: flex; align-items: center; gap: 14px; }
.selection-actions button { display: inline-grid; place-items: center; }
.selection-actions svg { width: 14px; height: 14px; }

.desk-books { display: grid; grid-template-columns: repeat(3, 1fr); gap: 18px; }

.desk-book {
  position: relative;
  min-width: 0;
}

.desk-book-main {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px 0 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.dismiss-recommendation {
  position: absolute;
  top: -2px;
  right: 0;
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  opacity: 0.38;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.dismiss-recommendation:hover,
.dismiss-recommendation:focus-visible { opacity: 0.9; transform: scale(1.08); }
.dismiss-recommendation:disabled { cursor: wait; opacity: 0.22; }
.dismiss-recommendation svg { width: 13px; height: 13px; }

.desk-book-move,
.desk-book-enter-active,
.desk-book-leave-active { transition: opacity 0.24s ease, transform 0.24s ease; }
.desk-book-enter-from,
.desk-book-leave-to { opacity: 0; transform: translateY(6px); }

.book-cover {
  width: 38px;
  height: 54px;
  flex: 0 0 38px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: #201a14;
  box-shadow: 2px 5px 12px rgba(0, 0, 0, 0.36);
}

.book-cover :deep(.el-image) { width: 100%; height: 100%; }
.book-cover :deep(.el-image__error) { color: inherit; background: transparent; }
.book-cover i { font-family: var(--reader-serif); font-size: 10px; font-style: normal; }
.book-copy { min-width: 0; }
.book-copy strong,
.book-copy em,
.book-copy small { display: block; overflow: hidden; text-overflow: ellipsis; }
.book-copy strong,
.book-copy em { white-space: nowrap; }
.book-copy strong { font-family: var(--reader-serif); font-size: 13px; font-weight: 500; }
.book-copy em { margin-top: 7px; font-size: 10px; font-style: normal; opacity: 0.58; }
.book-copy small {
  display: -webkit-box;
  min-height: 30px;
  margin-top: 6px;
  overflow: hidden;
  font-size: 10px;
  font-weight: 400;
  line-height: 1.5;
  opacity: 0.68;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.category-whisper {
  position: absolute;
  bottom: 39px;
  left: 50%;
  display: flex;
  gap: 16px;
  transform: translateX(-50%);
}

.category-whisper button {
  padding: 0;
  border: 0;
  color: var(--scene-text);
  background: transparent;
  font-family: var(--reader-serif);
  font-size: 11px;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.9);
  cursor: pointer;
  opacity: 0.45;
}

.category-whisper button:hover { opacity: 1; }

@media (max-width: 1720px) {
  .category-whisper { display: none; }
  .desk-selection { right: 36px; }
}

@media (max-width: 820px) {
  .room-page { min-height: calc(100vh - 70px); }
  .room-intro { top: 15vh; }
  .room-intro h1 { font-size: 31px; }
  .quiet-status { top: 64px; right: 16px; bottom: auto; left: auto; }
  .quiet-status button { justify-content: flex-end; }

  .desk-selection {
    right: 14px;
    bottom: 18px;
    left: 14px;
    width: auto;
    padding: 16px;
    background: linear-gradient(180deg, rgba(13, 12, 10, 0.28), rgba(13, 12, 10, 0.66));
    backdrop-filter: blur(8px);
  }

  .desk-books {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    scroll-snap-type: x proximity;
    scrollbar-width: none;
  }
  .desk-books::-webkit-scrollbar { display: none; }
  .desk-book {
    width: min(248px, 76vw);
    flex: 0 0 min(248px, 76vw);
    scroll-snap-align: start;
  }
  .desk-book-main {
    align-items: flex-start;
  }
}

@media (max-width: 500px) {
  .room-intro { top: 18vh; }
  .room-intro > p { font-size: 11px; }
  .room-intro > span { font-size: 12px; }
  .book-cover { width: 34px; height: 48px; flex-basis: 34px; }
}
</style>
