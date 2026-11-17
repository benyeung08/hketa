#!/usr/bin/env python3
"""
Static sanity checks for hketa, run by GitHub Actions before the APK is built.

It verifies that:
  1. Every JSON field read by the Java layer appears in the official sample
     responses (or is a documented alias).
  2. The API base URLs / endpoints match the published specifications.
  3. Gradle / AGP / Kotlin / SDK versions are consistent across config files.
  4. Every @string / @color / @drawable / @style / @mipmap / R.id reference
     resolves to a resource that actually exists.
  5. The multi-operator catalogues and spinner definitions stay in sync.

Only the Python standard library is needed.
"""

from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
API = ROOT / "app/src/main/java/hk/eta/app/api"
UI = ROOT / "app/src/main/java/hk/eta/app/ui"
RES = ROOT / "app/src/main/res"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

failures = []


def check(label, condition, detail=""):
    if condition:
        print(f"[OK]   {label}")
    else:
        print(f"[FAIL] {label} {detail}")
        failures.append(label)


def read(path):
    return path.read_text(encoding="utf-8") if path.exists() else ""


def all_keys(node, out):
    if isinstance(node, dict):
        out.update(node.keys())
        for value in node.values():
            all_keys(value, out)
    elif isinstance(node, list):
        for value in node:
            all_keys(value, out)
    return out


