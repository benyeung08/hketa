import { ALLOWED_RELEASE_HOSTS, MAX_APK_BYTES, RELEASE_ASSET_NAME, REPO_NAME_RE } from '../version';
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

/** 只接受 GitHub 官方下載 host，且必須是 https。 */
export function isAllowedReleaseUrl(raw: string): boolean {
  try {
    const u = new URL(raw);
    return u.protocol === 'https:' &&
      ALLOWED_RELEASE_HOSTS.some((h) => u.hostname === h || u.hostname.endsWith(`.${h}`));
  } catch {
    return false;
  }
}

/** 三段數字比對；帶 -rc/-beta 後綴者視為比同號正式版舊。 */
export function compareVersions(a: string, b: string): number {
  const pa = normaliseVersion(a).split('.').map((n) => Number(n) || 0);
  const pb = normaliseVersion(b).split('.').map((n) => Number(n) || 0);
  for (let i = 0; i < 3; i++) {
    const x = pa[i] ?? 0;
    const y = pb[i] ?? 0;
    if (x > y) return 1;
    if (x < y) return -1;
  }
  const preA = /-/.test(a.trim().replace(/^[vV]/, '')) ? 1 : 0;
  const preB = /-/.test(b.trim().replace(/^[vV]/, '')) ? 1 : 0;
  if (preA === preB) return 0;
  return preA < preB ? 1 : -1;
}

export async function fetchLatestRelease(owner: string, repo: string): Promise<ReleaseInfo> {
  const o = owner.trim();
  const r = repo.trim();
  if (!REPO_NAME_RE.test(o) || !REPO_NAME_RE.test(r)) {
    throw new Error('Invalid GitHub owner / repo');
  }
  const raw = await getJson<GhRelease>(`${API}/repos/${encodeURIComponent(o)}/${encodeURIComponent(r)}/releases/latest`, 10000);

  const assets = raw.assets ?? [];
  const candidates = assets
    .filter((a) => (a.name ?? '').toLowerCase().endsWith('.apk'))
    .filter((a) => isAllowedReleaseUrl(a.browser_download_url ?? ''))
    .filter((a) => {
      const size = Number(a.size ?? 0);
      return size > 0 && size <= MAX_APK_BYTES;
    });
  const preferred = candidates.find((a) => a.name === RELEASE_ASSET_NAME) ?? candidates[0] ?? null;

  // html_url 也只信任 github.com，避免 API 回傳被改寫的導向網址。
  let htmlUrl = `https://github.com/${o}/${r}/releases`;
  try {
    const u = new URL(raw.html_url ?? '');
    if (u.protocol === 'https:' && u.hostname === 'github.com') htmlUrl = u.toString();
  } catch { /* keep the safe fallback */ }

  return {
    tag: raw.tag_name ?? '',
    version: normaliseVersion(raw.tag_name ?? ''),
    name: (raw.name || raw.tag_name || 'Release').trim(),
    notes: (raw.body ?? '').trim(),
    publishedAt: raw.published_at ?? null,
    htmlUrl,
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
