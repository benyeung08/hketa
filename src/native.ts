import { FALLBACK_APP_VERSION } from './version';

export interface DeviceInfo {
  model: string;
  sdk: number;
  release: string;
  package: string;
}

export interface AppVersion {
  version: string;
  build: number;
}

export interface DownloadProgress {
  status: number;
  state: 'success' | 'failed' | 'running' | 'unknown';
  progress: number;
  bytes: number;
  total: number;
  reason: number;
  uri: string | null;
}

interface NativeBridge {
  deviceInfo(ok: (info: DeviceInfo) => void, fail: (message: string) => void): void;
  appVersion(ok: (info: AppVersion) => void, fail: (message: string) => void): void;
  openMap(
    lat: number,
    lng: number,
    label: string,
    ok: () => void,
    fail: (message: string) => void
  ): void;
  toast(message: string, ok: () => void, fail: (message: string) => void): void;
  keepAwake(enable: boolean, ok: () => void, fail: (message: string) => void): void;
  canInstallUnknown(ok: (res: { allowed: boolean }) => void, fail: (message: string) => void): void;
  requestInstallPermission(ok: () => void, fail: (message: string) => void): void;
  downloadApk(
    url: string,
    fileName: string,
    ok: (res: { id: number }) => void,
    fail: (message: string) => void
  ): void;
  downloadProgress(
    id: number,
    ok: (res: DownloadProgress) => void,
    fail: (message: string) => void
  ): void;
  installApk(id: number, ok: () => void, fail: (message: string) => void): void;
  openUrl(url: string, ok: () => void, fail: (message: string) => void): void;
}

const browserBridge: NativeBridge = {
  deviceInfo: (_ok, fail) => fail('Browser preview: no Cordova plugin'),
  appVersion: (ok) => ok({ version: FALLBACK_APP_VERSION, build: 0 }),
  openMap: (lat, lng, label, ok) => {
    console.info(`[hketa] geo intent preview: ${label} @ ${lat},${lng}`);
    ok();
  },
  toast: (message, ok) => {
    console.info(`[hketa] toast: ${message}`);
    ok();
  },
  keepAwake: (_enable, ok) => ok(),
  canInstallUnknown: (ok) => ok({ allowed: false }),
  requestInstallPermission: (_ok, fail) => fail('Android settings only'),
  downloadApk: (url, _name, _ok, fail) => {
    console.info(`[hketa] download preview: ${url}`);
    fail('In-app APK download requires the Android build');
  },
  downloadProgress: (_id, _ok, fail) => fail('Android only'),
  installApk: (_id, _ok, fail) => fail('Android only'),
  openUrl: (url, ok) => {
    window.open(url, '_blank', 'noopener,noreferrer');
    ok();
  }
};

function bridge(): NativeBridge {
  const w = window as unknown as { HkEtaNative?: NativeBridge };
  return w.HkEtaNative ?? browserBridge;
}

export const native = {
  deviceInfo: () => new Promise<DeviceInfo>((resolve, reject) => bridge().deviceInfo(resolve, reject)),
  appVersion: () => new Promise<AppVersion>((resolve, reject) => bridge().appVersion(resolve, reject)),
  openMap: (lat: number, lng: number, label: string) =>
    new Promise<void>((resolve, reject) => bridge().openMap(lat, lng, label, resolve, reject)),
  toast: (message: string) =>
    new Promise<void>((resolve, reject) => bridge().toast(message, resolve, reject)),
  keepAwake: (enable: boolean) =>
    new Promise<void>((resolve, reject) => bridge().keepAwake(enable, resolve, reject)),
  canInstallUnknown: () =>
    new Promise<boolean>((resolve, reject) =>
      bridge().canInstallUnknown((res) => resolve(Boolean(res.allowed)), reject)),
  requestInstallPermission: () =>
    new Promise<void>((resolve, reject) => bridge().requestInstallPermission(resolve, reject)),
  downloadApk: (url: string, fileName: string) =>
    new Promise<{ id: number }>((resolve, reject) => bridge().downloadApk(url, fileName, resolve, reject)),
  downloadProgress: (id: number) =>
    new Promise<DownloadProgress>((resolve, reject) => bridge().downloadProgress(id, resolve, reject)),
  installApk: (id: number) =>
    new Promise<void>((resolve, reject) => bridge().installApk(id, resolve, reject)),
  openUrl: (url: string) =>
    new Promise<void>((resolve, reject) => bridge().openUrl(url, resolve, reject))
};
