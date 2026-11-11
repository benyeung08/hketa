import { lookupCoords, queryEta } from './api';
import { native } from './native';
import { renderEta, t } from './ui';
import { initUpdatePanel } from './update-panel';
import type { EtaItem, EtaQuery, Lang, Operator } from './types';

function el<T extends HTMLElement = HTMLElement>(id: string): T {
  const node = document.getElementById(id);
  if (!node) throw new Error(`Missing #${id}`);
  return node as T;
}

interface Hint {
  labelTc: string;
  labelEn: string;
  tc: string;
  en: string;
}

const hints: Record<Operator, Hint> = {
  KMB: {
    labelTc: '服務類型', labelEn: 'Service type',
    tc: '例：40 / 647AB0AD358F10DF / 1（正線）。站 ID 可由 /stop 取得。',
    en: 'e.g. 40 / 647AB0AD358F10DF / 1 (normal). Get stop IDs from /stop.'
  },
  CTB: {
    labelTc: '方向（選填）', labelEn: 'Direction (opt.)',
    tc: 'company_id 固定 CTB（新巴路線已併入）；方向填 O 或 I 可篩選。',
    en: 'company_id is always CTB; enter O or I to filter direction.'
  },
  NLB: {
    labelTc: '語言', labelEn: 'Language',
    tc: 'route 填 NLB routeId、站號填 stopId；語言填 zh / en / cn。',
    en: 'route = NLB routeId, stop = stopId; language = zh / en / cn.'
  },
  GMB: {
    labelTc: '區域（選填）', labelEn: 'Region (opt.)',
    tc: 'route 填 route_id；可先用 /route/{HKI|KLN|NT}/{路線} 查 ID。',
    en: 'route = route_id; resolve it via /route/{HKI|KLN|NT}/{code}.'
  },
  MTR: {
    labelTc: '語言', labelEn: 'Language',
    tc: '路線填線路代碼（TML、TKL…），站號填車站代碼（ADM、CEN…）；TC / EN。',
    en: 'route = line code (TML, TKL…), stop = station code (ADM, CEN…); TC / EN.'
  },
  LRT: {
    labelTc: '含特別班次', labelEn: 'Special routes',
    tc: '路線可留空；站號填 station_id（例 600）；附加填 1 含特別路線。',
    en: 'route can be blank; stop = station_id (e.g. 600); 1 includes special routes.'
  }
};

const state: {
  lang: Lang;
  operator: Operator;
  lastQuery: EtaQuery | null;
  lastItems: EtaItem[];
} = {
  lang: (() => { try { return localStorage.getItem('hketa.lang') === 'en' ? 'en' : 'tc'; } catch { return 'tc'; } })(),
  operator: 'KMB',
  lastQuery: null,
  lastItems: []
};

function setLang(lang: Lang): void {
  state.lang = lang;
  try { localStorage.setItem('hketa.lang', lang); } catch { /* storage unavailable */ }
  applyLang();
  window.dispatchEvent(new CustomEvent('hketa:langchange'));
}

function switchView(view: 'eta' | 'settings'): void {
  el('view-eta').hidden = view !== 'eta';
  el('view-settings').hidden = view !== 'settings';
  el('tab-eta').classList.toggle('active', view === 'eta');
  el('tab-settings').classList.toggle('active', view === 'settings');
}

function applyLang(): void {
  document.querySelectorAll<HTMLElement>('[data-tc][data-en]').forEach((node) => {
    node.textContent = state.lang === 'tc' ? (node.dataset.tc ?? '') : (node.dataset.en ?? '');
  });
  el('lang-toggle').textContent = state.lang === 'tc' ? 'EN' : '繁';
  const h = hints[state.operator];
  el('hint').textContent = t(state.lang, { tc: h.tc, en: h.en });
  el('extra-label').textContent = t(state.lang, { tc: h.labelTc, en: h.labelEn });
  el<HTMLInputElement>('extra-input').placeholder = t(state.lang, { tc: '選填', en: 'Optional' });
  renderResults();
}

function renderResults(): void {
  el('results').innerHTML = renderEta(state.lastItems, state.lang);
}

function setStatus(text: string, isError = false): void {
  const node = el('status');
  node.textContent = text;
  node.classList.toggle('error', isError);
}

