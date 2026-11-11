export type Lang = 'tc' | 'en';
export type Operator = 'KMB' | 'CTB' | 'NLB' | 'GMB' | 'MTR' | 'LRT';

export interface BiText {
  tc: string;
  en: string;
}

/** 各家 API 正規化後的共同格式 */
export interface EtaItem {
  operator: Operator;
  route: string;
  stop: BiText;
  dest: BiText;
  eta: string | null;
  minutes: number | null;
  remark: BiText;
  platform?: string;
  direction?: string;
  scheduled: boolean;
  seq: number;
}

export interface ApiWrapper<T> {
  type?: string;
  version?: string;
  generated_timestamp?: string;
  data: T;
}

export interface EtaQuery {
  operator: Operator;
  route: string;
  stopId: string;
  extra: string;
  lang: Lang;
}

export interface Coords {
  lat: number;
  lng: number;
  label: BiText;
}
