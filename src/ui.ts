import type { BiText, EtaItem, Lang } from './types';

export const t = (lang: Lang, value: BiText): string => (lang === 'tc' ? value.tc : value.en);

export function escapeHtml(value: string): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function clock(iso: string | null, lang: Lang): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString(lang === 'tc' ? 'zh-HK' : 'en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  });
}

function minutesLabel(item: EtaItem, lang: Lang): string {
  if (item.minutes === null) return '—';
  if (item.minutes <= 0) return lang === 'tc' ? '即將到站' : 'Due';
  return lang === 'tc' ? `${item.minutes} 分鐘` : `${item.minutes} min`;
}

export function renderEta(items: EtaItem[], lang: Lang): string {
  if (!items.length) {
    const empty = lang === 'tc' ? '暫無到站資料（可能非服務時間、站號不符或 API 限制）' : 'No ETA right now (off-service, wrong stop ID, or API limit)';
    return `<div class="empty">${escapeHtml(empty)}</div>`;
  }

  return items.map((item) => {
    const meta: string[] = [];
    const time = clock(item.eta, lang);
    if (time) meta.push(time);
    if (item.platform) meta.push(`${lang === 'tc' ? '月台' : 'Plat.'} ${escapeHtml(item.platform)}`);
    const remark = t(lang, item.remark);
    if (remark && remark !== '—') meta.push(escapeHtml(remark));
    if (item.scheduled) {
      meta.push(`<span class="tag-scheduled">${lang === 'tc' ? '預定班次' : 'Scheduled'}</span>`);
    }

    const destText = t(lang, item.dest);
    const destPrefix = /^(往|To )/.test(destText) ? '' : (lang === 'tc' ? '往 ' : 'To ');

    return `
      <article class="eta-card">
        <div class="eta-top">
          <span class="badge">${escapeHtml(item.operator)}</span>
          <span class="route">${escapeHtml(item.route)}</span>
          <span class="mins">${escapeHtml(minutesLabel(item, lang))}</span>
        </div>
        <div class="eta-dest">${escapeHtml(destPrefix)}${escapeHtml(destText)}</div>
        <div class="eta-meta">${meta.join(' · ') || '—'}</div>
      </article>`;
  }).join('');
}