function collectQuery(): EtaQuery {
  return {
    operator: state.operator,
    route: el<HTMLInputElement>('route-input').value.trim(),
    stopId: el<HTMLInputElement>('stop-input').value.trim(),
    extra: el<HTMLInputElement>('extra-input').value.trim(),
    lang: state.lang
  };
}

async function runQuery(): Promise<void> {
  const q = collectQuery();
  if (!q.route || !q.stopId) {
    setStatus(state.lang === 'tc' ? '請先填路線與站號' : 'Route and stop ID are required', true);
    return;
  }
  state.lastQuery = q;
  const btn = el<HTMLButtonElement>('search-btn');
  btn.disabled = true;
  setStatus(state.lang === 'tc' ? '讀取即時到站資料…' : 'Fetching live ETA…');
  try {
    const items = await queryEta(q);
    state.lastItems = items.sort((a, b) => (a.minutes ?? 9999) - (b.minutes ?? 9999));
    renderResults();
    setStatus(
      state.lang === 'tc' ? `共 ${items.length} 班 · ${new Date().toLocaleTimeString('zh-HK')}` : `${items.length} arrivals · ${new Date().toLocaleTimeString('en-GB')}`
    );
  } catch (err) {
    state.lastItems = [];
    renderResults();
    setStatus(`${state.lang === 'tc' ? '讀取失敗' : 'Request failed'}: ${err instanceof Error ? err.message : String(err)}`, true);
  } finally {
    btn.disabled = false;
  }
}

function wireEvents(): void {
  el<HTMLFormElement>('eta-form').addEventListener('submit', (e) => {
    e.preventDefault();
    void runQuery();
  });

  el('refresh-btn').addEventListener('click', () => void runQuery());

  el('lang-toggle').addEventListener('click', () => setLang(state.lang === 'tc' ? 'en' : 'tc'));

  el('tab-eta').addEventListener('click', () => switchView('eta'));
  el('tab-settings').addEventListener('click', () => switchView('settings'));

  el('op-chips').querySelectorAll<HTMLButtonElement>('.chip').forEach((chip) => {
    chip.addEventListener('click', () => {
      state.operator = (chip.dataset.op ?? 'KMB') as Operator;
      el('op-chips').querySelectorAll('.chip').forEach((c) => c.classList.toggle('active', c === chip));
      applyLang();
    });
  });

  el('map-btn').addEventListener('click', async () => {
    const q = state.lastQuery ?? collectQuery();
    const coords = await lookupCoords(q);
    const lat = coords?.lat ?? 22.3193;
    const lng = coords?.lng ?? 114.1694;
    const label = coords ? t(state.lang, coords.label) : (state.lang === 'tc' ? '香港' : 'Hong Kong');
    try {
      await native.openMap(lat, lng, label);
      setStatus(state.lang === 'tc' ? `已交由原生地圖：${label}` : `Opened native map: ${label}`);
    } catch (err) {
      setStatus(`${state.lang === 'tc' ? '無法開啟地圖' : 'Cannot open map'}: ${String(err)}`, true);
    }
  });
}

function whenReady(): Promise<void> {
  const hasCordova = typeof (window as unknown as { cordova?: unknown }).cordova !== 'undefined';
  if (!hasCordova) {
    return new Promise((resolve) => {
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => resolve(), { once: true });
      } else {
        resolve();
      }
    });
  }
  return new Promise((resolve) => {
    let done = false;
    const finish = () => {
      if (!done) { done = true; resolve(); }
    };
    document.addEventListener('deviceready', finish, { once: true });
    window.setTimeout(finish, 3000);
  });
}

async function boot(): Promise<void> {
  applyLang();
  wireEvents();
  switchView('eta');
  const info = await native.deviceInfo().catch(() => null);
  el('device-line').textContent = info
    ? `${info.model} · Android ${info.release} (SDK ${info.sdk}) · Kotlin bridge OK`
    : (state.lang === 'tc' ? '瀏覽器預覽 · Kotlin bridge 未載入' : 'Browser preview · Kotlin bridge not loaded');

  initUpdatePanel({ getLang: () => state.lang, setLang });
}

whenReady().then(() => void boot()).catch((err: unknown) => console.error('[hketa] boot failed', err));
