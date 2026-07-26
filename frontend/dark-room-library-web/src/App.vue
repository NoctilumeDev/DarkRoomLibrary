<template>
  <div id="app">
    <VisualFilterDefs />
    <router-view />
  </div>
</template>

<script>
import VisualFilterDefs from "@/components/VisualFilterDefs.vue";

export default {
  name: "App",
  components: { VisualFilterDefs },
};
</script>

<style lang="scss">
#app {
  height: 100%;
}
</style>
