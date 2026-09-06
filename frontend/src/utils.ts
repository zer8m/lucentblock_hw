// src/utils.ts
export function formatTime(ts_ms: number | string | undefined | null): string {
  if (!ts_ms) return '-';
  const date = new Date(Number(ts_ms));
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${seconds}`;
}

export function formatNumber(value: number | string | undefined | null): string {
  if (value === undefined || value === null) return '0';
  return Number(value).toLocaleString();
}