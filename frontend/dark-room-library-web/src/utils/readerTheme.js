export const READER_THEME_KEY = "dark-room-reader-theme";
export const READER_THEMES = Object.freeze({ NIGHT: "night", DAY: "day" });

export function normalizeReaderTheme(theme) {
  return theme === READER_THEMES.DAY ? READER_THEMES.DAY : READER_THEMES.NIGHT;
}

export function getReaderTheme() {
  try {
    return normalizeReaderTheme(localStorage.getItem(READER_THEME_KEY));
  } catch {
    return READER_THEMES.NIGHT;
  }
}

export function saveReaderTheme(theme) {
  const normalized = normalizeReaderTheme(theme);
  try {
    localStorage.setItem(READER_THEME_KEY, normalized);
  } catch {
    // Storage can be unavailable in privacy mode; the current page still updates.
  }
  return normalized;
}

export function toggleReaderTheme(theme) {
  return saveReaderTheme(
    normalizeReaderTheme(theme) === READER_THEMES.NIGHT
      ? READER_THEMES.DAY
      : READER_THEMES.NIGHT
  );
}
