<template>
  <div id="app">
    <VisualFilterDefs />
    <router-view />
    <DemoToolbar />
  </div>
</template>

<script>
import DemoToolbar from "@/components/DemoToolbar.vue";
import VisualFilterDefs from "@/components/VisualFilterDefs.vue";

export default {
  name: "App",
  components: { DemoToolbar, VisualFilterDefs },
};
</script>

<style lang="scss">
#app {
  height: 100%;
}
</style>
