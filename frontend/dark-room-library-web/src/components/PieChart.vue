<template>
  <div class="pie-chart">
    <div class="chart-title">{{ tag }}</div>
    <div ref="chart" class="chart-canvas" :style="{ width, height }"></div>
  </div>
</template>

<script>
import { loadPieEcharts } from "@/utils/echarts.js";
import {
  ADMIN_THEME_EVENT,
  getAdminChartTheme,
} from "@/utils/adminChartTheme.js";

export default {
  name: "AdminPieChart",
  props: {
    types: {
      type: Array,
      default: () => [],
    },
    values: {
      type: Array,
      default: () => [],
    },
    width: {
      type: String,
      default: "100%",
    },
    tag: {
      type: String,
      default: "数据构成",
    },
    height: {
      type: String,
      default: "300px",
    },
  },
  data() {
    return {
      chart: null,
      resizeObserver: null,
    };
  },
  watch: {
    types: {
      deep: true,
      handler() {
        this.renderChart();
      },
    },
    values: {
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
      const echarts = await loadPieEcharts();
      if (this.$refs.chart !== chartElement) return;
      const theme = getAdminChartTheme(this.$el);
      if (!this.chart) this.chart = echarts.init(chartElement);

      this.chart.setOption(
        {
          animationDuration: 420,
          color: theme.palette,
          backgroundColor: "transparent",
          tooltip: {
            trigger: "item",
            formatter: "{b}<br/>{c}（{d}%）",
            backgroundColor: theme.surface,
            borderColor: theme.border,
            textStyle: { color: theme.text },
          },
          series: [
            {
              type: "pie",
              radius: ["48%", "70%"],
              center: ["50%", "54%"],
              minAngle: 4,
              padAngle: 2,
              itemStyle: {
                borderColor: theme.surface,
                borderWidth: 2,
                borderRadius: 3,
              },
              label: {
                show: true,
                color: theme.secondary,
                formatter: "{b}\n{d}%",
              },
              labelLine: {
                length: 12,
                length2: 8,
                lineStyle: { color: theme.grid },
              },
              emphasis: {
                scaleSize: 5,
                label: { color: theme.text, fontWeight: 700 },
              },
              data: this.values.map((value, index) => ({
                name: this.types[index] || "未命名",
                value,
              })),
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
.pie-chart {
  min-width: 0;
}

.chart-title {
  color: var(--admin-text);
  font-size: 18px;
  font-weight: 700;
}

.chart-canvas {
  min-height: 260px;
}
</style>
