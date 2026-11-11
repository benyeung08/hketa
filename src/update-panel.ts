import { compareVersions, fetchLatestRelease, formatSize } from './api/update';
import type { ReleaseInfo } from './api/update';
import { native } from './native';
import type { Lang } from './types';
import { DEFAULT_OWNER, DEFAULT_REPO, FALLBACK_APP_VERSION, RELEASE_ASSET_NAME } from './version';

type DictKey =
  | 'appSection' | 'currentVersion' | 'supportRange' | 'targetSdk' | 'rangeNote' | 'outsideRange' | 'language' | 'keepAwake'
  | 'updateSection' | 'repoOwner' | 'repoName' | 'autoCheck' | 'save' | 'checkUpdate'
  | 'updateIdle' | 'signingNote'
  | 'checking' | 'latest' | 'found' | 'noAsset' | 'checkFailed'
  | 'downloadUpdate' | 'grantInstall' | 'openRelease'
  | 'downloading' | 'downloadDone' | 'downloadFailed' | 'downloadUnavailable'
  | 'installPermissionNeeded' | 'installRequested' | 'installFallback'
  | 'saved' | 'repoMissing' | 'badgeNew' | 'badgeCurrent'
  | 'browserOnly' | 'grantHint';

const dict: Record<Lang, Record<DictKey, string>> = {
  tc: {
    appSection: '應用程式', currentVersion: '目前版本',
    supportRange: '支援範圍',
    targetSdk: '編譯目標',
    rangeNote: '可安裝於 Android 8.0–17（API 26–37）。minSdk 26、targetSdk 36；Android 17 裝置以向後相容執行，不設 maxSdk。',
    outsideRange: '此裝置為 Android 18+（API 38 以上），超出已驗證範圍；App 仍可執行，但行為未經測試。',
    language: '介面語言',
    keepAwake: '螢幕常亮（Kotlin 原生）',
    updateSection: '線上應用更新', repoOwner: 'GitHub 擁有者', repoName: 'Repository 名稱',
    autoCheck: '啟動時自動檢查更新', save: '儲存設定', checkUpdate: '檢查更新',
    updateIdle: '尚未檢查更新',
    signingNote: '新 APK 必須用同一把 keystore 簽名，否則系統會拒絕安裝。此功能適用於側載／自架發布；上架 Google Play 請改用 Play In-App Updates。',
    checking: '正在向 GitHub 檢查新版本…', latest: '已是最新版本', found: '發現新版本',
    noAsset: '此 Release 沒有 APK 附件，請改用「開啟下載頁」', checkFailed: '檢查失敗',
    downloadUpdate: '下載並安裝', grantInstall: '允許安裝', openRelease: '開啟下載頁',
    downloading: '下載中', downloadDone: '下載完成，準備安裝', downloadFailed: '下載失敗',
    downloadUnavailable: '套用內下載需要 Android 版本', installPermissionNeeded: '請先允許「安裝未知來源」，授權後會自動繼續',
    installRequested: '已交由系統安裝程式，請確認安裝', installFallback: '無法直接安裝，已改開下載頁',
    saved: '設定已儲存', repoMissing: '請填寫 owner 與 repo',
    badgeNew: '有新版本', badgeCurrent: '已安裝', browserOnly: '瀏覽器預覽',
    grantHint: '授權後回到 App 即會自動安裝'
  },
  en: {
    appSection: 'Application', currentVersion: 'Current version',
    supportRange: 'Supported range',
    targetSdk: 'Target SDK',
    rangeNote: 'Installable on Android 8.0–17 (API 26–37). minSdk 26 / targetSdk 36; Android 17 runs it through platform backward compatibility. No maxSdk.',
    outsideRange: 'This device runs Android 18+ (API 38+), beyond the verified range. The app still runs but behaviour is untested.',
    language: 'Language',
    keepAwake: 'Keep screen awake (Kotlin native)',
    updateSection: 'In-app update', repoOwner: 'GitHub owner', repoName: 'Repository name',
    autoCheck: 'Check for updates on launch', save: 'Save', checkUpdate: 'Check for updates',
    updateIdle: 'No update check yet',
    signingNote: 'The new APK must be signed with the SAME keystore or Android will reject the install. This is for sideload / self-distribution; use Play In-App Updates for Google Play.',
    checking: 'Checking GitHub for a new release…', latest: 'You are up to date', found: 'New version available',
    noAsset: 'This release has no APK asset — use "Open download page"', checkFailed: 'Update check failed',
    downloadUpdate: 'Download & install', grantInstall: 'Allow installs', openRelease: 'Open download page',
    downloading: 'Downloading', downloadDone: 'Download complete, launching installer', downloadFailed: 'Download failed',
    downloadUnavailable: 'In-app download requires the Android build', installPermissionNeeded: 'Allow "install unknown apps" first — install resumes when you return',
    installRequested: 'Handed to the system installer, please confirm', installFallback: 'Direct install failed, opened the download page',
    saved: 'Settings saved', repoMissing: 'Owner and repo are required',
    badgeNew: 'Update', badgeCurrent: 'Installed', browserOnly: 'Browser preview',
    grantHint: 'Installation resumes when you return to the app'
  }
};

