<template>
  <svg
    class="visual-filter-defs"
    width="0"
    height="0"
    aria-hidden="true"
    focusable="false"
  >
    <defs>
      <filter
        id="reader-paper-grain"
        x="-4%"
        y="-4%"
        width="108%"
        height="108%"
        color-interpolation-filters="sRGB"
      >
        <feTurbulence
          type="fractalNoise"
          baseFrequency="0.72 0.18"
          numOctaves="2"
          seed="17"
          result="fiberNoise"
        />
        <feColorMatrix
          in="fiberNoise"
          type="matrix"
          values="
            0 0 0 0 0.29
            0 0 0 0 0.24
            0 0 0 0 0.17
            0 0 0 0.16 0
          "
          result="paperFiber"
        />
        <feBlend in="SourceGraphic" in2="paperFiber" mode="multiply" />
      </filter>
    </defs>
  </svg>
</template>

<script>
export default {
  name: "VisualFilterDefs",
};
</script>

<style scoped>
.visual-filter-defs {
  position: fixed;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}
</style>