# --- Official sample payloads (DATA.GOV.HK / KMB / CTB / NLB / GMB / MTR) ---
SAMPLES = {
    "kmb_eta": {
        "type": "ETA", "version": "1.0",
        "generated_timestamp": "2021-03-04T17:22:35+08:00",
        "data": [{
            "co": "KMB", "route": "40", "dir": "0", "service_type": 1, "seq": 7,
            "stop": "A60AE774B09A5E44", "dest_tc": "麗港城", "dest_sc": "丽港城",
            "dest_en": "LAGUNA CITY", "eta_seq": 1,
            "eta": "2021-03-04T17:25:51+08:00", "rmk_tc": "", "rmk_sc": "",
            "rmk_en": "", "data_timestamp": "2021-03-04T17:22:15+08:00"
        }]
    },
    "ctb_eta": {
        "type": "ETA", "version": "2.0",
        "generated_timestamp": "2023-07-01T11:40:48+08:00",
        "data": [{
            "co": "CTB", "route": "720", "dir": "O", "seq": 1, "stop": "001313",
            "dest_tc": "中環", "dest_en": "Central", "dest_sc": "中环",
            "eta_seq": 1, "eta": "2023-07-01T11:45:00+08:00",
            "rmk_tc": "", "rmk_en": "", "rmk_sc": "",
            "data_timestamp": "2023-07-01T11:40:00+08:00"
        }]
    },
    "kmb_route_stop": {
        "type": "RouteStop", "generated_timestamp": "2021-03-04T17:22:35+08:00",
        "data": [{
            "route": "40", "bound": "O", "service_type": "01", "seq": 7,
            "stop": "A60AE774B09A5E44"
        }]
    },
    "ctb_route_stop": {
        "type": "Route", "version": "2.0",
        "generated_timestamp": "2023-07-01T11:40:48+08:00",
        "data": [{
            "co": "CTB", "route": "1", "dir": "I", "seq": 1, "stop": "002403",
            "data_timestamp": "2023-07-01T11:40:00+08:00"
        }]
    },
    "stop_list": {
        "generated_timestamp": "2023-07-01T11:40:48+08:00",
        "data": [{
            "stop": "002737", "name_tc": "砵典乍街, 德輔道中",
            "name_en": "Pottinger Street, Des Voeux Road Central",
            "name_sc": "砵典乍街, 德辅道中", "lat": 22.283948, "long": 114.156309,
            "data_timestamp": "2023-07-01T11:40:00+08:00"
        }]
    },
    "kmb_stop_all": {
        "type": "StopList", "version": "1.0",
        "generated_timestamp": "2020-11-29T11:40:48+08:00",
        "data": [{
            "stop": "A3ADFCDF8487ADB9", "name_tc": "中秀茂坪",
            "name_en": "SAU MAU PING (CENTRAL)", "name_sc": "中秀茂坪",
            "lat": 22.318856, "long": 114.231353,
            "data_timestamp": "2020-11-29T11:40:00+08:00"
        }]
    },
    "ctb_stop_all": {
        "type": "StopList", "version": "2.0",
        "generated_timestamp": "2023-07-01T11:40:48+08:00",
        "data": [{
            "stop": "002737", "name_tc": "砵典乍街, 德輔道中",
            "name_en": "Pottinger Street, Des Voeux Road Central",
            "name_sc": "砵典乍街, 德辅道中",
            "lat": 22.283948, "long": 114.156309,
            "data_timestamp": "2023-07-01T11:40:00+08:00"
        }]
    },
    "gmb_stop_index": {
        # Projected shape of the documented /stop/{stop_id} object when the
        # bulk /stop resource exposes it as a list. Parsed defensively at runtime.
        "type": "Stops", "generated_timestamp": "2020-09-24T16:19:19.858+08:00",
        "data": [{
            "stop_id": 20003337, "name_tc": "數碼港公共運輸交匯處",
            "name_en": "Cyberport Public Transport Interchange",
            "coordinates": {
                "wgs84": {
                    "latitude": 22.261913456129104,
                    "longitude": 114.13014951276878
                }
            }
        }]
    },
    "kmb_stop_eta": {
        "type": "StopETA", "version": "1.0",
        "generated_timestamp": "2021-03-18T12:21:38+08:00",
        "data": [{
            "co": "KMB", "route": "234X", "dir": "I", "service_type": 1,
            "seq": 2, "dest_tc": "尖沙咀東(麼地道)", "dest_sc": "尖沙咀东(么地道)",
            "dest_en": "TSIM SHA TSUI EAST (MODY ROAD)", "eta_seq": 1,
            "eta": "2021-03-18T12:31:59+08:00", "rmk_tc": "原定班次",
            "rmk_sc": "原定班次", "rmk_en": "Scheduled Bus",
            "data_timestamp": "2021-03-18T12:21:15+08:00"
        }]
    },
    "batch_stop_eta": {
        "type": "StopETA", "version": "1.0",
        "generated_timestamp": "2019-10-14T15:23:04+08:00",
        "data": [{
            "co": "CTB", "route": "13", "dir": "O", "seq": 6, "stop": "001001",
            "dest": "Kotewall Road", "eta_seq": 1,
            "eta": "2019-10-14T15:34:06+08:00", "rmk": "",
            "routeVariantName": None, "departed": None, "noGPS": None,
            "wheelChair": None, "data_timestamp": "2019-10-14T15:22:02+08:00"
        }]
    },
    "gmb_stop_eta": {
        "type": "ETA-Stop", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:29:36.167+08:00",
        "data": [{
            "enabled": True, "route_id": 2000410, "route_seq": 1, "stop_seq": 42,
            "description_tc": "", "description_sc": "", "description_en": "",
            "eta": [{
                "eta_seq": 1, "diff": 42,
                "timestamp": "2020-12-28T16:11:47.783+08:00",
                "remarks_tc": None, "remarks_sc": None, "remarks_en": None
            }]
        }]
    },
    "gmb_route_by_id": {
        "type": "Route", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:07:55.373+08:00",
        "data": [{
            "route_id": 2000410, "route_code": "69", "region": "HKI",
            "description_tc": "正常班次"
        }]
    },
    "nlb_routes": {
        "routes": [{
            "routeId": "2", "routeNo": "1", "routeName_c": "大澳 > 梅窩碼頭",
            "routeName_s": "大澳 > 梅窝码头", "routeName_e": "Tai O > Mui Wo Ferry Pier",
            "overnightRoute": 0, "specialRoute": 0
        }]
    },
    "nlb_stops": {
        "stops": [{
            "stopId": "117", "stopName_c": "機場(客運大樓)",
            "stopName_s": "机场(客运大楼)", "stopName_e": "Airport (Passenger Terminal Building)",
            "stopLocation_c": "暢達路", "stopLocation_s": "畅达路",
            "stopLocation_e": "Cheong Tat Road", "latitude": "22.31456900",
            "longitude": "113.93661000", "fare": "15.0", "fareHoliday": "25.0",
            "someDepartureObserveOnly": 0
        }]
    },
    "nlb_eta": {
        "estimatedArrivals": [{
            "estimatedArrivalTime": "2018-01-01 08:05:00",
            "routeVariantName": "經: 梅窩舊墟", "departed": 1, "noGPS": 0,
            "wheelChair": 1, "generateTime": "2018-01-01 08:00:00"
        }],
        "message": ""
    },
    "gmb_routes_all": {
        "type": "Routes-All", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:07:55.373+08:00",
        "data": {
            "routes": {"HKI": ["1", "69"], "KLN": ["12"], "NT": ["101M"]},
            "data_timestamp": "2020-12-22T11:27:48.705+08:00"
        }
    },
    "gmb_route": {
        "type": "Route", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:07:55.373+08:00",
        "data": [{
            "region": "HKI", "route_code": "69", "route_id": 2000410,
            "description_tc": "正常班次", "description_sc": "正常班次",
            "description_en": "Normal Schedule",
            "directions": [{
                "route_seq": 1, "orig_tc": "數碼港",
                "dest_tc": "鰂魚涌(船塢里)(循環線)",
                "headways": [{
                    "weekdays": [True, True, True, True, True, False, False],
                    "public_holiday": False, "headway_seq": 1,
                    "start_time": "06:30:00", "end_time": "07:00:00"
                }]
            }]
        }]
    },
    "gmb_route_stop": {
        "type": "Route-Stop", "version": "1.0",
        "generated_timestamp": "2020-12-28T14:51:23.258+08:00",
        "data": {
            "route_stops": [{
                "stop_seq": 1, "stop_id": 20003337,
                "name_tc": "數碼港公共運輸交匯處",
                "name_sc": "数码港公共运输交汇处",
                "name_en": "Cyberport Public Transport Interchange"
            }],
            "data_timestamp": "2020-12-23T13:23:09.065+08:00"
        }
    },
    "gmb_eta_stop": {
        "type": "ETA-Route-Stop", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:15:35.698+08:00",
        "data": [{
            "enabled": True, "route_seq": 1, "stop_seq": 1,
            "eta": [{
                "eta_seq": 1, "diff": 4,
                "timestamp": "2020-12-28T15:20:00.000+08:00",
                "remarks_tc": None, "remarks_sc": None, "remarks_en": None
            }]
        }]
    },
    "mtr_next_train": {
        "sys_time": "2022-04-25 15:20:00", "curr_time": "2022-04-25 15:20:00",
        "data": {
            "TKL-TKO": {
                "curr_time": "2022-04-25 15:20:00", "sys_time": "2022-04-25 15:20:00",
                "UP": [{
                    "ttnt": "1", "valid": "Y", "plat": "1",
                    "time": "2022-04-25 15:20:00", "source": "-",
                    "dest": "POA", "seq": "1"
                }],
                "DOWN": [{
                    "ttnt": "2", "valid": "Y", "plat": "2",
                    "time": "2022-04-25 15:21:00", "source": "-",
                    "dest": "NOP", "seq": "2"
                }]
            }
        },
        "status": 1, "message": "successful", "isdelay": "N", "url": ""
    },
    "mtr_lrt": {
        "platform_list": [{
            "platform_id": "1",
            "route_list": [{
                "train_length": 1, "arrival_departure": "A",
                "dest_en": "Siu Hong", "dest_ch": "兆康",
                "time_en": "5 min", "time_ch": "5 分鐘",
                "route_no": "615", "stop": 0, "special": 0,
                "additionalInfo1": "", "routeRemarkEng2": None,
                "routeRemarkChi2": None
            }]
        }],
        "generated_timestamp": "2026-04-28T12:00:00+08:00"
    },
    "mtr_bus": {
        "appRefreshTimeInSecond": "60",
        "busStop": [{
            "bus": [{
                "arrivalTimeInSecond": "108000", "arrivalTimeText": "",
                "busId": "804", "busLocation": {"latitude": 0, "longitude": 0},
                "busRemark": None, "departureTimeInSecond": "38",
                "departureTimeText": "Departing / Departed", "isDelayed": "0",
                "isScheduled": "1", "lineRef": "K12_TPMS"
            }],
            "busIcon": None, "busStopId": "K12-D010", "busStopRemark": "",
            "busStopStatus": "", "busStopStatusRemarkContent": None,
            "busStopStatusRemarkTitle": None, "busStopStatusTime": None,
            "isSuspended": "0"
        }],
        "caseNumber": 0, "caseNumberDetail": "", "footerRemarks": "",
        "routeColour": "", "routeName": "K12", "routeStatus": "0",
        "routeStatusColour": "black", "routeStatusRemarkContent": None,
        "routeStatusRemarkFooterRemark": "", "routeStatusRemarkTitle": None,
        "routeStatusTime": "2021/08/24 11:29", "status": "0"
    }
}

