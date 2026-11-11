import { RELEASE_ASSET_NAME } from '../version';
import { getJson } from './http';

export interface ReleaseInfo {
  tag: string;
  version: string;
  name: string;
  notes: string;
  publishedAt: string | null;
  htmlUrl: string;
  assetUrl: string | null;
  assetName: string | null;
  assetSize: number;
}

interface GhAsset {
  name?: string;
  browser_download_url?: string;
  size?: number;
  content_type?: string;
}

interface GhRelease {
  tag_name?: string;
  name?: string;
  body?: string;
  published_at?: string;
  html_url?: string;
  draft?: boolean;
  prerelease?: boolean;
  assets?: GhAsset[];
}

const API = 'https://api.github.com';

/** 把 tag 轉成可比較的版本號：v1.2.3-rc1 → 1.2.3 */
export function normaliseVersion(raw: string): string {
  return raw.trim().replace(/^[vV]/, '').split('-')[0].replace(/[^0-9.]/g, '');
}

/** 單純的三段數字比對；a > b 回 1，a < b 回 -1，相等回 0。 */
export function compareVersions(a: string, b: string): number {
  const pa = normaliseVersion(a).split('.').map((n) => Number(n) || 0);
  const pb = normaliseVersion(b).split('.').map((n) => Number(n) || 0);
  for (let i = 0; i < 3; i++) {
    const x = pa[i] ?? 0;
    const y = pb[i] ?? 0;
    if (x > y) return 1;
    if (x < y) return -1;
  }
  return 0;
}

export async function fetchLatestRelease(owner: string, repo: string): Promise<ReleaseInfo> {
  const o = owner.trim();
  const r = repo.trim();
  if (!/^[A-Za-z0-9_.-]+$/.test(o) || !/^[A-Za-z0-9_.-]+$/.test(r)) {
    throw new Error('Invalid GitHub owner / repo');
  }
  const raw = await getJson<GhRelease>(`${API}/repos/${encodeURIComponent(o)}/${encodeURIComponent(r)}/releases/latest`, 10000);

  const assets = raw.assets ?? [];
  const preferred = assets.find((a) => a.name === RELEASE_ASSET_NAME)
    ?? assets.find((a) => (a.name ?? '').toLowerCase().endsWith('.apk'))
    ?? null;

  return {
    tag: raw.tag_name ?? '',
    version: normaliseVersion(raw.tag_name ?? ''),
    name: (raw.name || raw.tag_name || 'Release').trim(),
    notes: (raw.body ?? '').trim(),
    publishedAt: raw.published_at ?? null,
    htmlUrl: raw.html_url ?? `https://github.com/${o}/${r}/releases`,
    assetUrl: preferred?.browser_download_url ?? null,
    assetName: preferred?.name ?? null,
    assetSize: Number(preferred?.size ?? 0)
  };
}

export function formatSize(bytes: number): string {
  if (!bytes) return '';
  return bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toFixed(1)} MB`
    : `${Math.round(bytes / 1024)} KB`;
}
