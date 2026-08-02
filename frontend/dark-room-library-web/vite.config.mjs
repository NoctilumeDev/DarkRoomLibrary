import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

export default defineConfig(({ mode }) => ({
  base: mode === "demo" ? "/DarkRoomLibrary/" : "/",
  plugins: [
    vue(),
    Components({
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: false })],
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  optimizeDeps: {
    entries: ["index.html", "src/**/*.js", "src/**/*.vue"],
    include: [
      "element-plus/es",
      "element-plus/es/components/message/index.mjs",
    ],
  },
  server: {
    host: "0.0.0.0",
    port: 5175,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:20606",
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: "0.0.0.0",
    port: 4175,
    strictPort: true,
  },
  test: {
    environment: "jsdom",
    globals: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov", "json-summary"],
      include: ["src/demo/adapter.js", "src/utils/**/*.js"],
      exclude: [
        "src/utils/adminChartTheme.js",
        "src/utils/echarts*.js",
        "src/utils/message.js",
        "src/utils/readerTheme.js",
        "src/utils/swalPlugin.js",
      ],
      thresholds: {
        lines: 70,
        branches: 60,
        functions: 70,
      },
    },
  },
}));
