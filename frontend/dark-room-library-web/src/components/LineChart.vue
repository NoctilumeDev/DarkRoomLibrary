<template>
  <div class="line-chart">
    <div class="chart-head">
      <span class="chart-title">{{ tag }}</span>
      <label class="time-select">
        <span>时间范围</span>
        <el-select v-model="selectedValue" size="small" aria-label="选择统计时间范围">
          <el-option
            v-for="item in options"
            :key="item.num"
            :label="item.name"
            :value="item.num"
          />
        </el-select>
      </label>
    </div>
    <div ref="chart" class="chart-canvas" :style="{ height }"></div>
  </div>
</template>

<script>
import { loadLineEcharts } from "@/utils/echarts.js";
import {
  ADMIN_THEME_EVENT,
  getAdminChartTheme,
  toRgba,
} from "@/utils/adminChartTheme.js";

export default {
  name: "AdminLineChart",
  props: {
    tag: {
      type: String,
      default: "趋势图",
    },
    values: {
      type: Array,
      required: true,
    },
    date: {
      type: Array,
      required: true,
    },
    height: {
      type: String,
      default: "280px",
    },
  },
  emits: ["on-selected"],
  data() {
    return {
      chart: null,
      resizeObserver: null,
      options: [
        { num: 7, name: "近 7 天" },
        { num: 30, name: "近 30 天" },
        { num: 60, name: "近 60 天" },
        { num: 365, name: "近一年" },
      ],
      selectedValue: 365,
    };
  },
  watch: {
    selectedValue(value) {
      this.$emit("on-selected", value);
    },
    values: {
      deep: true,
      handler() {
        this.renderChart();
      },
    },
    date: {
      deep: true,
      handler() {
        this.renderChart();
      },
    },
  },
  mounted() {
    this.renderChart();
    window.addEventListener(ADMIN_THEME_EVENT, this.renderChart);
    if (typeof ResizeObserver !== "undefined") {
      this.resizeObserver = new ResizeObserver(() => this.chart?.resize());
      this.resizeObserver.observe(this.$refs.chart);
    }
  },
  beforeUnmount() {
    window.removeEventListener(ADMIN_THEME_EVENT, this.renderChart);
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
  },
  methods: {
    async renderChart() {
      const chartElement = this.$refs.chart;
      if (!chartElement) return;
      const echarts = await loadLineEcharts();
      if (this.$refs.chart !== chartElement) return;
      const theme = getAdminChartTheme(this.$el);
      if (!this.chart) this.chart = echarts.init(chartElement);

      this.chart.setOption(
        {
          animationDuration: 420,
          backgroundColor: "transparent",
          grid: {
            left: 18,
            right: 16,
            top: 26,
            bottom: 18,
            containLabel: true,
          },
          tooltip: {
            trigger: "axis",
            backgroundColor: theme.surface,
            borderColor: theme.border,
            textStyle: { color: theme.text },
          },
          xAxis: {
            type: "category",
            boundaryGap: false,
            data: this.date,
            axisLine: { lineStyle: { color: theme.grid } },
            axisTick: { show: false },
            axisLabel: { color: theme.secondary, hideOverlap: true },
          },
          yAxis: {
            type: "value",
            minInterval: 1,
            axisLine: { show: false },
            axisTick: { show: false },
            axisLabel: { color: theme.secondary },
            splitLine: { lineStyle: { color: theme.grid } },
          },
          series: [
            {
              type: "line",
              smooth: true,
              symbol: "circle",
              symbolSize: 7,
              data: this.values,
              lineStyle: { width: 2, color: theme.palette[1] },
              itemStyle: {
                color: theme.surface,
                borderColor: theme.palette[1],
                borderWidth: 2,
              },
              areaStyle: { color: toRgba(theme.palette[1], 0.13) },
            },
          ],
        },
        true
      );
    },
  },
};
</script>

<style scoped lang="scss">
.line-chart {
  min-width: 0;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.chart-title {
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 700;
}

.time-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--admin-muted);
  font-size: 12px;

  .el-select {
    width: 116px;
  }
}

.chart-canvas {
  width: 100%;
  min-height: 240px;
}

@media (max-width: 560px) {
  .chart-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
