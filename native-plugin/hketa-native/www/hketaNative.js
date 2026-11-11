var exec = require('cordova/exec');
var SERVICE = 'HkEtaNative';

// JavaScript side of the Kotlin plugin. TypeScript calls window.HkEtaNative
// through src/native.ts after `deviceready`.
module.exports = {
  deviceInfo: function (success, error) {
    exec(success, error, SERVICE, 'deviceInfo', []);
  },

  appVersion: function (success, error) {
    exec(success, error, SERVICE, 'appVersion', []);
  },

  openMap: function (lat, lng, label, success, error) {
    exec(success, error, SERVICE, 'openMap', [lat, lng, label]);
  },

  toast: function (message, success, error) {
    exec(success, error, SERVICE, 'toast', [message]);
  },

  keepAwake: function (enable, success, error) {
    exec(success, error, SERVICE, 'keepAwake', [enable]);
  },

  // --- in-app update (Settings) ---
  canInstallUnknown: function (success, error) {
    exec(success, error, SERVICE, 'canInstallUnknown', []);
  },

  requestInstallPermission: function (success, error) {
    exec(success, error, SERVICE, 'requestInstallPermission', []);
  },

  downloadApk: function (url, fileName, success, error) {
    exec(success, error, SERVICE, 'downloadApk', [url, fileName]);
  },

  downloadProgress: function (id, success, error) {
    exec(success, error, SERVICE, 'downloadProgress', [id]);
  },

  installApk: function (id, success, error) {
    exec(success, error, SERVICE, 'installApk', [id]);
  },

  openUrl: function (url, success, error) {
    exec(success, error, SERVICE, 'openUrl', [url]);
  }
};
