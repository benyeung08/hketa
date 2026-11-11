import type { BiText, EtaItem } from '../types';
import { bi, getJson, minutesFrom } from './http';

const BASE = 'https://rt.data.gov.hk/v2/transport/nlb';

interface NlbEtaRow {
  estimatedArrivalTime?: string;
  routeVariantName?: string;
  departed?: number;
  noGPS?: number;
  wheelChair?: number;
  generateTime?: string;
}

interface NlbEtaResp {
  estimatedArrivals?: NlbEtaRow[];
  message?: string;
}

export interface NlbStop {
  stopId: number | string;
  stopName_c?: string;
  stopName_e?: string;
  latitude?: string;
  longitude?: string;
}

export async function nlbStops(routeId: string): Promise<NlbStop[]> {
  const url = `${BASE}/stop.php?action=list&routeId=${encodeURIComponent(routeId)}`;
  const r = await getJson<{ stops?: NlbStop[] }>(url);
  return r.stops ?? [];
}

export async function nlbEta(
  routeId: string,
  stopId: string,
  language = 'zh'
): Promise<EtaItem[]> {
  const url = `${BASE}/stop.php?action=estimatedArrivals&routeId=${encodeURIComponent(routeId)}`
    + `&stopId=${encodeURIComponent(stopId)}&language=${encodeURIComponent(language)}`;
  const r = await getJson<NlbEtaResp>(url);
  const stop = bi(`站 ${stopId}`, `Stop ${stopId}`);
  return (r.estimatedArrivals ?? [])
    .filter((x) => Boolean(x.estimatedArrivalTime))
    .map((x, i) => {
      const raw = x.estimatedArrivalTime as string;
      const iso = raw.includes('T') ? raw : raw.replace(' ', 'T');
      const withZone = /[+-]\d\d:\d\d$|Z$/.test(iso) ? iso : `${iso}+08:00`;
      const remark: BiText = x.noGPS
        ? bi('未有 GPS 資料', 'No GPS data')
        : bi(x.routeVariantName ?? '', x.routeVariantName ?? '');
      return {
        operator: 'NLB' as const,
        route: routeId,
        stop,
        dest: bi(x.routeVariantName ?? '', x.routeVariantName ?? ''),
        eta: withZone,
        minutes: minutesFrom(withZone),
        remark,
        scheduled: Boolean(x.noGPS),
        seq: i + 1
      };
    });
}
