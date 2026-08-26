export type PageResult<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function pageNumber(value: string | null, fallback = 0) {
  const parsed = Number(value ?? fallback);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

export function pageSize(value: string | null, fallback = 25) {
  return Math.min(100, Math.max(1, pageNumber(value, fallback)));
}

export function lastPage(total: number, size: number) {
  return Math.max(0, Math.ceil(total / Math.max(1, size)) - 1);
}

export function pageRange(page: number, size: number, total: number) {
  const start = page * size;
  return {
    from: total === 0 || start >= total ? 0 : start + 1,
    to: start >= total ? 0 : Math.min(start + size, total),
    pages: Math.max(1, Math.ceil(total / size)),
  };
}
