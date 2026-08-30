<template>
  <section class="statistics-page">
    <header class="page-intro">
      <div>
        <p>馆务统计</p>
        <h1>统计看板</h1>
      </div>
      <span>从借阅、馆藏与风险三个方向观察系统运行</span>
    </header>

    <div class="metric-grid">
      <article
        v-for="card in overviewCards"
        :key="card.label"
        class="metric-card"
        :class="card.tone"
      >
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.caption }}</small>
      </article>
    </div>

    <section class="insight-row">
      <div class="visual-panel">
        <header class="panel-head">
          <div>
            <p>借阅节奏</p>
            <h2>月度借阅趋势</h2>
          </div>
          <el-date-picker
            v-model="borrowMonth"
            type="month"
            placeholder="选择月份"
            value-format="YYYY-MM"
            size="small"
            @change="loadMonthlyBorrow"
          />
        </header>
        <div ref="monthlyChart" class="chart-canvas"></div>
      </div>
      <aside class="insight-note">
        <p>本月借阅</p>
        <strong>{{ monthlyTotal }}</strong>
        <span>累计借阅次数</span>
        <dl>
          <div>
            <dt>峰值日期</dt>
            <dd>{{ monthlyPeakText }}</dd>
          </div>
          <div>
            <dt>数据口径</dt>
            <dd>按借阅记录创建日期统计</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="insight-row">
      <div class="visual-panel">
        <header class="panel-head">
          <div>
            <p>流通热度</p>
            <h2>热门图书排行</h2>
          </div>
          <span>前 10 名</span>
        </header>
        <div ref="hotBooksChart" class="chart-canvas"></div>
      </div>
      <aside class="insight-note">
        <p>借阅最热</p>
        <strong class="text-value">{{ hotTopBook }}</strong>
        <span>当前排名第一</span>
        <dl>
          <div>
            <dt>榜单借阅总量</dt>
            <dd>{{ hotBorrowTotal }} 次</dd>
          </div>
          <div>
            <dt>用途</dt>
            <dd>辅助采购与库存补充判断</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="insight-row">
      <div class="visual-panel">
        <header class="panel-head">
          <div>
            <p>馆藏结构</p>
            <h2>馆藏分类分析</h2>
          </div>
          <span>按在册数量统计</span>
        </header>
        <div ref="collectionChart" class="chart-canvas"></div>
      </div>
      <aside class="insight-note">
        <p>馆藏总量</p>
        <strong>{{ collectionTotal }}</strong>
        <span>分类统计在册数量</span>
        <dl>
          <div>
            <dt>主要分类</dt>
            <dd>{{ collectionTop }}</dd>
          </div>
          <div>
            <dt>用途</dt>
            <dd>识别馆藏结构是否均衡</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="insight-row table-row">
      <div class="visual-panel">
        <header class="panel-head">
          <div>
            <p>库存风险</p>
            <h2>低库存图书</h2>
          </div>
          <span>可借数量低于 3 本</span>
        </header>
        <el-table :data="lowStockBooks" max-height="340" size="small">
          <el-table-column prop="name" min-width="180" label="书名" />
          <el-table-column prop="author" min-width="120" label="作者" />
          <el-table-column prop="availableCount" width="90" label="可借库存" />
          <el-table-column width="90" label="状态">
            <template #default="scope">
              <el-tag
                :type="scope.row.availableCount === 0 ? 'danger' : 'warning'"
                size="small"
              >
                {{ scope.row.availableCount === 0 ? "缺货" : "紧张" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <aside class="insight-note risk-note">
        <p>风险图书</p>
        <strong>{{ lowStockBooks.length }}</strong>
        <span>需要关注的馆藏</span>
        <dl>
          <div>
            <dt>已无库存</dt>
            <dd>{{ outOfStockCount }} 本</dd>
          </div>
          <div>
            <dt>建议</dt>
            <dd>结合热门排行决定是否发起采购</dd>
          </div>
        </dl>
      </aside>
    </section>

    <section class="insight-row table-row">
      <div class="visual-panel">
        <header class="panel-head">
          <div>
            <p>逾期风险</p>
            <h2>逾期用户排行</h2>
          </div>
          <span>按逾期记录聚合</span>
        </header>
        <el-table :data="overdueUsers" max-height="340" size="small">
          <el-table-column prop="userName" min-width="180" label="用户名" />
          <el-table-column prop="overdueCount" width="110" label="逾期本数" />
          <el-table-column width="130" label="罚款金额">
            <template #default="scope">
              <span class="danger-text">¥{{ scope.row.totalFine }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <aside class="insight-note risk-note">
        <p>逾期用户</p>
        <strong>{{ overdueUsers.length }}</strong>
        <span>当前榜单人数</span>
        <dl>
          <div>
            <dt>罚款合计</dt>
            <dd>¥{{ overdueFineTotal }}</dd>
          </div>
          <div>
            <dt>建议</dt>
            <dd>优先处理逾期本数较多的用户</dd>
          </div>
        </dl>
      </aside>
    </section>
  </section>
</template>

<script>
import { loadBarEcharts, loadLineEcharts, loadPieEcharts } from "@/utils/echarts.js";
import {
  ADMIN_THEME_EVENT,
  getAdminChartTheme,
  toRgba,
} from "@/utils/adminChartTheme.js";

function currentMonth() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

function sum(values) {
  return values.reduce((total, value) => total + Number(value || 0), 0);
}

export default {
  name: "StatisticsDashboard",
  data() {
    return {
      borrowMonth: currentMonth(),
      overviewCards: [
        { label: "图书总数", value: 0, caption: "当前馆藏", tone: "jade" },
        { label: "用户总数", value: 0, caption: "注册读者", tone: "blue" },
        { label: "借阅中", value: 0, caption: "尚未归还", tone: "gold" },
        { label: "已归还", value: 0, caption: "历史归还", tone: "ink" },
      ],
      lowStockBooks: [],
      overdueUsers: [],
      monthlyDays: [],
      monthlyCounts: [],
      hotBookNames: [],
      hotBookCounts: [],
      collectionNames: [],
      collectionValues: [],
      monthlyChart: null,
      hotBooksChart: null,
      collectionChart: null,
    };
  },
  computed: {
    monthlyTotal() {
      return sum(this.monthlyCounts);
    },
    monthlyPeakText() {
      if (!this.monthlyCounts.length) return "暂无数据";
      const peak = Math.max(...this.monthlyCounts);
      const index = this.monthlyCounts.indexOf(peak);
      return `${this.monthlyDays[index]}，${peak} 次`;
    },
    hotTopBook() {
      if (!this.hotBookCounts.length) return "暂无数据";
      const index = this.hotBookCounts.indexOf(Math.max(...this.hotBookCounts));
      return this.hotBookNames[index] || "暂无数据";
    },
    hotBorrowTotal() {
      return sum(this.hotBookCounts);
    },
    collectionTotal() {
      return sum(this.collectionValues);
    },
    collectionTop() {
      if (!this.collectionValues.length) return "暂无数据";
      const index = this.collectionValues.indexOf(
        Math.max(...this.collectionValues)
      );
      return this.collectionNames[index] || "暂无数据";
    },
    outOfStockCount() {
      return this.lowStockBooks.filter((book) => book.availableCount === 0).length;
    },
    overdueFineTotal() {
      return sum(this.overdueUsers.map((user) => user.totalFine)).toFixed(2);
    },
    gridOption() {
      return { left: 16, right: 18, top: 34, bottom: 14 };
    },
  },
  mounted() {
    this.loadOverview();
    this.loadMonthlyBorrow();
    this.loadHotBooks();
    this.loadLowStock();
    this.loadOverdueUsers();
    this.loadCollectionAnalysis();
    window.addEventListener(ADMIN_THEME_EVENT, this.renderAllCharts);
    window.addEventListener("resize", this.resizeCharts);
  },
  beforeUnmount() {
    window.removeEventListener(ADMIN_THEME_EVENT, this.renderAllCharts);
    window.removeEventListener("resize", this.resizeCharts);
    this.monthlyChart?.dispose();
    this.hotBooksChart?.dispose();
    this.collectionChart?.dispose();
  },
  methods: {
    resizeCharts() {
      this.monthlyChart?.resize();
      this.hotBooksChart?.resize();
      this.collectionChart?.resize();
    },
    renderAllCharts() {
      this.renderMonthlyChart();
      this.renderHotBooksChart();
      this.renderCollectionChart();
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
        console.error("loadOverview:", error);
      }
    },
    async loadMonthlyBorrow() {
      if (!this.borrowMonth) return;
      const [year, month] = this.borrowMonth.split("-");
      try {
        const response = await this.$axios.get(
          `/statistics/monthlyBorrow/${year}/${Number.parseInt(month, 10)}`
        );
        if (response.data.code === 200 && response.data.data) {
          this.monthlyDays = response.data.data.map((item) => `${item.day}日`);
          this.monthlyCounts = response.data.data.map((item) => item.count);
          this.renderMonthlyChart();
        }
      } catch (error) {
        console.error("loadMonthlyBorrow:", error);
      }
    },
    async renderMonthlyChart() {
      const chartElement = this.$refs.monthlyChart;
      if (!chartElement) return;
      const echarts = await loadLineEcharts();
      if (this.$refs.monthlyChart !== chartElement) return;
      const theme = getAdminChartTheme(this.$el);
      if (!this.monthlyChart) this.monthlyChart = echarts.init(chartElement);
      this.monthlyChart.setOption(
        {
          animationDuration: 420,
          tooltip: this.tooltipOption(theme, "axis"),
          grid: this.gridOption,
          xAxis: this.categoryAxis(theme, this.monthlyDays),
          yAxis: this.valueAxis(theme, "借阅次数"),
          series: [
            {
              data: this.monthlyCounts,
              type: "line",
              smooth: true,
              symbolSize: 7,
              lineStyle: { width: 2, color: theme.palette[1] },
              itemStyle: { color: theme.palette[1] },
              areaStyle: { color: toRgba(theme.palette[1], 0.13) },
            },
          ],
        },
        true
      );
    },
    async loadHotBooks() {
      try {
        const response = await this.$axios.get("/statistics/hotBooks?limit=10");
        const books = response.data.data?.books || [];
        if (response.data.code === 200) {
          this.hotBookNames = books.map((book) => book.bookName);
          this.hotBookCounts = books.map((book) => book.borrowCount);
          this.renderHotBooksChart();
        }
      } catch (error) {
        console.error("loadHotBooks:", error);
      }
    },
    async renderHotBooksChart() {
      const chartElement = this.$refs.hotBooksChart;
      if (!chartElement) return;
      const echarts = await loadBarEcharts();
      if (this.$refs.hotBooksChart !== chartElement) return;
      const theme = getAdminChartTheme(this.$el);
      if (!this.hotBooksChart) this.hotBooksChart = echarts.init(chartElement);
      const rows = this.hotBookNames.map((name, index) => ({
        name: name.length > 12 ? `${name.slice(0, 12)}…` : name,
        count: this.hotBookCounts[index],
      }));
      this.hotBooksChart.setOption(
        {
          animationDuration: 420,
          tooltip: this.tooltipOption(theme, "axis", { type: "shadow" }),
          grid: { ...this.gridOption, left: 22 },
          xAxis: this.valueAxis(theme, "借阅次数"),
          yAxis: {
            type: "category",
            data: rows.map((row) => row.name).reverse(),
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { color: theme.secondary },
          },
          series: [
            {
              data: rows.map((row) => row.count).reverse(),
              type: "bar",
              barMaxWidth: 20,
              itemStyle: { color: theme.palette[0], borderRadius: [0, 3, 3, 0] },
            },
          ],
        },
        true
      );
    },
    async loadLowStock() {
      try {
        const response = await this.$axios.get("/statistics/lowStock?threshold=3");
        if (response.data.code === 200) {
          this.lowStockBooks = response.data.data?.books || [];
        }
      } catch (error) {
        console.error("loadLowStock:", error);
      }
    },
    async loadOverdueUsers() {
      try {
        const response = await this.$axios.get("/statistics/overdueUsers");
        if (response.data.code === 200) {
          this.overdueUsers = (response.data.data || []).slice(0, 10);
        }
      } catch (error) {
        console.error("loadOverdueUsers:", error);
      }
    },
    async loadCollectionAnalysis() {
      try {
        const response = await this.$axios.get("/statistics/collectionAnalysis");
        const categories = response.data.data?.categories || [];
        if (response.data.code === 200) {
          this.collectionNames = categories.map((item) => item.category);
          this.collectionValues = categories.map((item) => item.totalCount);
          this.renderCollectionChart();
        }
      } catch (error) {
        console.error("loadCollectionAnalysis:", error);
      }
    },
    async renderCollectionChart() {
      const chartElement = this.$refs.collectionChart;
      if (!chartElement) return;
      const echarts = await loadPieEcharts();
      if (this.$refs.collectionChart !== chartElement) return;
      const theme = getAdminChartTheme(this.$el);
      if (!this.collectionChart) {
        this.collectionChart = echarts.init(chartElement);
      }
      this.collectionChart.setOption(
        {
          animationDuration: 420,
          color: theme.palette,
          tooltip: {
            ...this.tooltipOption(theme, "item"),
            formatter: "{b}<br/>{c} 本（{d}%）",
          },
          series: [
            {
              type: "pie",
              radius: ["46%", "70%"],
              center: ["50%", "52%"],
              padAngle: 2,
              itemStyle: {
                borderColor: theme.surface,
                borderWidth: 2,
                borderRadius: 3,
              },
              label: { color: theme.secondary, formatter: "{b}  {d}%" },
              labelLine: { lineStyle: { color: theme.grid } },
              data: this.collectionNames.map((name, index) => ({
                name,
                value: this.collectionValues[index],
              })),
            },
          ],
        },
        true
      );
    },
    tooltipOption(theme, trigger, axisPointer) {
      return {
        trigger,
        axisPointer,
        backgroundColor: theme.surface,
        borderColor: theme.border,
        textStyle: { color: theme.text },
      };
    },
    categoryAxis(theme, data) {
      return {
        type: "category",
        boundaryGap: false,
        data,
        axisLine: { lineStyle: { color: theme.grid } },
        axisTick: { show: false },
        axisLabel: { color: theme.secondary, hideOverlap: true },
      };
    },
    valueAxis(theme, name) {
      return {
        type: "value",
        name,
        minInterval: 1,
        nameTextStyle: { color: theme.muted },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: theme.secondary },
        splitLine: { lineStyle: { color: theme.grid } },
      };
    },
  },
};
</script>

<style scoped lang="scss">
.statistics-page {
  display: grid;
  gap: 20px;
  color: var(--admin-text);
}

.page-intro,
.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
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
    font-size: 19px;
  }

  > span {
    color: var(--admin-muted);
    font-size: 12px;
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

  span,
  small {
    display: block;
    color: var(--admin-muted);
  }

  strong {
    display: block;
    margin: 8px 0 4px;
    color: var(--admin-text);
    font-size: 30px;
    line-height: 1;
  }
}