interface Prefs {
  owner: string;
  repo: string;
  autoCheck: boolean;
  keepAwake: boolean;
}

interface PanelCtx {
  getLang(): Lang;
  setLang(lang: Lang): void;
}

function el<T extends HTMLElement = HTMLElement>(id: string): T {
  const node = document.getElementById(id);
  if (!node) throw new Error(`Missing #${id}`);
  return node as T;
}

const read = (key: string, fallback = ''): string => {
  try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
};
const write = (key: string, value: string): void => {
  try { localStorage.setItem(key, value); } catch { /* storage unavailable */ }
};

function loadPrefs(): Prefs {
  return {
    owner: read('hketa.owner', DEFAULT_OWNER),
    repo: read('hketa.repo', DEFAULT_REPO),
    autoCheck: read('hketa.autoCheck') === '1',
    keepAwake: read('hketa.keepAwake') === '1'
  };
}

export function initUpdatePanel(ctx: PanelCtx): void {
  const L = (key: DictKey): string => dict[ctx.getLang()][key];

  let prefs = loadPrefs();
  let currentVersion = FALLBACK_APP_VERSION;
  let deviceSdk: number | null = null;
  let latest: ReleaseInfo | null = null;
  let isNewer = false;
  let downloadId: number | null = null;
  let pollTimer: number | null = null;
  let pollTicks = 0;
  let installReady = false;
  let pendingInstall = false;
  let statusKey: DictKey | null = 'updateIdle';
  let statusParam = '';
  let statusRaw = '';
  let statusError = false;

  const statusNode = el('update-status');
  const dot = el('update-dot');

  function paintStatus(): void {
    const text = statusKey
      ? L(statusKey) + (statusParam ? ` ${statusParam}` : '')
      : statusRaw;
    statusNode.textContent = text;
    statusNode.classList.toggle('error', statusError);
  }

  function setStatus(key: DictKey, param = '', isError = false): void {
    statusKey = key; statusParam = param; statusRaw = ''; statusError = isError;
    paintStatus();
  }

  function setStatusRaw(text: string, isError = false): void {
    statusKey = null; statusRaw = text; statusParam = ''; statusError = isError;
    paintStatus();
  }

  function refreshText(): void {
    document.querySelectorAll<HTMLElement>('[data-i18n]').forEach((node) => {
      if (node.id === 'update-status') return;
      const key = node.dataset.i18n as DictKey;
      if (key && dict[ctx.getLang()][key]) node.textContent = L(key);
    });
    el<HTMLSelectElement>('lang-select').value = ctx.getLang();
    paintSupportNote();
    paintStatus();
    if (latest) renderRelease(latest);
  }

  function paintSupportNote(): void {
    const note = el('support-note');
    const beyond = deviceSdk !== null && deviceSdk > 37;
    note.textContent = beyond ? L('outsideRange') : L('rangeNote');
    note.classList.toggle('warn', beyond);
  }

  function showDot(show: boolean): void { dot.hidden = !show; }

  function setButtons(): void {
    el<HTMLButtonElement>('open-release').disabled = false;
    el<HTMLButtonElement>('grant-install').disabled = false;
    el<HTMLButtonElement>('download-update').disabled = !(latest && isNewer && latest.assetUrl);
  }

  function renderRelease(rel: ReleaseInfo): void {
    el('release-card').hidden = false;
    el('release-badge').textContent = isNewer ? L('badgeNew') : L('badgeCurrent');
    el('release-badge').classList.toggle('ok-badge', !isNewer);
    el('release-version').textContent = rel.tag || `v${rel.version}`;
    el('release-size').textContent = formatSize(rel.assetSize);
    el('release-name').textContent = rel.name;
    el('release-date').textContent = rel.publishedAt
      ? new Date(rel.publishedAt).toLocaleString(ctx.getLang() === 'tc' ? 'zh-HK' : 'en-GB')
      : '';
    el('release-notes').textContent = rel.notes
      ? (rel.notes.length > 900 ? `${rel.notes.slice(0, 900)}…` : rel.notes)
      : '—';
  }

  function showProgress(p: number): void {
    el('update-progress-wrap').hidden = false;
    el('update-bar').style.width = `${p}%`;
    el('update-progress-text').textContent = `${p}%`;
  }

  function stopPoll(): void {
    if (pollTimer !== null) { window.clearInterval(pollTimer); pollTimer = null; }
  }

  async function openReleasePage(): Promise<void> {
    const url = latest?.htmlUrl
      ?? `https://github.com/${prefs.owner}/${prefs.repo}/releases`;
    try {
      await native.openUrl(url);
    } catch (err) {
      setStatusRaw(`openUrl: ${String(err)}`, true);
    }
  }

  async function doInstall(): Promise<void> {
    if (downloadId === null || !installReady) return;
    const allowed = await native.canInstallUnknown().catch(() => false);
    if (!allowed) {
      pendingInstall = true;
      setStatus('installPermissionNeeded', '', true);
      try { await native.requestInstallPermission(); } catch { /* settings unavailable */ }
      return;
    }
    pendingInstall = false;
    try {
      await native.installApk(downloadId);
      setStatus('installRequested');
    } catch {
      setStatus('installFallback', '', true);
      await openReleasePage();
    }
  }

  async function tick(): Promise<void> {
    if (downloadId === null) return;
    if (++pollTicks > 300) { // ~4.5 min safety net
      stopPoll();
      setStatus('downloadFailed', 'timeout', true);
      await openReleasePage();
      return;
    }
    try {
      const p = await native.downloadProgress(downloadId);
      if (p.state === 'success') {
        stopPoll();
        installReady = true;
        showProgress(100);
        setStatus('downloadDone');
        await doInstall();
      } else if (p.state === 'failed') {
        stopPoll();
        setStatus('downloadFailed', `reason ${p.reason}`, true);
        await openReleasePage();
      } else {
        showProgress(p.progress);
        setStatus('downloading', `${p.progress}%`);
      }
    } catch (err) {
      stopPoll();
      setStatusRaw(`${L('downloadFailed')}: ${String(err)}`, true);
    }
  }

  async function downloadAndInstall(): Promise<void> {
    if (!latest?.assetUrl) return;
    stopPoll();
    installReady = false;
    pendingInstall = false;
    pollTicks = 0;
    const btn = el<HTMLButtonElement>('download-update');
    btn.disabled = true;
    setStatus('downloading');
    showProgress(0);
    try {
      const res = await native.downloadApk(latest.assetUrl, RELEASE_ASSET_NAME);
      downloadId = res.id;
      pollTimer = window.setInterval(() => void tick(), 900);
      await tick();
    } catch (err) {
      stopPoll();
      btn.disabled = false;
      const msg = String(err);
      setStatusRaw(msg.includes('Android build')
        ? `${L('downloadUnavailable')} — ${L('browserOnly')}`
        : `${L('downloadFailed')}: ${msg}`, true);
      await openReleasePage();
    }
  }

  async function checkUpdate(silent = false): Promise<void> {
    setStatus('checking');
    showDot(false);
    try {
      const rel = await fetchLatestRelease(prefs.owner, prefs.repo);
      latest = rel;
      isNewer = compareVersions(rel.version, currentVersion) > 0;
      renderRelease(rel);
      setButtons();
      if (!rel.assetUrl) {
        setStatus('noAsset', '', true);
      } else if (isNewer) {
        setStatus('found', `v${rel.version}`);
        showDot(true);
      } else {
        setStatus('latest', `v${rel.version}`);
      }
    } catch (err) {
      latest = null;
      isNewer = false;
      setButtons();
      setStatusRaw(`${L('checkFailed')}: ${err instanceof Error ? err.message : String(err)}`, true);
      if (!silent) showDot(false);
    }
  }

  // ---- wiring ----
  el<HTMLInputElement>('repo-owner').value = prefs.owner;
  el<HTMLInputElement>('repo-name').value = prefs.repo;
  el<HTMLInputElement>('auto-check').checked = prefs.autoCheck;
  el<HTMLInputElement>('keep-awake').checked = prefs.keepAwake;
  el<HTMLSelectElement>('lang-select').value = ctx.getLang();

  el<HTMLSelectElement>('lang-select').addEventListener('change', (e) => {
    ctx.setLang((e.target as HTMLSelectElement).value as Lang);
  });

  el('save-repo').addEventListener('click', () => {
    const owner = el<HTMLInputElement>('repo-owner').value.trim();
    const repo = el<HTMLInputElement>('repo-name').value.trim();
    if (!owner || !repo) { setStatus('repoMissing', '', true); return; }
    prefs = { ...prefs, owner, repo };
    write('hketa.owner', owner);
    write('hketa.repo', repo);
    setStatus('saved');
  });

  el<HTMLInputElement>('auto-check').addEventListener('change', (e) => {
    prefs = { ...prefs, autoCheck: (e.target as HTMLInputElement).checked };
    write('hketa.autoCheck', prefs.autoCheck ? '1' : '0');
  });

  el<HTMLInputElement>('keep-awake').addEventListener('change', (e) => {
    const enabled = (e.target as HTMLInputElement).checked;
    prefs = { ...prefs, keepAwake: enabled };
    write('hketa.keepAwake', enabled ? '1' : '0');
    void native.keepAwake(enabled).catch(() => { /* browser preview */ });
  });

  el('check-update').addEventListener('click', () => void checkUpdate());
  el('download-update').addEventListener('click', () => void downloadAndInstall());
  el('open-release').addEventListener('click', () => void openReleasePage());
  el('grant-install').addEventListener('click', async () => {
    try {
      await native.requestInstallPermission();
      pendingInstall = installReady;
      setStatusRaw(L('grantHint'));
    } catch (err) {
      setStatusRaw(`requestInstallPermission: ${String(err)}`, true);
    }
  });

  const onResume = (): void => {
    if (pendingInstall && installReady) void doInstall();
  };
  document.addEventListener('resume', onResume);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) onResume(); });

  window.addEventListener('hketa:langchange', refreshText);

  // ---- initial data ----
  void (async () => {
    const info = await native.appVersion().catch(() => null);
    currentVersion = info?.version || FALLBACK_APP_VERSION;
    el('app-version').textContent = info
      ? `${info.version} (build ${info.build})`
      : `${FALLBACK_APP_VERSION} · ${L('browserOnly')}`;

    const device = await native.deviceInfo().catch(() => null);
    deviceSdk = device?.sdk ?? null;
    el('settings-device').textContent = device
      ? `${device.model} · Android ${device.release} (SDK ${device.sdk}) · ${device.package}`
      : `${L('browserOnly')} · Kotlin bridge not loaded`;

    if (prefs.keepAwake) void native.keepAwake(true).catch(() => { /* ignore */ });

    refreshText();
    paintStatus();
    setButtons();

    if (prefs.autoCheck) window.setTimeout(() => void checkUpdate(true), 1200);
  })();
}
