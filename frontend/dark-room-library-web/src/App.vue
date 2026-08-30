<template>
  <div id="app">
    <VisualFilterDefs />
    <el-config-provider :locale="elementLocale">
      <router-view />
      <DemoToolbar />
    </el-config-provider>
  </div>
</template>

<script>
import zhCn from "element-plus/es/locale/lang/zh-cn";
import DemoToolbar from "@/components/DemoToolbar.vue";
import VisualFilterDefs from "@/components/VisualFilterDefs.vue";

export default {
  name: "App",
  components: { DemoToolbar, VisualFilterDefs },
  data() {
    return { elementLocale: zhCn };
  },
};
</script>

<style lang="scss">
#app {
  height: 100%;
}
</style>
