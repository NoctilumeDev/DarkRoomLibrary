function isValidDate(value) {
  return value instanceof Date && !Number.isNaN(value.getTime());
}

function formatLocalDate(value) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function toDayRange(range) {
  if (!Array.isArray(range) || range.length !== 2) {
    return { startTime: null, endTime: null };
  }

  const [start, end] = range;
  if (!isValidDate(start) || !isValidDate(end)) {
    return { startTime: null, endTime: null };
  }

  return {
    startTime: `${formatLocalDate(start)}T00:00:00`,
    endTime: `${formatLocalDate(end)}T23:59:59`,
  };
}

export function positiveIntegerFromQuery(value, fallback = 1) {
  const candidate = Array.isArray(value) ? value[0] : value;
  const parsed = Number.parseInt(String(candidate ?? ""), 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export function pageSizeFromQuery(value, allowed, fallback) {
  const parsed = positiveIntegerFromQuery(value, fallback);
  return allowed.includes(parsed) ? parsed : fallback;
}
