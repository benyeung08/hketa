/**
 * Shared contract types between the TypeScript API and the Kotlin client.
 * Keep these in sync with com/hketa/app/data/model on the Android side.
 */

/* ---------------- ETA (existing) ---------------- */

export type Operator = 'KMB' | 'CTB' | 'NLB';

export interface EtaEntry {
  operator: Operator;
  route: string;
  direction: 'inbound' | 'outbound';
  destination: string;
  eta: string | null;
  remark?: string;
  seq: number;
}

export interface EtaResponse {
  stopId: string;
  generatedAt: string;
  entries: EtaEntry[];
}

/* ---------------- Transit route catalogue ---------------- */

export type TransitMode = 'BUS' | 'MINIBUS' | 'MTR' | 'LRT';

/** A single route catalogue record. */
export interface RouteInfo {
  id: string;
  mode: TransitMode;
  operator: string;
  name: string;
  origin: string;
  destination: string;
  fare: number;
  fareCurrency: 'HKD';
  fullFare: number;
  /** Service interval in minutes. */
  interval: number;
  /** First / last departure times in "HH:mm" (Hong Kong local time). */
  firstDeparture: string;
  lastDeparture: string;
  /** ISO-8601 timestamp of the last successful refresh from upstream. */
  updatedAt: string;
}

/** A stop along a route, in order. */
export interface RouteStop {
  routeId: string;
  seq: number;
  stopId: string;
  stopName: string;
  /** Distance from the previous stop, in metres. */
  distanceM?: number;
}

export interface RouteWithStops extends RouteInfo {
  stops: RouteStop[];
}

/* ---------------- Update / version manifest ---------------- */

export interface DataVersion {
  mode: TransitMode;
  /** Monotonic sequence; clients compare to decide whether to re-download. */
  version: number;
  /** ISO-8601 timestamp of when this dataset was generated. */
  generatedAt: string;
  /** SHA-256 of the canonical JSON payload, for integrity checks. */
  checksum: string;
  /** Approximate byte size of the gzipped payload. */
  size: number;
}

export interface UpdateManifest {
  generatedAt: string;
  versions: DataVersion[];
}

export interface UpdateResult {
  mode: TransitMode;
  status: 'up-to-date' | 'updated' | 'failed';
  fromVersion: number;
  toVersion: number;
  message?: string;
}
