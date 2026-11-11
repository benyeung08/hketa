import type { EtaItem } from '../types';
import { bi, getJson, safeNumber } from './http';

const BASE = 'https://rt.data.gov.hk/v1/transport/mtr/lrt/getSchedule';

interface LrtRow {
  route_no: string;
  dest_ch: string;
  dest_en: string;
  time_ch: string;
  time_en: string;
  train_length?: number;
  special?: number;
  arrival_departure?: string;
}

interface LrtResp {
  platform_list?: Array<{ platform_id?: string; route_list?: LrtRow[] }>;
}

export async function lrtNextTrain(stationId: string, withSpecial = '1'): Promise<EtaItem[]> {
  const url = `${BASE}?station_id=${encodeURIComponent(stationId)}&with_special=${encodeURIComponent(withSpecial)}`;
  const r = await getJson<LrtResp>(url);
  const rows = (r.platform_list ?? []).flatMap((p) => p.route_list ?? []);
  const stop = bi(`輕鐵站 ${stationId}`, `LR station ${stationId}`);

  return rows.map((x, i) => {
    const minutes = safeNumber(x.time_en);
    return {
      operator: 'LRT' as const,
      route: x.route_no,
      stop,
      dest: bi(`往 ${x.dest_ch}`, `To ${x.dest_en}`),
      eta: null,
      minutes,
      remark: bi(x.time_ch || '—', x.time_en || '—'),
      scheduled: x.special === 1,
      seq: i + 1
    };
  });
}