# GET https://api.github.com/repos/{owner}/{repo}/releases/latest
SAMPLES["github_release_latest"] = {
    "url": "https://api.github.com/repos/OWNER/REPO/releases/latest",
    "html_url": "https://github.com/OWNER/REPO/releases/tag/v1.0.1",
    "tag_name": "v1.0.1",
    "name": "HK ETA v1.0.1",
    "body": "## Changes\n- 加入設定頁應用程式更新\n- 修正站名解析",
    "published_at": "2026-11-17T02:00:00Z",
    "draft": False,
    "prerelease": False,
    "assets": [{
        "name": "app-debug.apk",
        "content_type": "application/vnd.android.package-archive",
        "size": 5242880,
        "browser_download_url":
            "https://github.com/OWNER/REPO/releases/download/v1.0.1/app-debug.apk"
    }, {
        "name": "release-notes.txt",
        "content_type": "text/plain",
        "size": 512,
        "browser_download_url":
            "https://github.com/OWNER/REPO/releases/download/v1.0.1/release-notes.txt"
    }]
}
# Rate-limited / 404 error shape.
SAMPLES["github_api_error"] = {
    "message": "API rate limit exceeded for 1.2.3.4.",
    "documentation_url": "https://docs.github.com/rest/overview/rate-limits"
}

