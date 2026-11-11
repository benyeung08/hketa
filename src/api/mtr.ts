import type { EtaItem } from '../types';
import { bi, getJson, safeNumber } from './http';

const BASE = 'https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php';

export const MTR_LINES = [
  { code: 'TWL', tc: '荃灣綫', en: 'Tsuen Wan Line' },
  { code: 'KTL', tc: '觀塘綫', en: 'Kwun Tong Line' },
  { code: 'ISL', tc: '港島綫', en: 'Island Line' },
  { code: 'SIL', tc: '南港島綫', en: 'South Island Line' },
  { code: 'TKL', tc: '將軍澳綫', en: 'Tseung Kwan O Line' },
  { code: 'TML', tc: '屯馬綫', en: 'Tuen Ma Line' },
  { code: 'EAL', tc: '東鐵綫', en: 'East Rail Line' },
  { code: 'TCL', tc: '東涌綫', en: 'Tung Chung Line' },
  { code: 'AEL', tc: '機場快綫', en: 'Airport Express' },
  { code: 'DRL', tc: '迪士尼綫', en: 'Disneyland Resort Line' }
] as const;

/** 常用車站代碼；其餘代碼請直接輸入官方代碼。 */
export const MTR_STATION_PRESETS = [
  { code: 'CEN', tc: '中環', en: 'Central' },
  { code: 'ADM', tc: '金鐘', en: 'Admiralty' },
  { code: 'TST', tc: '尖沙咀', en: 'Tsim Sha Tsui' },
  { code: 'MOK', tc: '旺角', en: 'Mong Kok' },
  { code: 'TKO', tc: '將軍澳', en: 'Tseung Kwan O' },
  { code: 'SUN', tc: '欣澳', en: 'Sunny Bay' }
] as const;

interface MtrTrain {
  ttnt: string;
  valid: string;
  plat: string;
  time: string;
  source: string;
  dest: string;
  seq: string;
}

interface MtrNode {
  curr_time?: string;
  sys_time?: string;
  UP?: MtrTrain[];
  DOWN?: MtrTrain[];
}

interface MtrResp {
  sys_time?: string;
  curr_time?: string;
  data?: Record<string, MtrNode>;
  message?: string;
}

export async function mtrNextTrain(
  line: string,
  station: string,
  lang: 'tc' | 'en' = 'tc'
): Promise<EtaItem[]> {
  const url = `${BASE}?line=${encodeURIComponent(line)}&sta=${encodeURIComponent(station)}`
    + `&lang=${lang === 'tc' ? 'TC' : 'EN'}`;
  const r = await getJson<MtrResp>(url);
  const nodes = r.data ?? {};
  const node = nodes[`${line}-${station}`] ?? Object.values(nodes)[0];
  if (!node) return [];

  const lineInfo = MTR_LINES.find((x) => x.code === line);
  const stop = bi(`${station} ${lineInfo?.tc ?? ''}`.trim(), `${station} ${lineInfo?.en ?? ''}`.trim());
  const rows = [...(node.UP ?? []), ...(node.DOWN ?? [])];

  return rows
    .filter((x) => x.valid === 'Y')
    .map((x, i) => ({
      operator: 'MTR' as const,
      route: line,
      stop,
      dest: bi(`往 ${x.dest}`, `To ${x.dest}`),
      eta: null,
      minutes: safeNumber(x.ttnt),
      remark: bi(`${node.UP?.includes(x) ? '上行' : '下行'} · ${x.ttnt} 分鐘`, `${node.UP?.includes(x) ? 'UP' : 'DOWN'} · ${x.ttnt} min`),
      platform: x.plat,
      direction: node.UP?.includes(x) ? 'UP' : 'DOWN',
      scheduled: false,
      seq: Number(x.seq ?? i + 1)
    }));
}
