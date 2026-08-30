<template>
  <section class="admin-dashboard">
    <header class="page-intro">
      <div>
        <p>馆务总览</p>
        <h1>数据总览</h1>
      </div>
      <span>快速掌握馆藏、读者与借阅运行情况</span>
    </header>

    <div class="metric-grid">
      <article
        v-for="card in overviewCards"
        :key="card.label"
        class="metric-card"
        :class="card.tone"
      >
        <p>{{ card.label }}</p>
        <strong>{{ card.value }}</strong>
        <span>{{ card.caption }}</span>
      </article>
    </div>

    <section class="chart-row">
      <div class="chart-main">
        <PieChart tag="基础数据构成" :values="pieValues" :types="pieTypes" />
      </div>
      <aside class="chart-note">
        <p>数据构成</p>
        <strong>{{ baseTotal }}</strong>
        <span>当前汇总记录</span>
        <dl>
          <div>
            <dt>占比最高</dt>
            <dd>{{ largestBaseType }}</dd>
          </div>
          <div>
            <dt>统计口径</dt>
            <dd>实时业务数据</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="chart-row">
      <div class="chart-main">
        <LineChart
          height="300px"
          tag="用户增长"
          :values="userValues"
          :date="userDates"
          @on-selected="userDatesSelected"
        />
      </div>
      <aside class="chart-note">
        <p>读者变化</p>
        <strong>{{ userGrowthTotal }}</strong>
        <span>所选时段新增用户</span>
        <dl>
          <div>
            <dt>近期走势</dt>
            <dd>{{ userTrend }}</dd>
          </div>
          <div>
            <dt>用途</dt>
            <dd>观察读者活跃基础</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="chart-row">
      <div class="chart-main">
        <LineChart
          height="300px"
          tag="新增图书"
          :values="modelValues"
          :date="modelDates"
          @on-selected="modelDatesSelected"
        />
      </div>
      <aside class="chart-note">
        <p>馆藏变化</p>
        <strong>{{ bookGrowthTotal }}</strong>
        <span>所选时段新增图书</span>
        <dl>
          <div>
            <dt>近期走势</dt>
            <dd>{{ bookTrend }}</dd>
          </div>
          <div>
            <dt>用途</dt>
            <dd>观察馆藏补充节奏</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="notice-section">
      <div class="section-head">
        <div>
          <p>近日公告</p>
          <h2>最新公告</h2>
        </div>
        <span>最近发布的馆内信息</span>
      </div>
      <div v-if="noticeList.length" class="notice-list">
        <article v-for="notice in noticeList" :key="notice.id || notice.name">
          <strong>{{ notice.name }}</strong>
          <span>{{ notice.createTime }}</span>
        </article>
      </div>
      <el-empty v-else description="暂无公告" />
    </section>
  </section>
</template>

<script>
import LineChart from "@/components/LineChart.vue";
import PieChart from "@/components/PieChart.vue";

function sum(values) {
  return values.reduce((total, value) => total + Number(value || 0), 0);
}

function trendText(values) {
  if (values.length < 2) return "等待更多数据";
  const current = Number(values[values.length - 1] || 0);
  const previous = Number(values[values.length - 2] || 0);
  if (current > previous) return "较上一周期上升";
  if (current < previous) return "较上一周期回落";
  return "与上一周期持平";
}

export default {
  name: "AdminDashboard",
  components: { LineChart, PieChart },
  data() {
    return {
      userValues: [],
      userDates: [],
      modelDates: [],
      modelValues: [],
      pieValues: [],
      pieTypes: [],
      noticeList: [],
      overviewCards: [
        { label: "图书总数", value: 0, caption: "当前馆藏", tone: "jade" },
        { label: "用户总数", value: 0, caption: "注册读者", tone: "blue" },
        { label: "借阅中", value: 0, caption: "尚未归还", tone: "gold" },
        { label: "已归还", value: 0, caption: "历史归还", tone: "ink" },
      ],
    };
  },
  computed: {
    baseTotal() {
      return sum(this.pieValues);
    },
    largestBaseType() {
      if (!this.pieValues.length) return "等待数据";
      const maxIndex = this.pieValues.indexOf(Math.max(...this.pieValues));
      return this.pieTypes[maxIndex] || "未分类";
    },
    userGrowthTotal() {
      return sum(this.userValues);
    },
    bookGrowthTotal() {
      return sum(this.modelValues);
    },
    userTrend() {
      return trendText(this.userValues);
    },
    bookTrend() {
      return trendText(this.modelValues);
    },
  },
  created() {
    this.userDatesSelected(365);
    this.modelDatesSelected(365);
    this.loadPieCharts();
    this.loadMessage();
    this.loadOverview();
  },
  methods: {
    async loadMessage() {
      try {
        const response = await this.$axios.post("/notice/query", {
          current: 1,
          size: 4,
        });
        if (response.data.code === 200) {
          this.noticeList = response.data.data || [];
        }
      } catch (error) {
        this.$message.error("公告加载失败，请稍后重试。");
        console.error("Main.vue loadMessage:", error);
      }
    },
    async loadPieCharts() {
      try {
        const response = await this.$axios.get("/views/staticControls");
        if (response.data.code === 200) {
          this.pieValues = response.data.data.map((entity) => entity.count);
          this.pieTypes = response.data.data.map((entity) => entity.name);
        }
      } catch (error) {
        this.$message.error("基础数据加载失败，请稍后重试。");
        console.error("Main.vue loadPieCharts:", error);
      }
    },
    async modelDatesSelected(time) {
      try {
        const response = await this.$axios.get(`/book/queryByDays/${time}`);
        if (response.data.code === 200) {
          this.modelValues = response.data.data.map((entity) => entity.count);
          this.modelDates = response.data.data.map((entity) => entity.name);
        }
      } catch (error) {
        this.$message.error("新增图书数据加载失败。");
        console.error("Main.vue modelDatesSelected:", error);
      }
    },
    async userDatesSelected(time) {
      try {
        const response = await this.$axios.get(`/user/queryByDays/${time}`);
        if (response.data.code === 200) {
          this.userValues = response.data.data.map((entity) => entity.count);
          this.userDates = response.data.data.map((entity) => entity.name);
        }
      } catch (error) {
        this.$message.error("用户增长数据加载失败。");
        console.error("Main.vue userDatesSelected:", error);
      }
    },
    async loadOverview() {
      try {
        const response = await this.$axios.get("/statistics/overview");
        if (response.data.code === 200) {
          const data = response.data.data;
          this.overviewCards[0].value = data.totalBooks || 0;
          this.overviewCards[1].value = data.totalUsers || 0;
          this.overviewCards[2].value = data.activeBorrows || 0;
          this.overviewCards[3].value = data.returnedBorrows || 0;
        }
      } catch (error) {
        console.error("Main.vue loadOverview:", error);
      }
    },
  },
};
</script>