official_keys = set()
for name, payload in SAMPLES.items():
    official_keys = all_keys(payload, official_keys)
    check(f"sample '{name}' is valid JSON", isinstance(payload, dict))
    json.dumps(payload, ensure_ascii=False)

# Documented aliases / fields that appear in other sections of the official
# specifications but not necessarily in the samples above.
ALIASES = {
    "dest", "rmk", "dest_sc", "rmk_sc", "name_sc", "description_sc",
    "description_en", "remarks_sc", "remarks_en", "routeRemarkEng2",
    "weekdays", "public_holiday", "headway_seq", "start_time", "end_time",
    "latitude", "longitude", "lat", "long", "coordinates", "wgs84",
    "stop_id", "stops", "routes", "routeId", "routeNo", "route_code",
    "stopName_s", "stopLocation_c", "stopLocation_s", "stopLocation_e",
    "someDepartureObserveOnly", "fare", "fareHoliday", "overnightRoute",
    "specialRoute", "generateTime", "message", "noGPS", "wheelChair",
    "routeVariantName", "departed", "enabled", "diff", "timestamp",
    "remarks_tc", "route_id", "route_seq", "stop_seq", "region",
    "stopId", "stopName_c", "stopName_e", "stopName_s", "name_c",
    # RouteDataStore writes its own meta.json; these are local keys, not API fields.
    "label", "ok", "count", "error", "at", "total", "sources", "updated_at",
    # Column-name keywords scanned in CSV headers are not JSON keys either.
    "linecode", "stationcode", "stopid", "stationid", "longitude", "latitude",
    # GitHub Releases REST API.
    "tag_name", "html_url", "published_at", "prerelease", "draft", "assets",
    "browser_download_url", "content_type", "size", "body", "documentation_url",
    "url"
}
CORE_FIELDS = {
    "data", "eta", "eta_seq", "stop", "seq", "route", "dest_tc", "rmk_tc",
    "data_timestamp", "name_tc", "service_type", "dir", "bound",
    "estimatedArrivalTime", "timestamp", "route_id", "busStopId",
    "time", "plat", "route_no", "dest_ch",
    "lat", "long", "latitude", "longitude", "stop_id", "coordinates",
    "dest", "rmk", "routeVariantName", "enabled", "diff"
}
check("official samples contain every core field",
      CORE_FIELDS <= official_keys,
      f"missing={sorted(CORE_FIELDS - official_keys)}")

# MtrCatalog scans CSV *header keywords*, not JSON keys, so it is checked below.
java = "\n".join(read(API / name) for name in
                 ("EtaRepository.java", "GeoStops.java", "HttpJson.java",
                  "RouteDataStore.java", "AppUpdate.java"))
field_refs = set(re.findall(r'HttpJson\.str\([^,]+,\s*"([^"]+)"\)', java))
field_refs |= set(re.findall(
    r'\.opt(?:String|Int|Long|Double|Boolean|JSONObject|JSONArray)\(\s*"([^"]+)"',
    java
))
unknown = sorted(field_refs - official_keys - ALIASES)
check("all JSON keys used in Java match the API specs", not unknown,
      f"unknown={unknown}")
check("parser reads ETA fields of every family",
      {"eta", "timestamp", "estimatedArrivalTime", "time", "arrivalTimeInSecond"}
      <= field_refs)

# --- Endpoint / version consistency ------------------------------------------
repo_java = read(API / "EtaRepository.java")
catalog_java = read(API / "MtrCatalog.java")
http_java = read(API / "HttpJson.java")
manifest_text = read(MANIFEST)
app_gradle = read(ROOT / "app/build.gradle.kts")
workflow = read(ROOT / ".github/workflows/build-apk.yml")
check("KMB base URL", "https://data.etabus.gov.hk/v1/transport/kmb" in repo_java)
check("Citybus base URL", "https://rt.data.gov.hk/v2/transport/citybus" in repo_java)
check("NLB base URL", "https://rt.data.gov.hk/v2/transport/nlb" in repo_java)
check("GMB base URL", "https://data.etagmb.gov.hk" in repo_java)
check("MTR base URL", "https://rt.data.gov.hk/v1/transport/mtr" in repo_java)
check("MTR CSV catalogues", "mtr_lines_and_stations.csv" in catalog_java
      and "mtr_bus_stops.csv" in catalog_java
      and "light_rail_routes_and_stops.csv" in catalog_java)
