import type { BiText } from '../types';

export async function getJson<T>(url: string, timeoutMs = 9000): Promise<T> {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
      cache: 'no-store'
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${res.statusText} — ${url}`);
    }
    return (await res.json()) as T;
  } finally {
    window.clearTimeout(timer);
  }
}

export const bi = (tc: string, en: string): BiText => ({
  tc: tc || en,
  en: en || tc
});

export function minutesFrom(iso: string | null, now = Date.now()): number | null {
  if (!iso) return null;
  const normalized = iso.includes('T') ? iso : iso.replace(' ', 'T');
  const t = Date.parse(normalized);
  if (Number.isNaN(t)) return null;
  return Math.max(0, Math.round((t - now) / 60000));
}

export function isScheduled(...remarks: string[]): boolean {
  const text = remarks.join(' ').toLowerCase();
  return text.includes('預定') || text.includes('scheduled');
}

export function safeNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const n = Number(value.replace(/[^0-9.-]/g, ''));
    return Number.isFinite(n) ? n : null;
  }
  return null;
}
