import type { ApiWrapper, EtaItem } from '../types';
import { bi, getJson, minutesFrom, safeNumber } from './http';

const BASE = 'https://data.etagmb.gov.hk';

interface GmbRouteInfo {
  route_id: number;
  route_code: string;
  region: string;
  description_tc?: string;
  description_en?: string;
}

interface GmbEtaRow {
  eta_seq: number;
  diff: number | string;
  timestamp: string;
  remarks_tc?: string;
  remarks_en?: string;
}

interface GmbEtaBlock {
  enabled?: boolean;
  eta?: GmbEtaRow[];
  description_tc?: string;
  description_en?: string;
  route_id?: number;
  route_seq?: number;
}

export async function gmbRoute(region: string, routeCode: string): Promise<GmbRouteInfo[]> {
  const r = await getJson<ApiWrapper<{ routes?: GmbRouteInfo[] } | GmbRouteInfo[]>>(
    `${BASE}/route/${encodeURIComponent(region)}/${encodeURIComponent(routeCode)}`
  );
  const data = r.data as { routes?: GmbRouteInfo[] } | GmbRouteInfo[];
  return Array.isArray(data) ? data : (data.routes ?? []);
}

export async function gmbStopEta(routeId: string, stopId: string): Promise<EtaItem[]> {
  const r = await getJson<ApiWrapper<GmbEtaBlock | GmbEtaBlock[]>>(
    `${BASE}/eta/route-stop/${encodeURIComponent(routeId)}/${encodeURIComponent(stopId)}`
  );
  const blocks: GmbEtaBlock[] = Array.isArray(r.data) ? r.data : [r.data];
  const stop = bi(`站 ${stopId}`, `Stop ${stopId}`);
  const items: EtaItem[] = [];

  for (const block of blocks) {
    const rows = block.enabled === false ? [] : (block.eta ?? []);
    for (const row of rows) {
      const remark = bi(block.description_tc ?? row.remarks_tc ?? '', block.description_en ?? row.remarks_en ?? '');
      items.push({
        operator: 'GMB',
        route: routeId,
        stop,
        dest: remark,
        eta: row.timestamp ?? null,
        minutes: safeNumber(row.diff) ?? minutesFrom(row.timestamp ?? null),
        remark,
        scheduled: false,
        seq: Number(row.eta_seq ?? 0)
      });
    }
  }

  return items;
}