for fragment in ("/eta/", "/route-stop/", "/stop", "/route.php?action=list",
                 "/stop.php?action=list", "/stop.php?action=estimatedArrivals",
                 "/eta/route-stop/", "/getSchedule.php", "/lrt/getSchedule",
                 "/bus/getSchedule", "/stop-eta/", "/batch/stopeta/",
                 "/batch/stop-eta/", "/eta/stop/", "/route/"):
    check(f"endpoint fragment {fragment}", fragment in repo_java)
geo_java = read(API / "GeoStops.java")
check("GeoStops reads full stop lists", all(
    f in geo_java for f in ("/stop", "/route.php?action=list",
                            "/stop.php?action=list", "coordinates", "wgs84")))
check("GeoStops caches & computes distance", "CACHE_TTL_MS" in geo_java
      and "distanceMeters" in geo_java)
check("MtrCatalog exposes nearby CSV seeds", "nearbySeeds" in catalog_java
      and "NearbySeed" in catalog_java)
check("nearby mode is wired into repository", 'NEARBY' in repo_java
      and "GeoStops.nearby" in repo_java and "stopEta" in repo_java)
check("location permissions in manifest", "ACCESS_FINE_LOCATION" in manifest_text
      and "ACCESS_COARSE_LOCATION" in manifest_text)
check("activity-ktx for permission request", "activity-ktx" in app_gradle)
loc_kt = read(UI / "LocationHelper.kt")
check("LocationManager one-shot fix", "LocationManager" in loc_kt
      and "requestSingleUpdate" in loc_kt and "withTimeoutOrNull" in loc_kt)
check("location fallback to Central", "DEFAULT_LAT" in loc_kt)
check("MTR Bus uses POST + JSON", "postJson" in http_java and "postJson" in repo_java)
check("7 operators wired", all(
    f'"{op}"' in repo_java for op in ("KMB", "CTB", "NLB", "GMB", "MTR", "LRT", "MTRB")))
check("10 heavy rail lines", catalog_java.count('"AEL"') >= 1
      and all(c in catalog_java for c in ("AEL", "TCL", "TML", "TKL", "EAL",
                                          "SIL", "TWL", "ISL", "KTL", "DRL")))
check("light rail station table is bundled", catalog_java.count('"1|') >= 1
      and '"600|元朗|Yuen Long"' in catalog_java)
check("MTR Bus route list is bundled", '"K12"' in catalog_java
      and '"506"' in catalog_java)

# --- Settings: route-catalogue update -----------------------------------------
store_java = read(API / "RouteDataStore.java")
settings_kt = read(UI / "SettingsActivity.kt")
prefs_kt = read(UI / "RouteDataPrefs.kt")
app_kt = read(ROOT / "app/src/main/java/hk/eta/app/HkEtaApp.kt")
viewmodel_kt = read(UI / "EtaViewModel.kt")
main_layout = read(RES / "layout/activity_main.xml")
settings_layout = read(RES / "layout/activity_settings.xml")
check("HkEtaApp registered in manifest", 'android:name=".HkEtaApp"' in manifest_text)
check("SettingsActivity registered in manifest",
      ".ui.SettingsActivity" in manifest_text)
check("Application initialises RouteDataStore", "RouteDataStore.init(this)" in app_kt)
check("settings entry on main screen", "btnSettings" in main_layout
      and "btnSettings" in read(UI / "MainActivity.kt")
      and "SettingsActivity::class.java" in read(UI / "MainActivity.kt"))
check("settings layout has manual update", "btnUpdate" in settings_layout
      and "spInterval" in settings_layout and "swWifi" in settings_layout
      and "tvSources" in settings_layout and "btnClear" in settings_layout)
check("10 official catalogue datasets + derived NLB stops",
      store_java.count("new Dataset(") == 10
      and "buildNlbStops" in store_java and "NLB_STOPS" in store_java)
check("official catalogue URLs",
      all(u in store_java for u in (
          "/v1/transport/kmb/route/", "/v1/transport/kmb/stop",
          "/v2/transport/citybus/route/CTB", "/v2/transport/citybus/stop",
          "/v2/transport/nlb/route.php?action=list",
          "data.etagmb.gov.hk/route", "data.etagmb.gov.hk/stop",
          "mtr_lines_and_stations.csv", "light_rail_routes_and_stops.csv",
          "mtr_bus_stops.csv")))
check("CTB route list has v1 fallback",
      "/v1/transport/citybus-nwfb/route/CTB" in store_java)
check("atomic snapshot writes", "writeAtomic" in store_java and '.tmp' in store_java)
check("refresh guarded against concurrent runs", "AtomicBoolean" in store_java
      and "compareAndSet" in store_java)
check("Wi-Fi-only gate", "onWifi" in store_java
      and "TRANSPORT_WIFI" in store_java and "onWifi()" in prefs_kt)
