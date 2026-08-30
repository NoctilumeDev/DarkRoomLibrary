const COMPACT_VIEWPORT_QUERY = "(max-width: 760px)";

export default {
  data() {
    return {
      isCompactViewport: false,
    };
  },
  mounted() {
    this.compactViewportQuery = window.matchMedia(COMPACT_VIEWPORT_QUERY);
    this.syncCompactViewport(this.compactViewportQuery);
    this.compactViewportQuery.addEventListener(
      "change",
      this.syncCompactViewport
    );
  },
  beforeUnmount() {
    this.compactViewportQuery?.removeEventListener(
      "change",
      this.syncCompactViewport
    );
  },
  methods: {
    syncCompactViewport(event) {
      this.isCompactViewport = event.matches;
    },
  },
};