<style scoped lang="scss">
.admin-dashboard {
  display: grid;
  gap: 20px;
  color: var(--admin-text);
}

.page-intro,
.section-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: end;

  p {
    margin: 0;
    color: var(--admin-gold);
    font-size: 11px;
    font-weight: 700;
  }

  h1,
  h2 {
    margin: 5px 0 0;
    color: var(--admin-text);
  }

  h1 {
    font-size: 32px;
  }

  h2 {
    font-size: 22px;
  }

  > span {
    color: var(--admin-muted);
    font-size: 13px;
  }
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(145px, 1fr));
  gap: 12px;
}

.metric-card {
  position: relative;
  padding: 17px 18px;
  overflow: hidden;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);

  &::before {
    content: "";
    position: absolute;
    inset: 0 auto 0 0;
    width: 4px;
    background: var(--admin-text-secondary);
  }

  &.jade::before {
    background: var(--admin-jade);
  }

  &.blue::before {
    background: var(--admin-blue);
  }

  &.gold::before {
    background: var(--admin-gold);
  }

  p,
  span {
    margin: 0;
    color: var(--admin-muted);
  }

  p {
    font-size: 13px;
  }

  strong {
    display: block;
    margin: 8px 0 4px;
    color: var(--admin-text);
    font-size: 30px;
    line-height: 1;
  }

  span {
    font-size: 11px;
  }
}

.chart-row {
  display: grid;
  grid-template-columns: minmax(0, 3.2fr) minmax(210px, 0.8fr);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
}

.chart-main {
  min-width: 0;
  padding: 20px 22px 14px;
}

.chart-note {
  min-width: 0;
  padding: 22px 20px;
  border-left: 1px solid var(--admin-border);
  background: var(--admin-surface-muted);

  > p {
    margin: 0;
    color: var(--admin-gold);
    font-size: 11px;
    font-weight: 700;
  }

  > strong {
    display: block;
    margin-top: 12px;
    color: var(--admin-text);
    font-size: 32px;
  }

  > span {
    display: block;
    margin-top: 3px;
    color: var(--admin-muted);
    font-size: 12px;
  }

  dl {
    display: grid;
    gap: 14px;
    margin: 28px 0 0;
  }

  dl div {
    padding-top: 12px;
    border-top: 1px solid var(--admin-border);
  }

  dt {
    color: var(--admin-muted);
    font-size: 11px;
  }

  dd {
    margin: 5px 0 0;
    color: var(--admin-text-secondary);
    font-size: 13px;
    line-height: 1.55;
  }
}

.notice-section {
  padding-top: 2px;
}

.notice-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 13px;
  border-top: 1px solid var(--admin-border);

  article {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 14px 4px;
    border-bottom: 1px solid var(--admin-border);
  }

  article:nth-child(odd) {
    padding-right: 18px;
  }

  article:nth-child(even) {
    padding-left: 18px;
    border-left: 1px solid var(--admin-border);
  }

  strong {
    min-width: 0;
    overflow: hidden;
    color: var(--admin-text);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    flex: none;
    color: var(--admin-muted);
    font-size: 12px;
  }
}

@media (max-width: 1020px) {
  .chart-row {
    grid-template-columns: 1fr;
  }

  .chart-note {
    border-top: 1px solid var(--admin-border);
    border-left: 0;
  }

  .chart-note dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 18px;
  }
}

@media (max-width: 760px) {
  .page-intro,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .notice-list {
    grid-template-columns: 1fr;
  }

  .notice-list article:nth-child(n) {
    padding-right: 4px;
    padding-left: 4px;
    border-left: 0;
  }
}
</style>
