/**
 * HK Bus ETA service — aggregates official open data feeds into a unified shape.
 *
 * Data sources (no API key required):
 *  - KMB : https://data.etabus.gov.hk/v1/transport/kmb
 *  - Citybus / NLB : https://rt.data.gov.hk/v1/transport/citybus-nwfb
 *
 * This module is plain TypeScript / Node — it has no Express dependency, so the
 * same fetch logic can be unit-tested or reused from the Android side via a
 * shared contract (see `types/eta.ts`).
 */

export type Operator = 'KMB' | 'CTB' | 'NLB';

export interface EtaEntry {
  operator: Operator;
  route: string;
  direction: 'inbound' | 'outbound';
  destination: string;
  /** ISO-8601 timestamp of the predicted arrival, in HK time (+08:00). null = scheduled only. */
  eta: string | null;
  /** Human readable "X 分鐘" / "X mins". */
  remark?: string;
  /** Sequence of this prediction (1 = next bus). */
  seq: number;
}

export interface EtaResponse {
  stopId: string;
  generatedAt: string;
  entries: EtaEntry[];
}

const KMB_BASE = 'https://data.etabus.gov.hk/v1/transport/kmb';
const CTB_BASE = 'https://rt.data.gov.hk/v1/transport/citybus-nwfb';

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Upstream ${url} responded ${res.status}`);
  }
  return (await res.json()) as T;
}

function toIso(dateStr: string | undefined | null): string | null {
  if (!dateStr) return null;
  // KMB returns full ISO strings; Citybus returns "YYYY-MM-DD HH:mm:ss".
  if (dateStr.includes(' ')) {
    return new Date(dateStr.replace(' ', 'T') + '+08:00').toISOString();
  }
  return new Date(dateStr).toISOString();
}

/* ----------------------------- KMB ----------------------------- */

interface KmbEtaRaw {
  co: string;
  route: string;
  dir: string;
  dest_tc?: string;
  dest_en?: string;
  eta: string | null;
  rmk_tc?: string;
  rmk_en?: string;
  eta_seq: number;
}

async function fetchKmb(stopId: string): Promise<EtaEntry[]> {
  const url = `${KMB_BASE}/stop-eta/${encodeURIComponent(stopId)}`;
  const json = await getJson<{ data: KmbEtaRaw[] }>(url);
  return (json.data ?? []).map((d) => ({
    operator: 'KMB',
    route: d.route,
    direction: d.dir === 'I' ? 'inbound' : 'outbound',
    destination: d.dest_tc || d.dest_en || '',
    eta: toIso(d.eta),
    remark: d.rmk_tc || d.rmk_en,
    seq: d.eta_seq,
  }));
}

/* --------------------------- Citybus --------------------------- */

interface CtbStopRouteRaw {
  co: string;
  route: string;
  dir: string;
}

interface CtbEtaRaw {
  co: string;
  route: string;
  dir: string;
  dest_tc?: string;
  dest_en?: string;
  eta: string | null;
  rmk_tc?: string;
  rmk_en?: string;
  eta_seq: number;
}

/**
 * Citybus/NLB requires a two-step lookup: first list routes serving the stop,
 * then fetch ETA for each (route, direction) pair.
 */
async function fetchCitybus(stopId: string): Promise<EtaEntry[]> {
  const listUrl = `${CTB_BASE}/route-stop/${encodeURIComponent(stopId)}/1`;
  const list = await getJson<{ data: CtbStopRouteRaw[] }>(listUrl);

  const pairs = (list.data ?? []).map((r) => ({
    route: r.route,
    dir: r.dir === 'I' ? 'inbound' : 'outbound',
    co: r.co === 'NWFB' ? 'CTB' : (r.co as Operator),
  }));

  const results = await Promise.all(
    pairs.map(async (p) => {
      try {
        const url = `${CTB_BASE}/eta/${encodeURIComponent(stopId)}/${encodeURIComponent(
          p.route,
        )}/${p.dir === 'inbound' ? 'inbound' : 'outbound'}`;
        const json = await getJson<{ data: CtbEtaRaw[] }>(url);
        return (json.data ?? []).map<EtaEntry>((d) => ({
          operator: p.co,
          route: d.route,
          direction: p.dir,
          destination: d.dest_tc || d.dest_en || '',
          eta: toIso(d.eta),
          remark: d.rmk_tc || d.rmk_en,
          seq: d.eta_seq,
        }));
      } catch {
        return [] as EtaEntry[];
      }
    }),
  );

  return results.flat();
}

/* --------------------------- Public --------------------------- */

/**
 * Fetch ETAs across all supported operators and merge them.
 * Pass `operators` to limit the lookup (e.g. only KMB for faster responses).
 */
export async function getEtas(
  stopId: string,
  operators: Operator[] = ['KMB', 'CTB', 'NLB'],
): Promise<EtaResponse> {
  const tasks: Promise<EtaEntry[]>[] = [];
  if (operators.includes('KMB')) tasks.push(fetchKmb(stopId).catch(() => [] as EtaEntry[]));
  if (operators.includes('CTB') || operators.includes('NLB'))
    tasks.push(fetchCitybus(stopId).catch(() => [] as EtaEntry[]));

  const batches = await Promise.all(tasks);
  const entries = batches
    .flat()
    .sort((a, b) => a.seq - b.seq || (a.eta ?? '').localeCompare(b.eta ?? ''));

  return {
    stopId,
    generatedAt: new Date().toISOString(),
    entries,
  };
}
