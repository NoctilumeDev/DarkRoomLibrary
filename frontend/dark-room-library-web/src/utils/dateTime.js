export const APP_TIME_ZONE = import.meta.env.VITE_APP_TIME_ZONE || "Asia/Shanghai";

const SQL_DATE_TIME_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/;

const zonedPartsFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: APP_TIME_ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hourCycle: "h23",
});

function zonedParts(date) {
  const parts = Object.fromEntries(
    zonedPartsFormatter
      .formatToParts(date)
      .filter((part) => part.type !== "literal")
      .map((part) => [part.type, Number(part.value)])
  );
  return {
    year: parts.year,
    month: parts.month,
    day: parts.day,
    hour: parts.hour,
    minute: parts.minute,
    second: parts.second,
  };
}

function offsetAt(date) {
  const parts = zonedParts(date);
  const wallClockAsUtc = Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour,
    parts.minute,
    parts.second
  );
  const instantToSecond = Math.floor(date.getTime() / 1000) * 1000;
  return wallClockAsUtc - instantToSecond;
}

function wallClockToInstant(parts) {
  const target = Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour,
    parts.minute,
    parts.second
  );
  let candidate = target;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const next = target - offsetAt(new Date(candidate));
    if (next === candidate) break;
    candidate = next;
  }
  return new Date(candidate);
}

export function parseAppDateTime(value) {
  if (value instanceof Date) return new Date(value.getTime());
  const text = String(value ?? "").trim();
  if (!text) return null;

  const sqlMatch = text.match(SQL_DATE_TIME_PATTERN);
  if (sqlMatch) {
    return wallClockToInstant({
      year: Number(sqlMatch[1]),
      month: Number(sqlMatch[2]),
      day: Number(sqlMatch[3]),
      hour: Number(sqlMatch[4]),
      minute: Number(sqlMatch[5]),
      second: Number(sqlMatch[6]),
    });
  }

  const parsed = new Date(text);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function toAppSqlDateTime(value = new Date()) {
  const date = parseAppDateTime(value);
  if (!date) return "";
  const parts = zonedParts(date);
  const pad = (part) => String(part).padStart(2, "0");
  return `${parts.year}-${pad(parts.month)}-${pad(parts.day)} `
    + `${pad(parts.hour)}:${pad(parts.minute)}:${pad(parts.second)}`;
}

export function formatAppMonthDayTime(value) {
  const date = parseAppDateTime(value);
  if (!date) return "时间未知";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: APP_TIME_ZONE,
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
}

export function remainingAppDays(deadline, now = new Date()) {
  const end = parseAppDateTime(deadline);
  const current = parseAppDateTime(now);
  if (!end || !current) return null;
  return Math.max(1, Math.ceil((end.getTime() - current.getTime()) / 86400000));
}
