const DEFAULT_API_BASE_URL = "/api/dark-room-library/v1";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL;

export function buildApiUrl(path) {
  if (/^https?:\/\//i.test(path)) return path;
  const base = API_BASE_URL.replace(/\/$/, "");
  const suffix = path.startsWith("/") ? path : `/${path}`;
  return `${base}${suffix}`;
}

export function resolveFileUrl(url) {
  if (!url) return "";
  if (/^(https?:|data:|blob:)/i.test(url)) return url;
  if (/^\//.test(url)) return url;
  return `/${url}`;
}

export function toApiRequestPath(url) {
  if (!url) return "";
  const baseUrl = new URL(API_BASE_URL, window.location.origin);
  const targetUrl = new URL(resolveFileUrl(url), window.location.origin);
  const basePath = baseUrl.pathname.replace(/\/$/, "");
  if (targetUrl.pathname.startsWith(basePath)) {
    const relativePath = targetUrl.pathname.slice(basePath.length) || "/";
    return `${relativePath}${targetUrl.search}`;
  }
  return `${targetUrl.pathname}${targetUrl.search}`;
}
