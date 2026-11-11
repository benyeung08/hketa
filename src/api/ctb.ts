import type { ApiWrapper, BiText, EtaItem } from '../types';
import { bi, getJson, isScheduled, minutesFrom } from './http';

const BASE = 'https://rt.data.gov.hk/v2/transport/citybus';
export const CTB_COMPANY = 'CTB';

export interface CtbStop {
  stop: string;
  name_tc: string;
  name_en: string;
  lat: string;
  long: string;
}

interface CtbEtaRow {
  co: string;
  route: string;
  dir: string;
  seq: number;
  stop: string;
  dest_tc: string;
  dest_en: string;
  eta_seq: number;
  eta: string | null;
  rmk_tc: string;
  rmk_en: string;
  data_timestamp: string;
}

export async function ctbStop(stopId: string): Promise<CtbStop | null> {
  const r = await getJson<ApiWrapper<CtbStop>>(`${BASE}/stop/${encodeURIComponent(stopId)}`);
  return r.data ?? null;
}

export async function ctbEta(
  stopId: string,
  route: string,
  company = CTB_COMPANY
): Promise<EtaItem[]> {
  const url = `${BASE}/eta/${encodeURIComponent(company)}/${encodeURIComponent(stopId)}/${encodeURIComponent(route)}`;
  const r = await getJson<ApiWrapper<CtbEtaRow[]>>(url);
  const stop = bi(`站 ${stopId}`, `Stop ${stopId}`);
  return (r.data ?? []).map((x) => toEta(x, stop));
}

function toEta(x: CtbEtaRow, stop: BiText): EtaItem {
  const remark = bi(x.rmk_tc ?? '', x.rmk_en ?? '');
  return {
    operator: 'CTB',
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