check("snapshot invalidation fans out", all(
      c in store_java for c in ("MtrCatalog.invalidateCaches()",
                                "GeoStops.invalidate()", "EtaRepository.clearCaches()")))
check("data layer prefers local snapshots",
      "catalogueJson" in repo_java and "catalogCsv" in catalog_java
      and "datasetJson" in geo_java and "RouteDataStore.NLB_STOPS" in geo_java)
check("manual update ignores Wi-Fi restriction",
      "RouteDataStore.refresh(false)" in settings_kt)
check("prefs: interval / wifi / last check", all(
      k in prefs_kt for k in ("INTERVAL_DAYS", "isDue", "markChecked",
                              "updateAllowed", "KEY_LAST_CHECK")))
check("background auto-update at startup",
      "maybeAutoUpdateCatalogue" in viewmodel_kt
      and "RouteDataPrefs.isDue" in viewmodel_kt)

# --- Settings: in-app application update --------------------------------------
update_java = read(API / "AppUpdate.java")
update_prefs_kt = read(UI / "AppUpdatePrefs.kt")
installer_kt = read(UI / "ApkInstaller.kt")
file_paths_xml = read(RES / "xml/file_paths.xml")
main_kt = read(UI / "MainActivity.kt")
check("GitHub latest-release endpoint",
      "https://api.github.com/repos/" in update_java
      and "/releases/latest" in update_java
      and "HttpJson.getGitHub" in update_java)
check("GitHub API headers", "application/vnd.github+json" in http_java
      and "X-GitHub-Api-Version" in http_java
      and "User-Agent" in http_java)
check("repo format validated before request", "validRepo" in update_java
      and "ownerRepo.trim().matches" in update_java)
check("release parser reads tag/name/body/assets", all(
      k in update_java for k in ("tag_name", "html_url", "published_at",
                                 "prerelease", "assets", "browser_download_url",
                                 "content_type")))
check("only .apk assets accepted", '.apk' in update_java)
check("version comparison handles v-prefix", "compareVersions" in update_java
      and "startsWith(\"v\")" in update_java)
check("safe download host whitelist", "isSafeUrl" in update_java
      and "release-assets.githubusercontent.com" in update_java
      and "objects.githubusercontent.com" in update_java)
check("download is atomic, size-limited and private", all(
      k in update_java for k in ("MAX_APK_BYTES", ".tmp", "renameTo",
                                 "setInstanceFollowRedirects(false)")))
check("REQUEST_INSTALL_PACKAGES declared",
      "android.permission.REQUEST_INSTALL_PACKAGES" in manifest_text)
check("FileProvider registered with matching authority",
      "androidx.core.content.FileProvider" in manifest_text
      and 'android:authorities="hk.eta.app.fileprovider"' in manifest_text
      and "hk.eta.app.fileprovider" not in installer_kt
      and 'context.packageName + AUTHORITY_SUFFIX' in installer_kt
      and 'AUTHORITY_SUFFIX = ".fileprovider"' in installer_kt)
check("file_paths exposes private updates dir",
      "files-path" in file_paths_xml and 'path="updates/"' in file_paths_xml
      and "@xml/file_paths" in manifest_text)
check("unknown-sources gate on Android 8+",
      "canRequestPackageInstalls" in installer_kt
      and "ACTION_MANAGE_UNKNOWN_APP_SOURCES" in installer_kt)
check("installer grants read URI permission",
      "FLAG_GRANT_READ_URI_PERMISSION" in installer_kt
      and "application/vnd.android.package-archive" in installer_kt
      and "FileProvider.getUriForFile" in installer_kt)
check("settings layout has app-update section", all(
      i in settings_layout for i in ("tvAppVersion", "etRepo", "swAutoApp",
                                     "btnCheckApp", "btnDownloadApk",
                                     "btnReleasePage", "progressApp",
                                     "tvAppUpdateStatus", "tvReleaseNotes")))
check("settings wires check / download / install", all(
      s in settings_kt for s in ("AppUpdate.latest", "AppUpdate.download",
                                 "ApkInstaller.install", "unknownSourcesLauncher",
                                 "openReleasePage", "compareVersions")))
check("repo pref + auto-check + 24h throttle", all(
      k in update_prefs_kt for k in ("KEY_REPO", "KEY_AUTO", "KEY_LAST_CHECK",
                                     "CHECK_INTERVAL_MS", "installedVersion",
                                     "cacheResult")))
check("startup badge check is silent and read-only",
      "maybeAutoCheckAppUpdate" in viewmodel_kt
      and "AppUpdatePrefs.cacheResult" in viewmodel_kt
      and "app_update_new_badge" in main_kt)
check("default_github_repo is defined",
      "default_github_repo" in read(RES / "values/strings.xml"))

