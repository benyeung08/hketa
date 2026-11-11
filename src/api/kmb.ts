import type { ApiWrapper, BiText, EtaItem } from '../types';
import { bi, getJson, isScheduled, minutesFrom } from './http';

const BASE = 'https://data.etabus.gov.hk/v1/transport/kmb';

export interface KmbRoute {
  route: string;
  bound: string;
  service_type: string;
  orig_tc: string;
  orig_en: string;
  dest_tc: string;
  dest_en: string;
}

export interface KmbStop {
  stop: string;
  name_tc: string;
  name_en: string;
  lat: string;
  long: string;
}

interface KmbEtaRow {
  co: string;
  route: string;
  dir: string;
  service_type: string;
  seq: number;
  dest_tc: string;
  dest_en: string;
  eta: string | null;
  eta_seq: number;
  rmk_tc: string;
  rmk_en: string;
  data_timestamp: string;
}

export async function kmbRoutes(): Promise<KmbRoute[]> {
  const r = await getJson<ApiWrapper<KmbRoute[]>>(`${BASE}/route/`);
  return r.data ?? [];
}

export async function kmbStop(stopId: string): Promise<KmbStop | null> {
  const r = await getJson<ApiWrapper<KmbStop>>(`${BASE}/stop/${encodeURIComponent(stopId)}`);
  return r.data ?? null;
}

export async function kmbStopEta(stopId: string, route?: string): Promise<EtaItem[]> {
  const r = await getJson<ApiWrapper<KmbEtaRow[]>>(`${BASE}/stop-eta/${encodeURIComponent(stopId)}`);
  const stop = bi(`站 ${stopId}`, `Stop ${stopId}`);
  const rows = (r.data ?? []).filter((x) => !route || x.route === route);
  return rows.map((x) => toEta(x, stop));
}

export async function kmbRouteEta(
  stopId: string,
  route: string,
  serviceType = '1'
): Promise<EtaItem[]> {
  const url = `${BASE}/eta/${encodeURIComponent(stopId)}/${encodeURIComponent(route)}/${encodeURIComponent(serviceType)}`;
  const r = await getJson<ApiWrapper<KmbEtaRow[]>>(url);
  const stop = bi(`站 ${stopId}`, `Stop ${stopId}`);
  return (r.data ?? []).map((x) => toEta(x, stop));
}

function toEta(x: KmbEtaRow, stop: BiText): EtaItem {
  const remark = bi(x.rmk_tc ?? '', x.rmk_en ?? '');
  return {
    operator: 'KMB',
    route: x.route,
    stop,
    dest: bi(x.dest_tc ?? '', x.dest_en ?? ''),
    eta: x.eta,
    minutes: minutesFrom(x.eta),
    remark,
    direction: x.dir,
    scheduled: isScheduled(x.rmk_tc, x.rmk_en),
    seq: Number(x.eta_seq ?? 0)
  };
}
