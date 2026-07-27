import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

export default defineConfig({
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
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("/node_modules/zrender/")) {
            return "charts-renderer";
          }
          if (id.includes("/node_modules/echarts/")) {
            return "charts";
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
  },
});
