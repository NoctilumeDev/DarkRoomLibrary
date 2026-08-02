export function hasOlderMessages(total, batchLength) {
  const normalizedTotal = Number(total);
  const normalizedBatchLength = Number(batchLength);
  if (
    !Number.isFinite(normalizedTotal) ||
    !Number.isFinite(normalizedBatchLength) ||
    normalizedTotal < 0 ||
    normalizedBatchLength < 0
  ) {
    return false;
  }
  return normalizedTotal > normalizedBatchLength;
}
