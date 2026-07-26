export const ADMIN_THEME_EVENT = "admin-theme-change";

function readVariable(styles, name, fallback) {
  return styles.getPropertyValue(name).trim() || fallback;
}

export function toRgba(color, alpha) {
  const value = color.replace("#", "");
  if (!/^[0-9a-f]{6}$/i.test(value)) return color;
  const number = Number.parseInt(value, 16);
  return `rgba(${(number >> 16) & 255}, ${(number >> 8) & 255}, ${
    number & 255
  }, ${alpha})`;
}

export function getAdminChartTheme(element) {
  const scope =
    element?.closest?.(".admin-shell") ||
    document.querySelector(".admin-shell") ||
    document.documentElement;
  const styles = getComputedStyle(scope);

  return {
    text: readVariable(styles, "--admin-text", "#292822"),
    secondary: readVariable(styles, "--admin-text-secondary", "#5f5b52"),
    muted: readVariable(styles, "--admin-muted", "#7b756a"),
    surface: readVariable(styles, "--admin-surface-strong", "#f8f5ee"),
    border: readVariable(styles, "--admin-border", "rgba(58, 55, 47, 0.18)"),
    grid: readVariable(styles, "--chart-grid", "rgba(62, 59, 51, 0.12)"),
    palette: [1, 2, 3, 4, 5].map((index) =>
      readVariable(styles, `--chart-${index}`, "#777064")
    ),
  };
}
