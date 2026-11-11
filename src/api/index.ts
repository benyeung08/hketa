import type { Coords, EtaItem, EtaQuery } from '../types';
import { bi } from './http';
import { ctbEta, ctbStop } from './ctb';
import { gmbStopEta } from './gmb';
import { kmbRouteEta, kmbStop } from './kmb';
import { lrtNextTrain } from './lrt';
import { mtrNextTrain } from './mtr';
import { nlbEta, nlbStops } from './nlb';

export async function queryEta(q: EtaQuery): Promise<EtaItem[]> {
  const route = q.route.trim();
  const stop = q.stopId.trim();
  const extra = q.extra.trim();

  switch (q.operator) {
    case 'KMB':
      return kmbRouteEta(stop, route, extra || '1');
    case 'CTB': {
      const rows = await ctbEta(stop, route);
      const dir = extra.toUpperCase();
      return dir ? rows.filter((x) => (x.direction ?? '').toUpperCase() === dir) : rows;
    }
    case 'NLB':
      return nlbEta(route, stop, extra || 'zh');
    case 'GMB':
      return gmbStopEta(route, stop);
    case 'MTR':
      return mtrNextTrain(route, stop, q.lang);
    case 'LRT':
      return lrtNextTrain(stop, extra || '1');
  }
}

/** 儘量從官方 /stop API 取座標，給 Kotlin 的 geo: Intent 用。 */
export async function lookupCoords(q: EtaQuery): Promise<Coords | null> {
  const stop = q.stopId.trim();
  try {
    if (q.operator === 'KMB') {
      const s = await kmbStop(stop);
      if (s?.lat && s.long) {
        return { lat: Number(s.lat), lng: Number(s.long), label: bi(s.name_tc ?? '', s.name_en ?? '') };
      }
    }
    if (q.operator === 'CTB') {
      const s = await ctbStop(stop);
      if (s?.lat && s.long) {
        return { lat: Number(s.lat), lng: Number(s.long), label: bi(s.name_tc ?? '', s.name_en ?? '') };
      }
    }
    if (q.operator === 'NLB') {
      const list = await nlbStops(q.route.trim());
      const s = list.find((x) => String(x.stopId) === stop);
      if (s?.latitude && s.longitude) {
        return { lat: Number(s.latitude), lng: Number(s.longitude), label: bi(s.stopName_c ?? '', s.stopName_e ?? '') };
      }
    }
  } catch {
    return null;
  }
  return null;
}