# --- Settings: signing consistency --------------------------------------------
signature_kt = read(UI / "ApkSignature.kt")
settings_strings = read(RES / "values/strings.xml")
gitignore = read(ROOT / ".gitignore")
pin_script = read(ROOT / "scripts/pin_signing_fingerprint.py")
keystore = ROOT / "app/hketa-debug.keystore"
check("committed fixed dev keystore exists", keystore.exists()
      and keystore.stat().st_size > 1000)
check("gradle uses fixed signing configs", all(
      s in app_gradle for s in ("signingConfigs", "hketaDev", "hketaRelease",
                                "hketa-debug.keystore", "hketa-release.keystore",
                                "signingConfig = signingConfigs.getByName")))
check("release keystore is gitignored",
      "/app/hketa-release.keystore" in gitignore)
check("CI verifies committed keystore fingerprint",
      "keytool -list -v" in workflow and "app/hketa-debug.keystore" in workflow)
check("CI supports injected release keystore via Secrets",
      "HKETA_KEYSTORE_BASE64" in workflow
      and "HKETA_KEY_ALIAS" in workflow
      and "app/hketa-release.keystore" in workflow)
check("CI pins and cross-checks APK certificate", all(
      s in workflow for s in ("scripts/pin_signing_fingerprint.py",
                              "--print-certs", "apksigner",
                              "expected_signing_sha256",
                              "SIGNING_FINGERPRINT.txt",
                              "assembleRelease")))
check("pin script validates 64-hex fingerprint and rewrites resource",
      "fullmatch(r\"[0-9A-F]{64}\"" in pin_script
      and 'KEY = "expected_signing_sha256"' in pin_script)
check("expected_signing_sha256 resource defined",
      '<string name="expected_signing_sha256">' in settings_strings)
check("signature strings all defined", all(
      name in settings_strings for name in ("signing_section", "signing_fingerprint",
                                            "signing_status_ok", "signing_status_fail",
                                            "signing_detail", "signing_hint",
                                            "app_update_signature_failed")))
check("signature layout ids exist", all(
      i in settings_layout for i in ("tvSigningFingerprint", "tvSigningStatus",
                                     "tvSigningDetail")))
check("app compares APK cert with installed cert", all(
      s in signature_kt for s in ("installedFingerprints", "archiveFingerprints",
                                  "getPackageArchiveInfo", "GET_SIGNING_CERTIFICATES",
                                  "signingCertificateHistory", "MessageDigest",
                                  "SHA-256", "apkPackage")))
check("app verifies packageName and optional pin", all(
      s in signature_kt for s in ("context.packageName", "expectedSha256",
                                  "normalize", "hasPastSigningCertificates")))
check("settings verifies before install and deletes mismatched APK", all(
      s in settings_kt for s in ("ApkSignature.verify", "renderSignatureCheck",
                                 "apk.delete()", "showInstalledFingerprint")))
check("unknown-sources return path re-verifies signature",
      settings_kt.count("ApkSignature.verify") >= 2)

# --- Launcher icon (user-supplied rainbow bus artwork) ------------------------
import struct

