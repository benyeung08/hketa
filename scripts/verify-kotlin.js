const fs = require('fs');
const path = require('path');

// Cordova before_build hook.
// cordova-android 15 injects kotlin-gradle-plugin / kotlin-android / kotlin-stdlib
// when GradlePluginKotlinEnabled=true is set in config.xml.
// This hook verifies Kotlin AND that the generated Gradle project carries the
// declared range: minSdk 26 (Android 8.0) / target+compile 36 (Android 16,
// cordova-android 15's official ceiling). The APK still runs on Android 17.
module.exports = function (ctx) {
  const root = (ctx && ctx.opts && ctx.opts.projectRoot) || process.cwd();
  const platformDir = path.join(root, 'platforms', 'android');
  const appGradle = path.join(platformDir, 'app', 'build.gradle');
  const gradleConfig = path.join(platformDir, 'cdv-gradle-config.json');

  if (!fs.existsSync(appGradle)) {
    console.log('[hketa] Android platform not generated yet; skip Kotlin check.');
    return;
  }

  const source = fs.readFileSync(appGradle, 'utf8');
  if (source.includes('kotlin-android')) {
    console.log('[hketa] Kotlin Gradle plugin detected OK');
  } else {
    console.warn('[hketa] Kotlin plugin missing — check GradlePluginKotlinEnabled in config.xml');
  }

  if (!fs.existsSync(gradleConfig)) {
    console.warn('[hketa] cdv-gradle-config.json missing; skip SDK range check.');
    return;
  }

  const cfg = JSON.parse(fs.readFileSync(gradleConfig, 'utf8'));
  const expected = {
    MIN_SDK_VERSION: 26,        // Android 8.0 Oreo
    SDK_VERSION: 36,            // Android 16 — cordova-android 15 ceiling
    COMPILE_SDK_VERSION: 36
  };
  const wrong = Object.keys(expected).filter((k) => cfg[k] !== expected[k]);
  if (wrong.length) {
    throw new Error(
      '[hketa] SDK range mismatch: ' + wrong.map((k) => `${k}=${cfg[k]} (expected ${expected[k]})`).join(', ') +
      '. Supported devices are Android 8.0–17 (API 26–37), built at min 26 / target 36.'
    );
  }
  if (cfg.MAX_SDK_VERSION != null) {
    throw new Error(
      '[hketa] android-maxSdkVersion is set (' + cfg.MAX_SDK_VERSION + '). ' +
      'A hard cap breaks forward compatibility on Android 18+.'
    );
  }
  console.log('[hketa] SDK range OK: minSdk 26 / targetSdk 36 / compileSdk 36 / no maxSdk');
};