.insight-row {
  display: grid;
  grid-template-columns: minmax(0, 3.2fr) minmax(220px, 0.8fr);
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-border);
  border-radius: 6px;
  background: var(--admin-surface);
}

.visual-panel {
  min-width: 0;
  padding: 20px 22px;
}

.chart-canvas {
  width: 100%;
  height: 330px;
  margin-top: 6px;
}

.insight-note {
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
    line-height: 1.2;
  }

  > strong.text-value {
    font-size: 21px;
    overflow-wrap: anywhere;
  }

  > span {
    display: block;
    margin-top: 4px;
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

.table-row .visual-panel {
  padding-bottom: 24px;
}

.table-row .panel-head {
  margin-bottom: 16px;
}

.danger-text {
  color: var(--admin-danger);
  font-weight: 700;
}

@media (max-width: 1020px) {
  .insight-row {
    grid-template-columns: 1fr;
  }

  .insight-note {
    border-top: 1px solid var(--admin-border);
    border-left: 0;
  }

  .insight-note dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 18px;
  }
}

@media (max-width: 760px) {
  .page-intro,
  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-canvas {
    height: 290px;
  }
}

@media (max-width: 480px) {
  .metric-grid,
  .insight-note dl {
    grid-template-columns: 1fr;
  }
}
</style>