def png_size(path):
    """Reads width/height from a PNG IHDR without Pillow (stdlib only)."""
    if not path.exists():
        return (0, 0)
    with path.open("rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return (0, 0)
    return struct.unpack(">II", head[16:24])

LEGACY_PX = {"mdpi": 48, "hdpi": 72, "xhdpi": 96,
             "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_PX = {"mdpi": 108, "hdpi": 162, "xhdpi": 216,
               "xxhdpi": 324, "xxxhdpi": 432}
check("icon source artwork is committed",
      (ROOT / "app/icon-source.png").exists()
      and png_size(ROOT / "app/icon-source.png") == (1080, 1080))
check("icon generator script exists",
      (ROOT / "scripts/make_launcher_icons.py").exists()
      and "ic_launcher-playstore.png" in
      read(ROOT / "scripts/make_launcher_icons.py"))
for density, px in LEGACY_PX.items():
    folder = RES / f"mipmap-{density}"
    sizes = {
        "ic_launcher.png": png_size(folder / "ic_launcher.png"),
        "ic_launcher_round.png": png_size(folder / "ic_launcher_round.png"),
        "ic_launcher_background.png": png_size(folder / "ic_launcher_background.png"),
        "ic_launcher_foreground.png": png_size(folder / "ic_launcher_foreground.png"),
    }
    check(f"mipmap-{density} icon sizes", all(
          v == (expected, expected) for v, expected in zip(
              sizes.values(),
              (px, px, ADAPTIVE_PX[density], ADAPTIVE_PX[density])
          )), f"got={sizes}")
adaptive_xml = read(RES / "mipmap-anydpi-v26/ic_launcher.xml")
check("adaptive icon XML for both shapes", all(
      s in adaptive_xml for s in ("adaptive-icon", "@mipmap/ic_launcher_background",
                                   "@mipmap/ic_launcher_foreground"))
      and (RES / "mipmap-anydpi-v26/ic_launcher_round.xml").exists())
check("Play Store 512px icon", png_size(
      ROOT / "app/src/main/ic_launcher-playstore.png") == (512, 512))
check("manifest points at new icons", all(
      s in manifest_text for s in ('android:icon="@mipmap/ic_launcher"',
                                   'android:roundIcon="@mipmap/ic_launcher_round"')))

root_gradle = read(ROOT / "build.gradle.kts")
app_gradle = read(ROOT / "app/build.gradle.kts")
wrapper = read(ROOT / "gradle/wrapper/gradle-wrapper.properties")
workflow = read(ROOT / ".github/workflows/build-apk.yml")
check("AGP 8.5.2", 'version "8.5.2"' in root_gradle)
check("Kotlin 1.9.24", 'version "1.9.24"' in root_gradle)
check("Gradle 8.7 wrapper", "gradle-8.7-bin.zip" in wrapper)
check("Gradle 8.7 in CI", "gradle-8.7-bin.zip" in workflow)
check("compileSdk 34 in app + CI", "compileSdk = 34" in app_gradle and "android-34" in workflow)
check("CI assembles debug APK", "gradle assembleDebug" in workflow)
check("CI uploads the APK", "upload-artifact@v4" in workflow and "app-debug.apk" in workflow)

# --- Resource references ------------------------------------------------------
res_text = "\n".join(read(p) for p in RES.rglob("*.xml"))
manifest_text = read(MANIFEST)
kt_text = "\n".join(read(p) for p in UI.glob("*.kt"))

defined = set()
for path in RES.rglob("*"):
    if not path.is_file():
        continue
    text = read(path) if path.suffix == ".xml" else ""
    parent = path.parent.name
    if parent.startswith("values") and path.suffix == ".xml":
        for tag in ("string", "color", "style", "string-array"):
            defined |= set(re.findall(rf'<{tag}\s+name="([^"]+)"', text))
    elif (parent.startswith("drawable") or parent.startswith("mipmap")) \
            and path.suffix in (".xml", ".png", ".webp", ".jpg"):
        defined.add(path.stem)
    elif parent.startswith("layout") and path.suffix == ".xml":
        defined.add(path.stem)

EXTERNAL = {"Widget.MaterialComponents.Button.OutlinedButton"}
refs = set(re.findall(r'@(?:string|color|drawable|style|mipmap)/([A-Za-z0-9_.]+)', res_text))
refs |= set(re.findall(r'@(?:string|color|drawable|style|mipmap)/([A-Za-z0-9_.]+)', manifest_text))
for kind in ("string", "color"):
    refs |= set(re.findall(rf'R\.{kind}\.([A-Za-z0-9_]+)', kt_text))
missing = sorted(refs - defined - EXTERNAL)
check("all XML / Kotlin resource references resolve", not missing,
      f"missing={missing}")

layout_ids = set(re.findall(r'android:id="@\+id/([A-Za-z0-9_]+)"', res_text))
kt_ids = set(re.findall(r'R\.id\.([A-Za-z0-9_]+)', kt_text))
missing_ids = sorted(kt_ids - layout_ids)
check("all R.id references exist in layouts", not missing_ids,
      f"missing={missing_ids}")

# --- Spinner / array sync -----------------------------------------------------
strings_root = ET.parse(RES / "values/strings.xml").getroot()
arrays = {
    a.get("name"): [item.text or "" for item in a.findall("item")]
    for a in strings_root.findall("string-array")
}
check("company_labels has 8 modes", len(arrays.get("company_labels", [])) == 8)
check("mode_hints has 8 entries", len(arrays.get("mode_hints", [])) == 8)
check("radius_labels has 4 entries", len(arrays.get("radius_labels", [])) == 4)
check("update_interval_labels has 4 entries",
      len(arrays.get("update_interval_labels", [])) == 4)
check("settings strings all defined", all(
      name in defined for name in ("settings", "settings_title", "route_data_section",
                                   "update_now", "auto_update", "sources_title",
                                   "clear_local", "settings_footer")))
main_kt = read(UI / "MainActivity.kt")
check("nearby button requests permissions", "btnNearby" in main_kt
      and "RequestMultiplePermissions" in main_kt and "selectedRadius" in main_kt)
vm_kt = read(UI / "EtaViewModel.kt")
check("ViewModel has loadNearby", "fun loadNearby" in vm_kt)
check("ViewModel maps all 8 spinner positions",
      all(f"{i} -> EtaRepository." in vm_kt for i in range(1, 8)))

if failures:
    print(f"\n{len(failures)} check(s) failed: {', '.join(failures)}")
    sys.exit(1)
print("\nAll static checks passed.")
