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


# CODE_OPTIMISATION: 以前 219 次 read() 每次都重新開檔讀碟（同一份
# RouteSearch.java 被讀 12 次）。checker 全程唯讀、唔會改檔，所以按絕對
# 路徑 memoize 係安全嘅：每份檔案一個 process 只讀一次，CI 檢查明顯提速，
# 全部 check 條件同輸出一個字都唔變。
_READ_CACHE = {}


def read(path):
    key = str(path)
    if key not in _READ_CACHE:
        _READ_CACHE[key] = path.read_text(encoding="utf-8") if path.exists() else ""
    return _READ_CACHE[key]


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
    # GMB_CIRCULAR_ETA: a circular route reports the SAME stop_id at two
    # stop_seq positions (first / last terminus), each with its OWN eta[].
    "gmb_eta_stop_circular": {
        "type": "ETA-Route-Stop", "version": "1.0",
        "generated_timestamp": "2020-12-28T15:15:35.698+08:00",
        "data": [{
            "enabled": True, "route_seq": 1, "stop_seq": 1,
            "eta": [
                {"eta_seq": 1, "diff": 4,
                 "timestamp": "2020-12-28T15:20:00.000+08:00",
                 "remarks_tc": None, "remarks_sc": None, "remarks_en": None},
                {"eta_seq": 2, "diff": 14,
                 "timestamp": "2020-12-28T15:30:00.000+08:00",
                 "remarks_tc": None, "remarks_sc": None, "remarks_en": None}
            ]
        }, {
            "enabled": True, "route_seq": 1, "stop_seq": 42,
            "eta": [
                {"eta_seq": 1, "diff": 24,
                 "timestamp": "2020-12-28T15:40:00.000+08:00",
                 "remarks_tc": None, "remarks_sc": None, "remarks_en": None},
                {"eta_seq": 2, "diff": 34,
                 "timestamp": "2020-12-28T15:50:00.000+08:00",
                 "remarks_tc": None, "remarks_sc": None, "remarks_en": None}
            ]
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
    # Local snapshot markers written by RouteDataStore (not API response fields).
    "partial", "skipped", "type", "version", "generated_timestamp",
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
          "/v2/transport/citybus/route/CTB",
          "/v2/transport/nlb/route.php?action=list",
          "data.etagmb.gov.hk/route", "data.etagmb.gov.hk/stop",
          "mtr_lines_and_stations.csv", "light_rail_routes_and_stops.csv",
          "mtr_bus_stops.csv")))
# Citybus v2 spec has ONLY /stop/{stop_id}; the old bulk /stop URL caused a
# permanent HTML 403 in the 線路資料更新 screen.
check("no invalid Citybus full stop-list download",
      "/v2/transport/citybus/stop\"" not in store_java
      and "new Dataset(CTB_STOPS" in store_java
      and "stopCacheStatus" in store_java)
check("KMB bulk stop list is optional with trailing-slash retry",
      "KMB_STOPS, \"九巴／龍運全量站點（選用）\", true, true" in store_java
      and "/v1/transport/kmb/stop/\"" in store_java)
check("403 / HTML WAF pages are retried and rejected", all(
      s in http_java for s in (
          "USER_AGENT_BROWSER", "getFirst(", "getFirstText(", "singleStop(",
          "trimmed.startsWith(\"<\")", "Accept-Language")))
check("single-stop API resolves Chinese stop names", all(
      s in repo_java for s in (
          "fetchStopName", "HttpJson.singleStop", "/stop/\" + HttpJson.seg(stopId)",
          "resolveMissingNames", "invokeAll(tasks, 12L")))
check("resolved stop names are persisted as a partial snapshot", all(
      s in store_java for s in (
          "cacheStop(", "partial", "mergeStops", "catalogueJson(")))
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
# LAUNCH_CATALOGUE_REFRESH：以前呢度要求 `RouteDataPrefs.isDue`（每日／每 3 日
# 節流）。而家開 App 就更新一次，所以改成檢查「真 code 已改用共用 gate」；
# 用精確 `if (RouteDataPrefs.isDue(app)` 而唔係 `RouteDataPrefs.isDue` 字串，
# 避免被 KDoc 入面「唔再用 isDue」嘅說明呃到假陽性。
check("background catalogue auto-update at startup runs ONCE per cold start",
      "maybeAutoUpdateCatalogue" in viewmodel_kt
      and "CatalogueRefreshRunner.runOnce" in viewmodel_kt
      and "if (RouteDataPrefs.isDue(app)" not in viewmodel_kt
      and "RouteDataPrefs.isDue(app) &&" not in viewmodel_kt)

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
      i in settings_layout for i in ("@layout/update_pill", "advancedAppUpdate",
                                     "btnAdvanced", "tvPillStatus", "etRepo", "swAutoApp",
                                     "btnCheckApp", "btnDownloadApk",
                                     "btnReleasePage", "progressApp",
                                     "tvAppUpdateStatus", "tvReleaseNotes"))
      and 'android:id="@+id/tvAppVersion"'
      in read(RES / "layout/update_pill.xml"))

# --- 更新應用程式：深色版本膠囊（vX · build · ⟳ 🕐 ⧉）-----------------------
pill_xml = read(RES / "layout/update_pill.xml")
pill_bg = read(RES / "drawable/bg_update_pill.xml")
pill_icons = [read(RES / f"drawable/ic_pill_{n}.xml")
              for n in ("refresh", "history", "copy")]
check("update pill reproduces vX · build · refresh/history/copy bar",
      all(s in pill_xml for s in (
          'android:id="@+id/updatePill"', 'android:id="@+id/tvAppVersion"',
          'android:id="@+id/tvAppBuild"', 'android:id="@+id/tvPillDot"',
          'android:id="@+id/pillRefresh"', 'android:id="@+id/pillHistory"',
          'android:id="@+id/pillCopy"', '@drawable/bg_update_pill',
          '@drawable/ic_pill_refresh', '@drawable/ic_pill_history',
          '@drawable/ic_pill_copy', '@color/pill_text', '@color/pill_muted'))
      and '@color/pill_bg' in pill_bg
      and all('android:strokeColor="#FFFFFF"' in x for x in pill_icons))
check("all original download / install controls survive in advanced drawer",
      '@layout/update_pill' in settings_layout
      and 'android:id="@+id/advancedAppUpdate"' in settings_layout
      and 'android:id="@+id/btnAdvanced"' in settings_layout
      and 'android:id="@+id/tvPillStatus"' in settings_layout
      and all(i in settings_layout for i in (
          "btnCheckApp", "btnDownloadApk", "btnReleasePage",
          "progressApp", "tvAppUpdateStatus", "tvReleaseNotes", "etRepo", "swAutoApp")))
check("pill actions wired: re-check / history / copy / advanced toggle",
      all(s in settings_kt for s in (
          "fun versionLabel", "fun triggerCheck", "fun showUpdateHistory",
          "fun copyVersionInfo", "fun toggleAdvanced", "fun expandAdvanced",
          "fun renderPillStatus", "ClipboardManager", "ClipData.newPlainText",
          "AlertDialog.Builder", "pillRefresh.setOnClickListener",
          "pillHistory.setOnClickListener", "pillCopy.setOnClickListener",
          "renderPillStatus(failed = true)"))
      and 'tvAppVersion.text = versionLabel()' in settings_kt
      and 'tvAppBuild.text = AppUpdatePrefs.installedVersionCode(this).toString()'
      in settings_kt)
pill_strings = read(RES / "values/strings.xml")
check("pill strings all defined", all(
      f'<string name="{name}">' in pill_strings for name in (
          "pill_refresh_cd", "pill_history_cd", "pill_copy_cd",
          "pill_status_never", "pill_status_checked", "pill_status_latest",
          "pill_status_new", "pill_status_failed", "pill_status_checking",
          "pill_advanced_open", "pill_advanced_close", "pill_history_title",
          "pill_history_body", "pill_history_never", "pill_history_no_tag",
          "pill_on", "pill_off", "pill_copied", "pill_ok")))
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
# --- 定位附近路線嘅距離：由主頁搬入設定頁，對照「50 ▼」---------------------
radius_xml = read(RES / "layout/radius_picker.xml")
radius_kt = read(UI / "NearbyRadiusPrefs.kt")
check("settings page owns the dark big-number radius picker",
      'layout="@layout/radius_picker"' in settings_layout
      and 'NEARBY_RADIUS_SECTION' in settings_layout
      and all(s in radius_xml for s in (
          'android:id="@+id/radiusPickerRoot"',
          'android:id="@+id/radiusPickerValue"',
          'android:id="@+id/radiusPickerUnit"',
          'android:id="@+id/radiusPickerArrow"',
          '@drawable/bg_update_pill', '@drawable/ic_pill_arrow_down',
          '@string/nearby_radius_unit', '@color/pill_text'))
      and 'android:fillColor="#AEB7C2"'
      in read(RES / "drawable/ic_pill_arrow_down.xml"))
check("radius picker wired to a single-choice dialog + shared prefs",
      all(s in settings_kt for s in (
          "fun setupRadiusSection", "fun renderRadiusPicker",
          "setSingleChoiceItems", "NearbyRadiusPrefs.setRadiusMeters",
          "nearby_radius_saved", "radiusPickerRoot.setOnClickListener"))
      and "setupRadiusSection()" in settings_kt)
check("NearbyRadiusPrefs exposes 7 radii incl. 50 m, default 500 m",
      all(s in radius_kt for s in (
          "intArrayOf(50, 100, 200, 300, 500, 1000, 2000)",
          "DEFAULT_METERS = 500", "fun radiusMeters", "fun setRadiusMeters",
          "fun radiusIndex")))
check("MainActivity reads radius from Settings prefs and reloads on change",
      "NearbyRadiusPrefs.radiusMeters(this)" in main_kt
      and "lastRadius" in main_kt
      and "radiusChanged" in main_kt
      and "fun selectedRadius(): Int = NearbyRadiusPrefs.radiusMeters(this)"
      in main_kt)
check("radius strings all defined", all(
      f'<string name="{name}">' in read(RES / "values/strings.xml")
      for name in ("nearby_radius_section", "nearby_radius_title",
                   "nearby_radius_hint", "nearby_radius_unit",
                   "nearby_radius_cd", "nearby_radius_saved",
                   "home_swipe_hint", "pill_cancel")))

# --- ⟳ ETA 自動刷新 ---------------------------------------------------------
eta_prefs_kt = read(UI / "EtaRefreshPrefs.kt")
eta_vm_kt = read(UI / "EtaViewModel.kt")
eta_detail_kt = read(UI / "RouteDetailActivity.kt")
eta_settings_kt = read(UI / "SettingsActivity.kt")
eta_main_kt = read(UI / "MainActivity.kt")
eta_picker_xml = read(RES / "layout/eta_refresh_picker.xml")
check("ETA auto-refresh prefs: 4 intervals, ON by default, 30s, off = 0",
      all(s in eta_prefs_kt for s in (
          "longArrayOf(15_000L, 30_000L, 45_000L, 60_000L)",
          "DEFAULT_INTERVAL_MS = 30_000L", "fun enabled",
          "fun setEnabled", "fun intervalMs", "fun selectedIntervalMs",
          "fun setIntervalMs", "fun intervalIndex",
          "fun markAutoRefreshed", "fun lastAutoAt",
          "fun dueForForegroundRefresh", "fun statusLabel",
          "fun settingsLabel"))
      and "if (enabled(context)) selectedIntervalMs(context) else 0L"
      in eta_prefs_kt)
check("ViewModel polls EVERY visible ETA surface, interval read from prefs",
      all(s in eta_vm_kt for s in (
          "AUTO_REFRESH_ETA", "EtaRefreshPrefs.intervalMs(getApplication())",
          "delay(interval)", "fun refreshVisibleEta",
          "fun refreshNearbyCards", "fun onForeground", "fun onBackground",
          "private suspend fun fetchCards", "coroutineScope",
          "EtaRefreshPrefs.markAutoRefreshed", "fun startAutoRefresh"))
      and "delay(30_000L)" not in eta_vm_kt
      and "state.selection != null -> refresh()" in eta_vm_kt
      and "state.cards.isNotEmpty() && state.stops.isNotEmpty()" in eta_vm_kt
      and "else -> return" in eta_vm_kt)
check("nearby auto-refresh reuses CACHED stops, never re-locates",
      "fetchCards(stops)" in eta_vm_kt
      and "refreshNearbyCards(state.stops)" in eta_vm_kt
      and "LocationHelper.current" not in
          eta_vm_kt[eta_vm_kt.index("private fun refreshNearbyCards"):
                    eta_vm_kt.index("private suspend fun fetchCards")])
check("foreground-only: onResume restarts + catches up, onPause stops",
      "vm.onForeground()" in eta_main_kt
      and "vm.onBackground()" in eta_main_kt
      and "override fun onPause()" in eta_main_kt
      and "vm.onForeground()" in eta_detail_kt
      and "vm.onBackground()" in eta_detail_kt
      and "override fun onPause()" in eta_detail_kt
      and "onBackground()" in eta_vm_kt[eta_vm_kt.index("override fun onCleared"):])
check("Home + route detail show switch / interval / last auto time",
      'android:id="@+id/tvAutoRefreshStatus"' in main_layout
      and "EtaRefreshPrefs.statusLabel(this)" in eta_main_kt
      and "fun renderAutoRefreshStatus" in eta_main_kt
      and "EtaRefreshPrefs.statusLabel(this)" in eta_detail_kt
      and "fun renderFooter" in eta_detail_kt
      and "tvFooter" in eta_detail_kt)
check("Settings owns the ETA auto-refresh switch + big-number interval picker",
      'android:id="@+id/etaRefreshSection"' in settings_layout
      and 'android:id="@+id/swEtaAutoRefresh"' in settings_layout
      and '@layout/eta_refresh_picker' in settings_layout
      and 'android:id="@+id/tvEtaRefreshStatus"' in settings_layout
      and 'android:id="@+id/etaRefreshPickerRoot"' in eta_picker_xml
      and 'android:id="@+id/etaRefreshPickerValue"' in eta_picker_xml
      and "@string/eta_refresh_unit" in eta_picker_xml
      and all(s in eta_settings_kt for s in (
          "fun setupEtaRefreshSection", "fun renderEtaRefreshSection",
          "EtaRefreshPrefs.setEnabled", "EtaRefreshPrefs.setIntervalMs",
          "EtaRefreshPrefs.INTERVALS_MS[which]",
          "etaRefreshPickerRoot.setOnClickListener",
          "EtaRefreshPrefs.selectedIntervalMs(this)"))
      and "setupEtaRefreshSection()" in eta_settings_kt)
check("eta refresh strings + 4-entry interval array all defined", all(
      f'<string name="{name}">' in read(RES / "values/strings.xml")
      for name in ("eta_refresh_section", "eta_refresh_switch",
                   "eta_refresh_unit", "eta_refresh_cd", "eta_refresh_hint",
                   "eta_refresh_interval_title", "eta_refresh_status_on",
                   "eta_refresh_status_never", "eta_refresh_status_off",
                   "eta_refresh_settings_on", "eta_refresh_settings_off",
                   "eta_refresh_auto_tag", "eta_refresh_nearby_tag"))
      and 'name="eta_refresh_interval_labels"'
      in read(RES / "values/strings.xml")
      and read(RES / "values/strings.xml").count(
          'name="eta_refresh_interval_labels"') == 1)
check("auto-refresh does NOT poll with no data / before location consent",
      "else -> return   // 未授權／未載入：唔打 API" in eta_vm_kt
      and "awaitLocationPermission()" in eta_vm_kt
      and "refreshJob?.cancel()" in eta_vm_kt)

# --- 懸浮液態玻璃導航欄 + 免責聲明卡搬入設定頁 ------------------------------
glass_kt = read(UI / "NavGlass.kt")
glass_prefs_kt = read(UI / "NavGlassPrefs.kt")
disclaimer_xml = read(RES / "layout/disclaimer_card.xml")
check("nav tab renamed to 應用程式設置, disclaimer moved into Settings",
      '<string name="tab_more">⚙&#10;應用程式設置</string>'
      in read(RES / "values/strings.xml")
      and '<string name="more_title">應用程式設置</string>'
      in read(RES / "values/strings.xml")
      and 'android:text="@string/more_disclaimer"' not in main_layout
      and 'more_disclaimer_moved' in main_layout
      and '@layout/disclaimer_card' in settings_layout
      and 'DISCLAIMER_CARD' in settings_layout)
check("disclaimer card reproduces the dark rounded white-rim screenshot",
      'android:id="@+id/disclaimerCard"' in disclaimer_xml
      and '@drawable/bg_disclaimer' in disclaimer_xml
      and '@string/disclaimer_lead' in disclaimer_xml
      and '@string/disclaimer_excluded' in disclaimer_xml
      and 'android:color="#CCFFFFFF"'
      in read(RES / "drawable/bg_disclaimer.xml")
      and all(s in read(RES / "values/strings.xml") for s in (
          "disclaimer_lead", "disclaimer_excluded", "disclaimer_section",
          "AI 智能問路", "實時載客量", "九巴月票", "遊戲室", "街景")))
check("NAV_OVERLAY_POSITION: bar is a TRUE floating overlay, lifted off bottom",
      'NAV_GEOMETRY' in main_layout
      and 'NAV_OVERLAY_POSITION' in main_layout
      and '<FrameLayout xmlns' in main_layout
      and 'android:id="@+id/mainContent"' in main_layout
      and 'android:clipChildren="false"' in main_layout
      and '@drawable/bg_nav_glass' in main_layout
      and 'android:layout_height="56dp"' in main_layout
      and 'android:layout_gravity="bottom|center_horizontal"' in main_layout
      and 'android:layout_marginStart="28dp"' in main_layout
      and 'android:layout_marginEnd="28dp"' in main_layout
      and 'android:layout_marginBottom="96dp"' in main_layout
      and 'android:elevation="6dp"' in main_layout
      and 'android:id="@+id/bottomTabs"' in main_layout
      and main_layout.rstrip().endswith('</FrameLayout>')
      and all(old not in main_layout[main_layout.index('NAV_GEOMETRY'):]
              for old in ('android:layout_marginStart="12dp"',
                          'android:layout_marginBottom="12dp"',
                          'android:layout_marginBottom="8dp"',
                          'android:layout_gravity="center_horizontal"',
                          'android:layout_height="64dp"'))
      and all(Path(RES / f"drawable/{n}.xml").is_file() for n in (
          "bg_nav_glass", "bg_nav_glass_dark", "bg_nav_glass_contrast",
          "bg_tab_glass_selected")))
check("NAV_OVERLAY_POSITION: scrollable pages keep last items reachable",
      main_layout.count('android:paddingBottom="180dp"') >= 3
      and 'android:paddingBottom="200dp"' in main_layout
      and main_layout.count('android:clipToPadding="false"') >= 3)
check("all three glass variants share the 28dp true-pill radius",
      all('android:radius="28dp"' in read(RES / f"drawable/{n}.xml")
          for n in ("bg_nav_glass", "bg_nav_glass_dark",
                    "bg_nav_glass_contrast"))
      and '#F02C2C2F' in read(RES / "drawable/bg_nav_glass.xml")
      and 'android:radius="30dp"' not in read(RES / "drawable/bg_nav_glass.xml"))
check("selected tab = ONE darker inner pill + blue label (screenshot)",
      '#FF141416' in read(RES / "drawable/bg_tab_glass_selected.xml")
      and 'android:radius="24dp"'
      in read(RES / "drawable/bg_tab_glass_selected.xml")
      and '#55FFFFFF' not in read(RES / "drawable/bg_tab_glass_selected.xml")
      and '<color name="nav_tab_active">#4A9EFF</color>'
      in read(RES / "values/colors.xml")
      and '<color name="nav_tab_muted">#A8B0BA</color>'
      in read(RES / "values/colors.xml")
      and 'R.color.nav_tab_active' in glass_kt
      and 'R.color.nav_tab_muted' in glass_kt
      and '@color/nav_tab_active' in main_layout
      # NAV_NO_FAV_TAB: 3 粒導航欄 -> 1 粒 active + 2 粒 muted
      and main_layout.count('@color/nav_tab_muted') == 2)
check("geometry scales with screen width; re-applied on rotation / resume",
      all(s in glass_kt for s in (
          "fun geometry(", "SIDE_RATIO_PHONE = 0.06f",
          "SIDE_RATIO_WIDE = 0.18f", "WIDE_BREAKPOINT_DP = 600f",
          "SIDE_MIN_DP = 16f", "SIDE_MAX_DP = 256f",
          "BAR_HEIGHT_DP = 56", "BAR_BOTTOM_DP = 8",
          "BAR_BOTTOM_RATIO = 0.12f", "BAR_BOTTOM_MIN_DP = 48f",
          "BAR_BOTTOM_MAX_DP = 160f", "BAR_BOTTOM_DEFAULT_DP = 96",
          "fun bottomMarginDp(heightDp: Float)",
          "is android.widget.FrameLayout.LayoutParams",
          "Gravity.BOTTOM or", "heightDp * BAR_BOTTOM_RATIO",
          "widthDp >= WIDE_BREAKPOINT_DP",
          "lp.marginStart = sidePx", "lp.marginEnd = sidePx",
          "lp.bottomMargin", "Gravity.CENTER_HORIZONTAL",
          "geometry(activity, bar)", "ELEVATION_DP = 6f"))
      and '56dp' in read(RES / "values/strings.xml")
      and '離底約 12%' in read(RES / "values/strings.xml")
      and "override fun onConfigurationChanged" in main_kt
      and "NavGlass.apply(this, bottomTabs)" in main_kt
      and "56dp" in read(RES / "values/strings.xml"))
check("glass follows system night / high-contrast / reduced-motion settings",
      all(s in glass_kt for s in (
          "fun apply(", "fun selectTab(", "fun status(",
          "Configuration.UI_MODE_NIGHT_MASK", "high_text_contrast_enabled",
          "ANIMATOR_DURATION_SCALE", "TRANSITION_ANIMATION_SCALE",
          "setBackgroundBlurRadius", "runCatching"))
      and "NavGlassPrefs.followSystem" in glass_kt
      and "fun followSystem" in glass_prefs_kt
      and "fun setFollowSystem" in glass_prefs_kt)
check("MainActivity re-applies glass on resume / config change; no opaque tabs",
      all(s in main_kt for s in (
          "NavGlass.apply(this, bottomTabs)",
          "override fun onConfigurationChanged",
          "NavGlass.selectTab(this, tabs[i], tabPages[i] == index)"))
      and "setBackgroundColor(" not in main_kt
      and "fun setupNavGlassSection" in settings_kt
      and "R.id.swNavGlass" in settings_kt
      and "NavGlass.status(this)" in settings_kt)
nav_strings = read(RES / "values/strings.xml")
check("nav glass strings all defined", all(
      (f'<string name="{name}">' in nav_strings
       or f'<string name="{name}" ' in nav_strings)
      for name in ("nav_glass_section", "nav_glass_follow", "nav_glass_hint",
                   "nav_glass_status", "nav_glass_state_follow",
                   "nav_glass_state_fixed", "nav_glass_state_night",
                   "nav_glass_state_contrast", "nav_glass_state_reduced",
                   "nav_glass_state_day", "nav_glass_state_no_blur")))

# --- CN_ANDROID17_GLASS: 中國版 Android 17 / 國內 ROM 液態玻璃相容 -------------
compat_kt = read(UI / "NavGlassCompat.kt")
no_blur_files = (
    RES / "drawable/bg_nav_glass_no_blur.xml",
    RES / "drawable/bg_nav_glass_no_blur_dark.xml",
)
check("CN_ANDROID17_GLASS: pure decision layer + AOSP cross-window blur probe",
      all(s in compat_kt for s in (
          "object NavGlassCompat", "const val API_BLUR_MIN = 31",
          "const val API_MODERN_MIN = 35", "const val BLUR_DAY = 18",
          "const val BLUR_NIGHT = 26", "fun blurEnabled(",
          "fun blurRadius(night: Boolean)", "fun modernPlatform(",
          "fun backgroundName(", "fun crossWindowBlurEnabled(context: Context)",
          "isCrossWindowBlurEnabled", "Context.WINDOW_SERVICE",
          "fun applyWindowBlur(", "setBackgroundBlurRadius",
          "Window::class.java.getMethod", "runCatching",
          "getOrDefault(false)"))
      and "fun blurEnabled(\n        sdkInt: Int," in compat_kt
      and "&& crossWindowBlur" in compat_kt
      and "&& !reducedMotion" in compat_kt
      and "&& !highContrast" in compat_kt)
check("CN_ANDROID17_GLASS: no SDK>34 constants, vendor keys or future-only APIs",
      all(s not in compat_kt for s in (
          "VERSION_CODES.UPSIDE_DOWN_CAKE", "VERSION_CODES.VANILLA_ICE_CREAM",
          "VERSION_CODES.BAKLAVA", "VERSION_CODES.",
          "MeshGradient", "isCrossWindowBlurEnabledListener"))
      and "Build.VERSION.SDK_INT" in compat_kt
      and "GMS" in compat_kt)
check("CN_ANDROID17_GLASS: opaque no-blur fallbacks keep the same 28dp pill",
      all(f.is_file() for f in no_blur_files)
      and all('android:radius="28dp"' in read(f)
              and '<solid android:color="#FF' in read(f)
              for f in no_blur_files)
      and 'android:radius="28dp"' in read(RES / "drawable/bg_nav_glass.xml"))
check("CN_ANDROID17_GLASS: NavGlass.apply is probe-driven and status is honest",
      all(s in glass_kt for s in (
          "NavGlassCompat.blurEnabled(", "NavGlassCompat.crossWindowBlurEnabled(activity)",
          "NavGlassCompat.applyWindowBlur(activity, enabled, night)",
          "backdropBlur(activity, bar, blurOn, night)",
          "R.drawable.bg_nav_glass_no_blur_dark",
          "R.drawable.bg_nav_glass_no_blur",
          "R.string.nav_glass_state_no_blur", "blurOn", "backdropBlur(",
          # --- NAV_LIQUID: 液態層斷言併入同一條 check，static 總數保持 399 ---
          "object NavLiquid {", "fun enabled(follow: Boolean, reduced: Boolean, contrast: Boolean): Boolean =",
          "fun durationMs(", "fun sheenAlpha(", "fun rimAlpha(",
          "fun travelPx(", "fun springScale(frame: Float): Float",
          "NAV_LIQUID", "R.id.navLiquidLayer", "R.id.navLiquidRim",
          "R.id.navLiquidSheen", "GradientDrawable.Orientation.LEFT_RIGHT",
          "GradientDrawable.Orientation.TOP_BOTTOM", "layer.clipToOutline = true",
          "ValueAnimator.INFINITE", "ValueAnimator.REVERSE",
          "RenderEffect.createBlurEffect(", "Shader.TileMode.CLAMP",
          "fun stopLiquid()", "NavLiquid.enabled(follow, reduced, contrast)",
          "NavLiquid.springScale(",
          "liquid(activity, follow, night, contrast, reduced, !blurOn, material)",
          # --- CN_ADVANCED_MATERIAL: 中國版 Android OS 17 進階材質支援 ---
          "CN_ADVANCED_MATERIAL", "NavGlassCompat.materialProbe(activity)",
          "NavGlassMaterial.resolve(", "material.liquid", "material.blur",
          "R.string.nav_glass_material_status",
          "NavGlassCompat.materialSourceLabel(probe)",
          "NavGlassPrefs.materialMode(context)"))
      and "!blurOn && night" in glass_kt
      and "!blurOn -> R.drawable.bg_nav_glass_no_blur" in glass_kt
      and "setBackgroundBlurRadius" in glass_kt
      and 'android:id="@+id/navLiquidLayer"' in main_layout
      and 'android:id="@+id/navLiquidRim"' in main_layout
      and 'android:id="@+id/navLiquidSheen"' in main_layout
      and 'android:clickable="false"' in main_layout
      and 'NavGlass.stopLiquid()' in main_kt
      and "NAV_LIQUID" in nav_strings
      # 純決策層：NavGlass.kt 入面 NavLiquid object 區塊唔可以有 android 引用
      and "android." not in glass_kt.split("object NavLiquid {", 1)[1]
      # --- CN_ADVANCED_MATERIAL 檔級斷言（併入同一條，static 總數保持 399）---
      and all(s in compat_kt for s in (
          "object NavGlassMaterial {", "MODE_FOLLOW", "MODE_FULL", "MODE_SIMPLE",
          "data class Decision(", "fun resolve(", "fun sanitizeMode(mode: Int): Int",
          "data class MaterialProbe(", "fun materialProbe(context: Context): MaterialProbe",
          "MATERIAL_CANDIDATE_KEYS", "MATERIAL_DISABLE_KEYS", "CN_ROM_BRANDS",
          "Settings.Global.getString", "Settings.Secure.getString",
          "Settings.System.getString", "PowerManager", "isPowerSaveMode",
          "fun materialSourceLabel(probe: MaterialProbe): String",
          "OEM_DISABLED", "OEM_ENABLED", "POWER_SAVE", "USER_SIMPLE", "USER_FULL"))
      and "android." not in compat_kt.split("object NavGlassMaterial {", 1)[1]
      and all(s in read(UI / "NavGlassPrefs.kt") for s in (
          "private const val KEY_MATERIAL_MODE =",
          "fun materialMode(context: Context): Int",
          "fun setMaterialMode(context: Context, mode: Int)",
          "NavGlassMaterial.sanitizeMode"))
      and all(s in settings_kt for s in (
          "R.id.spNavMaterial", "spNavMaterial.adapter = ArrayAdapter(",
          "AdapterView.OnItemSelectedListener",
          "NavGlassPrefs.setMaterialMode(this@SettingsActivity, position)",
          "R.string.nav_glass_material_follow",
          "R.string.nav_glass_material_full",
          "R.string.nav_glass_material_simple"))
      and 'android:id="@+id/spNavMaterial"' in read(RES / "layout/activity_settings.xml")
      and all(f'<string name="{n}"' in nav_strings for n in (
          "nav_glass_material_label", "nav_glass_material_follow",
          "nav_glass_material_full", "nav_glass_material_simple",
          "nav_glass_material_status"))
      and "CN_ADVANCED_MATERIAL" in nav_strings
      and "唔編造廠商私有 key" in nav_strings)
check("CN_ANDROID17_GLASS: hint documents the China-ROM / Android 17 fallback",
      all(s in nav_strings for s in (
          '<string name="nav_glass_state_no_blur">',
          "中國版 Android 17", "實色玻璃", "唔靠 GMS"))
      and '<string name="nav_glass_hint" formatted="false">' in nav_strings)

# --- NAV_NO_FAV_BUTTON: 取消導航欄下嘅「☆ 加入收藏」長條掣 -------------------
detail_kt_now = read(UI / "RouteDetailActivity.kt")
check("NAV_NO_FAV_BUTTON: the fav-add button under the glass bar is REMOVED",
      "btnFavAdd" not in main_layout
      and "btnFavAdd" not in main_kt
      and '@string/fav_add' not in main_layout
      and 'name="fav_add"' not in nav_strings
      and "已加入收藏" not in main_kt
      and "NAV_NO_FAV_BUTTON" in main_layout)
check("NAV_NO_FAV_BUTTON: favourites still work from the detail page + list",
      "vm.addCurrentFavorite()" in detail_kt_now
      and "favoriteAdapter" in main_kt
      and "vm.favorites()" in main_kt
      and "到路線詳情頁撳「收藏」加入" in nav_strings
      and "長按可移除" in nav_strings)
# --- NAV_NO_FAV_TAB: 所有頁面導航欄都唔再有「★ 收藏」分頁 -----------------
check("NAV_NO_FAV_TAB: 加入收藏 tab removed from EVERY page's glass nav bar",
      'android:id="@+id/tabFav"' not in main_layout
      and "@string/tab_fav" not in main_layout
      and 'name="tab_fav"' not in read(RES / "values/strings.xml")
      and "tabFav" not in main_kt
      and "R.id.tabFav" not in main_kt
      and "NAV_NO_FAV_TAB" in main_layout
      and "NAV_NO_FAV_TAB" in main_kt
      and all(f'android:id="@+id/{n}"' in main_layout for n in
              ("bottomTabs", "tabHome", "tabSearch", "tabMore"))
      and "listOf(tabHome, tabSearch, tabMore)" in main_kt
      and "intArrayOf(0, 2, 3)" in main_kt
      and "NavGlass.selectTab(this, tabs[i], tabPages[i] == index)" in main_kt
      # 收藏入口保留：設定頁掣 + 詳情頁側 chip + 收藏頁 + 長按移除
      and "R.id.btnFavList" in main_kt
      and "selectTab(1)" in main_kt
      and 'android:id="@+id/pageFav"' in main_layout
      and 'android:id="@+id/favRecycler"' in main_layout
      and "favoriteAdapter" in main_kt
      and "vm.favorites()" in main_kt
      and "R.id.chipFav" in detail_kt_now
      and "vm.addCurrentFavorite()" in detail_kt_now
      and "長按可移除" in nav_strings)

# --- 主頁附近路線卡片 → 路線詳情（還原截圖，無地圖）-------------------------
detail_kt = read(UI / "RouteDetailActivity.kt")
detail_xml = read(RES / "layout/activity_route_detail.xml")
detail_item = read(RES / "layout/item_detail_stop.xml")
check("RouteDetailActivity registered in manifest",
      'android:name=".ui.RouteDetailActivity"' in manifest_text
      and 'android:parentActivityName=".ui.MainActivity"' in manifest_text
      and 'android:label="@string/detail_title"' in manifest_text)
# Strip XML comments first: the layout's own documentation deliberately names
# the map SDK it replaces, which must not count as an actual map view.
detail_markup = re.sub(r"<!--.*?-->", "", detail_xml, flags=re.S)
check("route detail has NO map view / SDK / WebView at all",
      'ROUTE_DETAIL_NO_MAP' in detail_xml
      and 'NO_MAP_PANEL' in detail_xml
      and 'bg_no_map' in detail_xml
      and not any(x in detail_markup for x in (
          "com.google.android.gms.maps", "MapView", "GoogleMap",
          "WebView", "android.permission.ACCESS_FINE_LOCATION"))
      and 'Google' not in detail_markup)
check("route detail reproduces red header + side chips + timeline list",
      all(s in detail_xml for s in (
          'android:id="@+id/detailHeader"', 'android:id="@+id/tvDetailTitle"',
          'android:id="@+id/btnDetailBack"', 'android:id="@+id/detailActionCalendar"',
          'android:id="@+id/detailActionNotice"', 'android:id="@+id/detailActionDirection"',
          'android:id="@+id/detailSideChips"', 'android:id="@+id/chipBoard"',
          'android:id="@+id/chipNearby"', 'android:id="@+id/chipFav"',
          'android:id="@+id/chipMore"', 'android:id="@+id/detailStopsRecycler"',
          'android:id="@+id/tvStopBubble"', 'android:id="@+id/tvDetailFooter"'))
      and '@color/brand' in detail_xml
      # CIRCULAR_TWO_ACTIONS: 循環線三顆 -> 兩顆，id 全部保留
      and 'CIRCULAR_TWO_ACTIONS' in detail_xml
      and 'CIRCULAR_TWO_ACTIONS' in read(UI / "RouteDetailActivity.kt")
      and "public static boolean showDirectionAction(boolean circular)" in read(API / "CircularRoutes.java")
      and "CircularRoutes.showDirectionAction(circular)" in read(UI / "RouteDetailActivity.kt")
      and "directionAction.visibility = if" in read(UI / "RouteDetailActivity.kt")
      and "View.GONE" in read(UI / "RouteDetailActivity.kt")
      # CIRCULAR_TWO_ACTIONS: 循環線只有日曆（服務時間及班次）＋通知兩顆
      and "public static List<String> visibleHeaderActions(boolean circular)" in read(API / "CircularRoutes.java")
      and "public static final String ACTION_CALENDAR" in read(API / "CircularRoutes.java")
      and "CircularRoutes.visibleHeaderActions(circular)" in read(UI / "RouteDetailActivity.kt")
      and "actions.contains(CircularRoutes.ACTION_CALENDAR)" in read(UI / "RouteDetailActivity.kt")
      and "actions.contains(CircularRoutes.ACTION_NOTICE)" in read(UI / "RouteDetailActivity.kt")
      and "所有循環線**只有呢兩個圖標**" in detail_xml
      and "服務時間及班次（同單向路線同一個 dialog" in detail_xml
      # DETAIL_THREE_ICONS: 日曆 / 通知 / ⇅ 三個 vector 圖標
      and 'DETAIL_THREE_ICONS' in detail_xml
      and '@drawable/ic_detail_calendar' in detail_xml
      and '@drawable/ic_detail_notice' in detail_xml
      and '@drawable/ic_detail_direction' in detail_xml
      and '@string/detail_action_calendar_cd' in detail_xml
      and '@string/detail_action_notice_cd' in detail_xml
      and 'android:text="▦"' not in detail_xml
      and 'android:text="★"' not in detail_xml
      and 'android:text="⇄"' not in detail_xml)
check("item row shows 3 arrivals + honest fare / alighting placeholders",
      all(f'android:id="@+id/tripRow{i}"' in detail_item for i in (1, 2, 3))
      and all(f'android:id="@+id/tripMin{i}"' in detail_item for i in (1, 2, 3))
      and all(s in detail_item for s in (
          'android:id="@+id/detailDot"', 'android:id="@+id/tvFare"',
          'android:id="@+id/tvAlight"', 'android:id="@+id/tvAlightTime"',
          'android:id="@+id/tvNoEta"', '@drawable/bg_detail_dot_off',
          '@color/detail_minutes'))
      and '@string/detail_fare_na' in detail_item
      and '@string/detail_na' in detail_item)
check("no invented fare / alighting numbers; footer explains the dashes",
      all(s in read(RES / "values/strings.xml") for s in (
          "detail_fare_na", "detail_footer_honest", "$5.6", "15:31"))
      and '車資 —' in read(RES / "values/strings.xml")
      and 'name="detail_no_map_label"' in read(RES / "values/strings.xml"))
# DETAIL_STOP_BLACK — 站名喺固定白底上必須係黑色，
# 唔可以再用會喺 values-night 變成近白色嘅 @color/text / @color/muted。
detail_markup_clean = re.sub(r"<!--.*?-->", "", detail_xml, flags=re.S)
item_markup_clean = re.sub(r"<!--.*?-->", "", detail_item, flags=re.S)
day_colors = read(RES / "values/colors.xml")
night_colors = read(RES / "values-night/colors.xml")
day_stop = re.search(
    r'<color name="detail_stop_text">([^<]*)</color>', day_colors)
day_aux = re.search(
    r'<color name="detail_aux_text">([^<]*)</color>', day_colors)
night_stop = re.search(
    r'<color name="detail_stop_text">([^<]*)</color>', night_colors)
night_aux = re.search(
    r'<color name="detail_aux_text">([^<]*)</color>', night_colors)
check("DETAIL_ALL_BLACK: stop names AND every same-colour label are PURE BLACK",
      '<color name="detail_stop_text">#000000</color>' in day_colors
      and '<color name="detail_aux_text">#000000</color>' in day_colors
      and bool(day_stop) and day_stop.group(1).strip() == "#000000"
      and bool(day_aux) and day_aux.group(1).strip() == "#000000"
      and '@color/detail_stop_text' in detail_markup_clean
      and '@color/detail_stop_text' in item_markup_clean
      and '@color/detail_aux_text' in detail_markup_clean
      and '@color/detail_aux_text' in item_markup_clean
      # every textColour on this screen is either pure black or the blue accent
      and not any(c in detail_markup_clean for c in (
          '#374151', '#6B7787', '#93A2B5', '#EAF1FA')))
check("DETAIL_ALL_BLACK: dark mode keeps the SAME pure black (no near-white flip)",
      '<color name="detail_stop_text">#000000</color>' in night_colors
      and '<color name="detail_aux_text">#000000</color>' in night_colors
      and bool(night_stop) and night_stop.group(1).strip() == "#000000"
      and bool(night_aux) and night_aux.group(1).strip() == "#000000")
check("DETAIL_ALL_BLACK: footer pinned pale so black text is never on black",
      'android:id="@+id/tvDetailFooter"' in detail_markup_clean
      and 'DETAIL_ALL_BLACK' in detail_xml
      and 'android:background="#F4F7FB"' in detail_markup_clean
      and '@color/surface' not in detail_markup_clean)
check("DETAIL_ALL_BLACK: no generic theme text colours remain on this screen",
      not any(x in detail_markup_clean for x in ('@color/text', '@color/muted'))
      and not any(x in item_markup_clean for x in ('@color/text', '@color/muted')))
check("DETAIL_ALL_BLACK: red header / chips keep white text, blue minutes stay blue",
      detail_markup_clean.count('android:textColor="#FFFFFF"') >= 5
      and '@color/detail_minutes' in item_markup_clean
      and 'android:background="#FFFFFF"' in detail_markup_clean
      and '<color name="detail_minutes">#1A4E8C</color>' in day_colors)
check("BOTH Home cards and search rows open the NO-MAP RouteDetailActivity",
      main_kt.count('Intent(this, RouteDetailActivity::class.java)') >= 2
      and 'RouteDetailActivity.EXTRA_STOP_ID' in main_kt
      and 'fun openHistoryItem(item: SearchHistoryItem)' in main_kt
      and 'RouteDetailActivity.EXTRA_BOUND, item.bound' in main_kt
      and 'item.dest.removePrefix("往 ").trim()' in main_kt
      and 'vm.loadStops(result.route, result.company, result.bound)' not in main_kt
      and 'SearchHistoryPrefs.add(this' in main_kt)
check("search home PRELOADS official catalogue rows before any keystroke",
      'public static List<Result> preload()' in read(API / "RouteSearch.java")
      and 'RouteSearch.preload()' in read(UI / "EtaViewModel.kt")
      and 'if (q.isEmpty()) RouteSearch.preload() else RouteSearch.search(q)'
      in read(UI / "EtaViewModel.kt")
      and 'vm.searchRoutes("")' in main_kt
      and 'R.string.search_preload_empty' in read(UI / "EtaViewModel.kt")
      and 'name="search_preload_empty"' in read(RES / "values/strings.xml")
      and 'name="search_empty"' in read(RES / "values/strings.xml"))
check("preload uses official catalogues only; no invented destinations",
      'public static List<Result> preload()' in read(API / "RouteSearch.java")
      and all(s in read(API / "RouteSearch.java") for s in (
          "kmbPreload", "ctbPreload", "nlbPreload", "gmbPreload",
          "railPreload", "RouteDataStore.catalogueJson",
          "MtrCatalog.LINE_CODES", "MtrCatalog.LRT_ROUTES",
          "MtrCatalog.MTR_BUS_ROUTES", "目錄預載（未查方向）"))
      # HKE_MOBILITY_CATEGORIES: 舊版一刀切 24 條會 cut 走港鐵／輕鐵／小巴，
      # 而家改為全部官方目錄列如實返回。
      and "ALL_ROUTES_LIMIT = Integer.MAX_VALUE" in read(API / "RouteSearch.java")
      and "subList(0, 24)" not in read(API / "RouteSearch.java")
      and "return out;" in read(API / "RouteSearch.java"))

# --- HKE_MOBILITY_CATEGORIES: 還原香港出行易式分類 ---------------------------
# 呢段位於 checker 前半，SEARCH_UI 段嘅 search_strings / search_colors 等
# 變數要到後面先定義，所以呢度一律用局部 read()，唔依賴執行順序。
hke_rules = ROOT / "app/src/main/java/hk/eta/app/ui/SearchCategoryRules.kt"
_hke = read(hke_rules) if hke_rules.is_file() else ""
_hke_strings = read(RES / "values/strings.xml")
_hke_colors = read(RES / "values/colors.xml")
_hke_night = read(RES / "values-night/colors.xml")
check("HKE_MOBILITY_CATEGORIES: pure decision layer, no Android / no colour",
      hke_rules.is_file()
      and all(s in _hke for s in (
          "object SearchCategoryRules", "fun belongs(", "fun isBusCompany(",
          "fun isRail(", "capPerCompany(", "val ALL_ROUTES_LIMIT: Int =",
      "ALL_ROUTES_LIMIT == Int.MAX_VALUE"))
      and all(s not in _hke for s in (
          "import android", "import androidx", "@color/", "getString(")))
check("HKE_MOBILITY_CATEGORIES: operator constants match the Java data layer",
      all(f'const val {n} = "{v}"' in _hke for n, v in (
          ("KMB", "KMB"), ("CTB", "CTB"), ("NLB", "NLB"), ("GMB", "GMB"),
          ("MTR", "MTR"), ("LRT", "LRT"), ("MTRB", "MTRB"), ("JOINT", "JOINT")))
      and 'public static final String JOINT = "JOINT"' in
          read(API / "JointRoutes.java")
      and "HKE_MOBILITY_CATEGORIES" in _hke)
check("HKE_MOBILITY_CATEGORIES: bus-company chips show ONLY their own routes",
      all(s in _hke for s in (
          "CHIP_ALL -> isBusCompany(company)",
          "CHIP_CTB -> company == CTB || company == JOINT",
          'CHIP_KMB -> (company == KMB || company == JOINT) && brand == "KMB"',
          'CHIP_LWB -> (company == KMB || company == JOINT) && brand == "LWB"',
          "CHIP_NLB -> company == NLB"))
      and "company == KMB || company == CTB || company == NLB || company == JOINT"
          in _hke
      # 舊版「所有巴士類別」直接 return true，連小巴／鐵路都撈埋
      and "CHIP_ALL -> true" not in _hke
      and "0 -> true" not in main_kt)
check("HKE_MOBILITY_CATEGORIES: MTR category shows heavy rail + LRT + MTR Bus",
      "CAT_RAIL -> isRail(company)" in _hke
      and "company == MTR || company == LRT || company == MTRB" in _hke
      and "public static final String[] LRT_ROUTES" in
          read(API / "MtrCatalog.java")
      and "EtaRepository.LRT" in read(API / "RouteSearch.java")
      and "港鐵類別：重鐵／輕鐵路線" in _hke_strings)
check("HKE_MOBILITY_CATEGORIES: minibus routes belong to the minibus category",
      "CAT_MINIBUS -> company == GMB" in _hke
      and "小巴類別：綠色專線小巴官方目錄" in _hke_strings
      and "2 -> company == EtaRepository.GMB" not in main_kt)
check("HKE_MOBILITY_CATEGORIES: Activity delegates to the shared rule layer",
      "company, searchCategory, busChip, GovServiceHours.Brand.brand(route)" in main_kt
      and "SearchCategoryRules.capPerCompany(rows) { it.company }" in main_kt
      and "rows.take(6)" not in main_kt
      and "import hk.eta.app.api.JointRoutes" not in main_kt)
check("ALL_ROUTES: preload returns EVERY official catalogue row, no per-company cap",
      all(s in read(API / "RouteSearch.java") for s in (
          "ALL_ROUTES_LIMIT = Integer.MAX_VALUE", "return out;",
          "HKE_MOBILITY_CATEGORIES", "LRT_ROUTES.length",
          "MTR_BUS_ROUTES.length", "LINE_CODES.length"))
      and all(s not in read(API / "RouteSearch.java") for s in (
          "subList(0, 24)", "added < 6", "added < 2",
          "Math.min(4, MtrCatalog", "Math.min(3, MtrCatalog",
          "Math.min(2, MtrCatalog", "out.size() > 40"))
      and "3 列" not in read(RES / "values/strings.xml"))
check("HKE_MOBILITY_CATEGORIES: colours / layout / previous rounds untouched",
      'name="search_bg"' in _hke_colors
      and 'name="search_' not in _hke_night
      and all(f'android:id="@+id/{n}"' in main_layout for n in (
          "catMtr", "catBus", "catGmb", "chipAll", "chipCtb", "chipKmb",
          "chipLwb", "chipNlb", "chipDb"))
      and "KOTLIN_APP_SCOPE" in viewmodel_kt
      and "SEARCH_LOCAL_DATA" in main_kt
      and "CatalogueRefreshRunner.runOnce" in app_kt)
check("detail reuses open-data layer; every chip / header action has a real action",
      all(s in detail_kt for s in (
          "ViewModelProvider(this)[EtaViewModel::class.java]",
          "vm.loadStops(route, company, requestedBound)", "vm.selectStop(", "vm.refresh()",
          "vm.toggleDirection()", "vm.addCurrentFavorite()",
          "App1933Format.cardsFrom", "App1933Format.stopLabel",
          "class DetailStopAdapter", "fun submit(", "fun boardAt(",
          "fun buildTitle(", "Intent(this, SettingsActivity::class.java)"))
      and all(f'R.id.{n}' in detail_kt for n in (
          "btnDetailBack", "detailActionCalendar", "detailActionNotice",
          "detailActionDirection", "chipBoard", "chipNearby", "chipFav", "chipMore"))
       # CIRCULAR_TWO_ACTIONS: 循環線收起第三顆，普通路線維持三顆
       and "CircularRoutes.showDirectionAction(circular)" in detail_kt
       and "View.VISIBLE" in detail_kt and "View.GONE" in detail_kt
       and "CIRCULAR_TWO_ACTIONS" in read(API / "CircularRoutes.java")
       # DETAIL_THREE_ICONS: 三個圖標各有實際功能
       and "fun showServiceHours(" in detail_kt
       and "fun showNotices(" in detail_kt
       and "latestEtas = state.etas" in detail_kt
       # LOCAL_SERVICE_HOURS: 服務時間及班次只讀本機路線資料，唔行即時 ETA
       and "LocalServiceHours.lookup(company, route)" in detail_kt
       and "TimeUtils.hhmm(info.updatedAt)" in detail_kt
       and "info.hasSnapshot" in detail_kt
       and "info.csvOnly" in detail_kt
       and "info.rows" in detail_kt
       and "detail_calendar_local_header" in detail_kt
       and "detail_calendar_eta_note" in detail_kt
       and "public static Info lookup(String company, String route)" in read(API / "LocalServiceHours.java")
       and "RouteDataStore.readText(file)" in read(API / "LocalServiceHours.java")
       and "catalogueJson" not in read(API / "LocalServiceHours.java")
       # 日曆 dialog 已經完全唔讀即時 ETA 嘅班次時間
       and "etaMillis" not in detail_kt
       and "detail_calendar_no_timetable" in detail_kt
       and "remarkTc" in detail_kt
       and "AlertDialog.Builder(this)" in detail_kt
       # 循環線日曆同單向完全一樣 = 服務時間及班次，唔縮水
       and "STILL 服務時間及班次" in detail_kt
       and "visibleHeaderActions(boolean)" in detail_kt
       # GOV_SERVICE_HOURS: 日曆讀政府公開班次資料
       and "GovServiceHours.lookup(" in detail_kt
       and "RouteDataStore.serviceHoursDir()" in detail_kt
       and "gov.available" in detail_kt
       and "gov.headways" in detail_kt
       and "detail_calendar_gov_header" in detail_kt
       and "GOV_SERVICE_HOURS" in read(API / "GovServiceHours.java")
       and "pt-headway-tc/" in read(API / "GovServiceHours.java")
       and "DATA_LAST_UPDATED_DATE.csv" in read(API / "GovServiceHours.java")
       and "frequencies.txt" in read(API / "GovServiceHours.java")
       # GOV_IN_ROUTE_UPDATE: 政府班次資料正式加入「線路資料更新」
       and "GOV_IN_ROUTE_UPDATE" in read(API / "GovServiceHours.java")
       and "public static final String SOURCE_FILE" in read(API / "GovServiceHours.java")
       and "public static SourceOutcome outcome(" in read(API / "GovServiceHours.java")
       and "public static final String GOV_SOURCE" in read(API / "RouteDataStore.java")
       and "private static final int EXTRA_SOURCES = 2" in read(API / "RouteDataStore.java")
       and "private static SourceStatus refreshGovServiceHours(" in read(API / "RouteDataStore.java")
       and "statuses.add(refreshGovServiceHours(pool, ctx.getFilesDir()));" in read(API / "RouteDataStore.java")
       and "s.file == RouteDataStore.GOV_SOURCE" in read(UI / "SettingsActivity.kt")
       and "sources_gov_files" in read(UI / "SettingsActivity.kt")
       and "RouteDataStore.serviceHoursDir()?.listFiles()?.size" in read(UI / "MainActivity.kt")
       and "sources_gov_files" in read(RES / "values/strings.xml")
       and "sources_gov_skipped" in read(RES / "values/strings.xml")
       and "政府公開班次資料" in read(RES / "values/strings.xml"))
check("detail strings all defined", all(
      f'<string name="{name}">' in read(RES / "values/strings.xml")
      for name in ("detail_title", "detail_no_map_label", "detail_footer_honest",
                   "detail_fare_na", "detail_alight", "detail_na", "detail_no_eta",
                   "detail_chip_board", "detail_chip_nearby", "detail_chip_fav",
                   "detail_chip_more", "detail_action_trips_cd",
                   "detail_action_fav_cd", "detail_action_direction_cd",
                   "detail_fav_ok", "detail_fav_need",
                   "detail_trips_refreshed", "detail_no_stops",
                   "detail_action_calendar_cd", "detail_action_notice_cd",
                   "detail_calendar_title", "detail_calendar_next",
                   "detail_calendar_no_timetable", "detail_calendar_no_eta",
                   "detail_calendar_refresh", "detail_notice_title",
                   "detail_notice_from_eta", "detail_notice_empty",
                   "detail_dialog_ok", "detail_calendar_local_header",
                   "detail_calendar_no_snapshot", "detail_calendar_csv_only",
                   "detail_calendar_eta_note", "detail_notice_refresh",
                   "sources_gov_files", "sources_gov_skipped")))

check("default_github_repo is defined",
      "default_github_repo" in read(RES / "values/strings.xml"))
check("default_github_repo points at this repo",
      '<string name="default_github_repo">benyeung08/hketa</string>'
      in read(RES / "values/strings.xml"))
check("preflight script guards every required file", all(
      s in read(ROOT / "scripts/preflight.py") for s in (
          "REQUIRED", "hketa/app/build.gradle.kts", "missing", "empty",
          "::error::", "hketa-debug.keystore", "icon-source.png")))
check("preflight runs first in CI",
      "Preflight - verify uploaded files" in workflow
      and workflow.index("Preflight - verify uploaded files")
      < workflow.index("JDK 17"))
check("CI preflight is self-contained (no dependency on scripts/)",
      "SELF_CONTAINED_PREFLIGHT" in workflow
      and "python3 - <<'PY'" in workflow
      and "if [ ! -f scripts/preflight.py ]" not in workflow
      and "python3 scripts/preflight.py" not in workflow)
check("inline preflight lists every missing file", all(
      s in workflow for s in ("缺少 ", "多咗一層 hketa/", "原始碼得",
                              "scripts/check_api_fields.py",
                              "app/hketa-debug.keystore",
                              "ic_launcher_foreground.png")))
check("inline preflight catches OLD-content drift", all(
      s in workflow for s in ("CONTENT DRIFT", "內容仍係舊版",
                              "/app/hketa-release.keystore",
                              'android:roundIcon="@mipmap/ic_launcher_round"',
                              "hketaDev", "benyeung08/hketa")))
check("local preflight mirrors drift checks", all(
      s in read(ROOT / "scripts/preflight.py") for s in
      ("Content drift", "/app/hketa-release.keystore",
       "ic_launcher_round", "hketaDev", "benyeung08/hketa")))
check("drift errors include an exact fix hint", all(
      s in workflow for s in ("→ 修復：", "加一行：/app/hketa-release.keystore",
                              '改成：android:roundIcon=',
                              'default_github_repo">benyeung08/hketa')))
check("preflight tells user to full-sync, not patch one by one", all(
      s in workflow for s in ("唔好逐個檔改", "git add . && git commit",
                              "一次過拖「成個資料夾」")))
fix_drift = read(ROOT / "scripts/fix_drift.py")
DRIFT_REPAIR_DETAIL = (
    "→ scripts/fix_drift.py 唔見咗或仍係舊版；由完整 zip 覆蓋，"
    "或全量 git add -A && git commit && git push（唔好逐個檔 upload）")
check("one-shot drift repair script exists and is idempotent", all(
      s in fix_drift for s in ("fix_gitignore", "fix_manifest", "fix_strings",
                               "benyeung08/hketa", "ic_launcher_round",
                               "已經係新版", "Full sync")), DRIFT_REPAIR_DETAIL)

# --- CI: surface the REAL Gradle error (Assemble signed release APK) ----------
build_error = read(ROOT / "scripts/show_build_error.py")
check("Gradle build logs are captured with pipefail + tee", all(
      s in workflow for s in ("set -o pipefail", "tee build-release.log",
                              "id: assemble_release")))
# --- Kotlin compile pitfalls (real build-release.log: compileReleaseKotlin) ---
location_helper = read(UI / "LocationHelper.kt")
# Legit top-level symbols of kotlinx-coroutines-core/android 1.8.1.
COROUTINES_OK = {
    "kotlinx.coroutines.CoroutineScope", "kotlinx.coroutines.Deferred",
    "kotlinx.coroutines.CompletableDeferred", "kotlinx.coroutines.Dispatchers",
    "kotlinx.coroutines.Job", "kotlinx.coroutines.SupervisorJob",
    "kotlinx.coroutines.CoroutineExceptionHandler",
    "kotlinx.coroutines.async", "kotlinx.coroutines.awaitAll",
    "kotlinx.coroutines.launch", "kotlinx.coroutines.delay",
    "kotlinx.coroutines.withContext", "kotlinx.coroutines.withTimeoutOrNull",
    "kotlinx.coroutines.withTimeout", "kotlinx.coroutines.isActive",
    "kotlinx.coroutines.cancel", "kotlinx.coroutines.cancelAndJoin",
    "kotlinx.coroutines.joinAll", "kotlinx.coroutines.coroutineScope",
    "kotlinx.coroutines.supervisorScope", "kotlinx.coroutines.yield",
    "kotlinx.coroutines.flow.Flow", "kotlinx.coroutines.flow.flow",
    "kotlinx.coroutines.flow.flowOn", "kotlinx.coroutines.flow.collect",
    "kotlinx.coroutines.flow.MutableStateFlow", "kotlinx.coroutines.flow.StateFlow",
}
bad_imports = []
for kt in sorted(UI.rglob("*.kt")):
    for imp in re.findall(r"^import\s+(kotlinx\.coroutines[\w.]*)",
                          kt.read_text(encoding="utf-8"), re.M):
        if imp not in COROUTINES_OK:
            bad_imports.append(f"{kt.name}: {imp}")
check("no non-existent coroutines imports (Deferred.await is a member)",
      not bad_imports, ", ".join(bad_imports[:3]))
check("LocationHelper does not import kotlinx.coroutines.await",
      "import kotlinx.coroutines.await" not in location_helper)
check("LocationListener provider callbacks use non-null String (API 34 @NonNull)",
      "override fun onProviderEnabled(provider: String)" in location_helper
      and "override fun onProviderDisabled(provider: String)" in location_helper
      and "override fun onProviderEnabled(provider: String?)" not in location_helper
      and "override fun onProviderDisabled(provider: String?)" not in location_helper)
check("LocationHelper keeps all 3 legacy callbacks for minSdk 24",
      all(s in location_helper for s in
          ("override fun onStatusChanged(", "override fun onProviderEnabled(",
           "override fun onProviderDisabled(", "override fun onLocationChanged(")))

# --- Java compile pitfall (build-release.log: compileReleaseJavaWithJavac) ---
mtr_catalog = read(API / "MtrCatalog.java")
check("MtrCatalog has lower(String[]) overload for CSV header rows",
      "private static List<String> lower(String[] cells)" in mtr_catalog
      and "return lower(Arrays.asList(cells));" in mtr_catalog
      and "import java.util.Arrays;" in mtr_catalog)
check("every lower(rows.get(...)) call has an applicable overload",
      mtr_catalog.count("= lower(rows.get(0))") == 3
      and "private static List<String> lower(List<String> cells)" in mtr_catalog)
check("firstWithAll / firstWithAny still consume List<String> header",
      all(s in mtr_catalog for s in
          ("private static int firstWithAll(List<String> header",
           "private static int firstWithAny(List<String> header")))

check("CI no longer hides the error behind --stacktrace", all(
      s in workflow for s in ("NO --stacktrace on purpose", "--console=plain"))
      and "gradle assembleDebug --no-daemon --stacktrace" not in workflow
      and "gradle assembleRelease --no-daemon --stacktrace" not in workflow
      and "gradle assembleDebug" not in workflow)
check("distiller warns when old stacktrace-shaped log is seen",
      "新 workflow 未生效" in build_error,
      "→ scripts/show_build_error.py 唔見咗或仍係舊版；由完整 zip 覆蓋，"
      "或全量 git add -A && git commit && git push")
check("failure steps distil the real error instead of stack frames", all(
      s in workflow for s in ("Show real Gradle error (release)",
                              "steps.assemble_release.outcome",
                              "scripts/show_build_error.py",
                              "Upload full Gradle release log",
                              "hketa-gradle-release-log")))
check("error distiller matches real failure categories", all(
      s in build_error for s in ("What went wrong", "Execution failed for task",
                                 "Caused by", "Unresolved reference", "AAPT2",
                                 "Keystore", "Could not resolve",
                                 "SDK location", "GITHUB_STEP_SUMMARY")))
check("preflight validates keystore + PNG binary signatures", all(
      s in workflow for s in ("BINARY INTEGRITY", "\\x30\\x82", "89PNG",
                              "mipmap-*/ic_launcher*.png")))
check("local preflight mirrors binary checks", all(
      s in read(ROOT / "scripts/preflight.py") for s in
      ("Binary integrity", "\\x30\\x82", "89PNG", "mipmap-*/ic_launcher*.png")))

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

# --- 正式版 APK 對應版本發布（Releases） ---------------------------------------
release_script = read(ROOT / "scripts/verify_release_version.py")
app_update_java = read(API / "AppUpdate.java")

# 診斷放喺 CHECKER（已更新的檔）而唔係 workflow/yml（可能未更新的檔）：
# 舊版 yml 嘅 inline preflight 唔會檢查新 drift，但新 checker 一定會行，
# 所以由呢度印出「缺邊一個字串」，唔使再估。必須放喺所有 release checks
# 之前 —— 否則舊 yml 會先令後面嘅 .index() 拋 ValueError，診斷印唔到。
RELEASE_TOKENS = (
    "tag_name: ${{ github.ref_name }}",
    "name: HK ETA ${{ github.ref_name }}",
    "body_path: RELEASE_BODY.md",
    "RELEASE_NOTES_${TAG}.md",
    "Compose release body for this version",
    "generate_release_notes: false",
    "fail_on_unmatched_files: true",
    "prerelease: ${{ contains(github.ref_name, '-') }}",
)
missing_release_tokens = [s for s in RELEASE_TOKENS if s not in workflow]
if missing_release_tokens:
    print(f"::error::Release 版本對應：workflow 仍係舊版，缺以下 "
          f"{len(missing_release_tokens)} 項字串：")
    for s in missing_release_tokens:
        print(f"::error::  - 缺「{s}」→ 由完整 zip 覆蓋 "
              ".github/workflows/build-apk.yml")
    print("::error::新 checker 已傳、只係 yml 未同步；"
          "只需覆蓋 .github/workflows/build-apk.yml 呢一個檔即可。")

# Both published notes files are OPTIONAL: the workflow has an if/else
# fallback that writes a short version-matched body when a tag has no notes.
# v1.0.3 is the CURRENT release; v1.0.0 / v1.0.1 / v1.0.2 are kept as history.
# A missing / half-written (Chinese-only) file is reported as a drift below.
release_notes_v100 = ROOT / "RELEASE_NOTES_v1.0.0.md"
release_notes_v101 = ROOT / "RELEASE_NOTES_v1.0.1.md"
release_notes_v102 = ROOT / "RELEASE_NOTES_v1.0.2.md"
release_notes_v103 = ROOT / "RELEASE_NOTES_v1.0.3.md"
for _f in (release_notes_v100, release_notes_v101, release_notes_v102):
    if not _f.is_file():
        print(f"[INFO] {_f.name} 未上傳；"
              "workflow 會用版本對應嘅簡短 Release 內文（fallback），唔影響發布。")


def wpos(needle):
    """Safe .index() — returns -1 instead of raising on an old workflow."""
    return workflow.find(needle)


def one_apk_asset():
    """Count .apk lines ONLY inside step 23's `files: |` block.

    The backfill job added later has its own `path: |` block with .apk lines;
    scanning to EOF would wrongly count those too.
    """
    start = wpos("Attach signed release APK")
    if start < 0:
        return False
    m = re.search(r"(?m)^[ ]{10}files: \|\n((?:[ ]{12,}[^\n]*\n)+)",
                  workflow[start:])
    if not m:
        return False
    return len([l for l in m.group(1).splitlines()
                if ".apk" in l and l.strip()
                and not l.strip().startswith("#")]) == 1


# --- MAVEN_429_RETRY: Maven Central 429「Too Many Requests」-------------------
# GitHub Actions 共享出口 IP 會被 repo.maven.apache.org 限流（常見於
# jsr305-3.0.2.pom）。呢個係暫時性網絡失敗，唔係 code / 上傳 / 簽名問題。
distiller = read(ROOT / "scripts/show_build_error.py")
check("MAVEN_429_RETRY: CI caches Gradle dependencies instead of re-downloading",
      "MAVEN_429_RETRY" in workflow
      and "cache: gradle" in workflow
      and "MAVEN_429_RETRY" in distiller)
check("MAVEN_429_RETRY: BOTH assemble steps retry transient 429 / 5xx / timeout",
      all(s in workflow for s in (
          "for attempt in 1 2 3; do", "Too Many Requests",
          "Received status code 5[0-9][0-9]", "delay=$((attempt * 30))",
          'if [ "$ok" -ne 1 ]; then exit 1; fi'))
      and workflow.count("for attempt in 1 2 3; do") >= 2
      and workflow.count('if [ "$ok" -ne 1 ]; then exit 1; fi') >= 2)
check("MAVEN_429_RETRY: non-network failures are NOT retried (real error shows)",
      "Gradle 失敗唔係依賴下載問題，唔重試" in workflow
      and "交畀下一個 step 顯示真正錯誤" in workflow
      and workflow.count("Gradle 失敗唔係依賴下載問題，唔重試") >= 2)
check("MAVEN_429_RETRY: distiller recognises 429 and says just Re-run jobs",
      "Received status code 429|Too Many Requests|status code 429" in distiller
      and "共享出口 IP 被限流" in distiller
      and "唔使改任何檔" in distiller)
check("CI builds a SIGNED RELEASE (production) APK, not just debug",
      all(s in workflow for s in (
          "Assemble signed release APK", "gradle assembleRelease",
          "app/build/outputs/apk/release/app-release.apk"))
      and 'getByName("release")' in app_gradle
      and 'isMinifyEnabled = false' in app_gradle)
check("RELEASE_VERSION_GATE: tag == gradle version == actual APK badging",
      all(s in workflow for s in (
          "RELEASE_VERSION_GATE", "scripts/verify_release_version.py",
          "dump badging", "build-tools/34.0.0/aapt", "apk-badging.txt",
          "Verify release version matches tag", "VERSION_INFO.txt"))
      and all(s in release_script for s in (
          "RELEASE_VERSION_GATE", "def normalize_tag", "def parse_gradle",
          "def parse_badging", "def signing_fingerprint", "def apk_sha256",
          "tag 與 Gradle 版本唔對應", "APK 內容與 Gradle 版本唔對應",
          "versionCode 唔對應", "EXPECTED_PACKAGE",
          "hashlib.sha256", "GITHUB_STEP_SUMMARY"))
      and "versionName" in release_script and "versionCode" in release_script)
check("version gate runs AFTER signing check and BEFORE publishing",
      0 <= wpos("Verify APK signing certificate")
      < wpos("Verify release version matches tag")
      < wpos("Attach signed release APK"))
check("Release is explicitly tied to the matching tag/version",
      not missing_release_tokens
      and "RELEASE_BODY.md" in workflow
      and "No RELEASE_NOTES_${TAG}.md; using the short version-matched body."
      in workflow)
check("version-matched release notes are OPTIONAL (fallback body always built)",
      # 刻意唔硬性要求 RELEASE_NOTES_v1.0.0.md 存在：
      # workflow 有 if/else，缺檔時用簡短說明，兩者都係對應版本。
      "RELEASE_NOTES_${TAG}.md" in workflow
      and 'cp "RELEASE_NOTES_${TAG}.md" RELEASE_BODY.md' in workflow
      and "> RELEASE_BODY.md" in workflow
      and (not release_notes_v100.is_file()
           or all(s in release_notes_v100.read_text(encoding="utf-8")
                  for s in ("HK ETA v1.0.0", "## English",
                            "hketa-v1.0.0-release.apk"))))
check("exactly ONE .apk asset — the signed release — is attached",
      "hketa-${{ github.ref_name }}-release.apk" in workflow
      and "hketa-release-apk" in workflow
      and "SIGNING_FINGERPRINT.txt" in workflow
      and "VERSION_INFO.txt" in workflow
      and 'cp "$APK" "hketa-${GITHUB_REF_NAME}-release.apk"' in workflow
      and one_apk_asset())
check("in-app updater stays compatible: first .apk asset wins",
      'endsWith(".apk")' in app_update_java
      and "break;" in app_update_java
      and 'public static int compareVersions' in app_update_java)
# RELEASE_NOTES_v1.0.1: gradle fields are the SINGLE SOURCE OF TRUTH.
# Never hard-code the numbers here — bumping for v1.0.3 must not break CI.
_cur_code = re.search(r"\bversionCode\s*=\s*(\d+)", app_gradle)
_cur_name = re.search(r'\bversionName\s*=\s*"([^"]+)"', app_gradle)
_cur_tag = "v" + (_cur_name.group(1) if _cur_name else "?")
check("gradle version fields are the single source of truth",
      bool(_cur_code) and bool(_cur_name)
      and "in-app updater compares" in app_gradle)
# --- RELEASE_NOTES_v1.0.0：收錄至「昨天 2026-11-19」嘅已發佈首版說明 ---------
_v100_text = release_notes_v100.read_text(encoding="utf-8") \
    if release_notes_v100.is_file() else ""
_v100_zh = _v100_text.split("## 中文（繁體）")[1].split("\n---\n")[0] \
    if "## 中文（繁體）" in _v100_text else ""
_v100_en = _v100_text.split("## English")[1].split("\n---\n")[0] \
    if "## English" in _v100_text else ""
check("RELEASE_NOTES_v1.0.0.md scope is THROUGH YESTERDAY 2026-11-19",
      (not release_notes_v100.is_file()) or all(s in _v100_text for s in (
          "收錄範圍：截至 2026-11-19（昨天）",
          "scope: through 2026-11-19 (yesterday)",
          "收錄至 2026-11-19", "Highlights through 2026-11-19",
          "HK ETA v1.0.0", "## English",
          "hketa-v1.0.0-release.apk")))
check("RELEASE_NOTES_v1.0.0.md excludes the 2026-11-20 v1.0.1 work",
      (not release_notes_v100.is_file()) or all(
          s not in _v100_zh and s not in _v100_en
          for s in ("GMB circular trip fix", "綠小巴循環線班次修正",
                    "城巴/九巴", "已發佈 Release backfill")))
check("RELEASE_NOTES_v1.0.0.md Chinese/English sections are symmetric",
      (not release_notes_v100.is_file()) or (
          len(re.findall(r"^### ", _v100_zh, re.M))
          == len(re.findall(r"^### ", _v100_en, re.M))
          and len(re.findall(r"^- ", _v100_zh, re.M))
          == len(re.findall(r"^- ", _v100_en, re.M))
          and len(re.findall(r"^- ", _v100_zh, re.M)) >= 20))
check("RELEASE_NOTES_v1.0.0.md is TEXT-ONLY and states the tag-source caveat",
      (not release_notes_v100.is_file()) or all(s in _v100_text for s in (
          "唔使改任何程式碼", "唔會改 APK", "no code change",
          "tag 當初嘅 source 係早期版本", "tag points at early source",
          "versionCode = 2", "RELEASE_NOTES_v1.0.1.md")))
# --- RELEASE_NOTES_v1.0.1：20 號至最新（2026-11-20 → latest）-----------------
_day2_latest = ROOT / "RELEASE_NOTES_v1.0.1_day2_2026-11-20.md"
_v101_text = release_notes_v101.read_text(encoding="utf-8") \
    if release_notes_v101.is_file() else ""
_v101_zh = _v101_text.split("## 中文（繁體）")[1].split("\n---\n")[0] \
    if "## 中文（繁體）" in _v101_text else ""
_v101_en = _v101_text.split("## English")[1].split("\n---\n")[0] \
    if "## English" in _v101_text else ""
check("RELEASE_NOTES_v1.0.1.md scope is 2026-11-20 THROUGH LATEST (bilingual)",
      (not release_notes_v101.is_file()) or all(s in _v101_text for s in (
          "收錄範圍：2026-11-20 至最新",
          "scope: 2026-11-20 through the latest",
          "2026-11-20 至最新嘅更新重點",
          "Highlights from 2026-11-20 through the latest",
          "版本分界", "Version boundary",
          "RELEASE_NOTES_${TAG}.md")))
check("RELEASE_NOTES_v1.0.1.md STARTS at 2026-11-20, no duplicated 11-19 highlights",
      (not release_notes_v101.is_file()) or (
          all(s in _v101_text for s in (
              "#### 📅 2026-11-20", "#### 📅 最新", "#### 📅 Latest",
              "APP1933", "GMB circular trip fix", "綠小巴循環線班次修正",
              "RELEASE_NOTES_v1.0.0.md"))
          and all(s not in _v101_text for s in (
              "昨天（2026-11-19）", "Yesterday (2026-11-19)",
              "2026-11-19 → 最新", "2026-11-19 → latest",
              "由 11-19 起至最新"))
          and len(re.findall(r"^#### 📅 ", _v101_text, re.M)) == 4
          and _v101_text.count("#### 📅 2026-11-20") == 2))
check("RELEASE_NOTES_v1.0.1.md stays symmetric after the 20-through-latest round",
      (not release_notes_v101.is_file()) or (
          len(re.findall(r"^### ", _v101_zh, re.M))
          == len(re.findall(r"^### ", _v101_en, re.M))
          and len(re.findall(r"^- ", _v101_zh, re.M))
          == len(re.findall(r"^- ", _v101_en, re.M))
          and len(re.findall(r"^- ", _v101_zh, re.M)) >= 20))
check("v1.0.1 notes state that editing text never touches APK / tag / CI",
      (not release_notes_v101.is_file()) or all(s in _v101_text for s in (
          "唔郁 APK／tag", "唔使重跑 CI",
          "never touches the APK / tag", "needs no CI re-run")))
check("Standalone day2 note also carries the latest round, still symmetric",
      (not _day2_latest.is_file()) or (
          all(s in _day2_latest.read_text(encoding="utf-8") for s in (
              "雙語 Release 說明分工", "Bilingual release-note split",
              "GMB circular trip fix", "2026-11-20"))
          and (lambda x, y: (
              len(re.findall(r"^### ", x, re.M)) == len(re.findall(r"^### ", y, re.M))
              and len(re.findall(r"^- ", x, re.M)) == len(re.findall(r"^- ", y, re.M))
              and len(re.findall(r"^- ", x, re.M)) >= 19))(
                  _day2_latest.read_text(encoding="utf-8").split("## 中文（繁體）")[1].split("---")[0],
                  _day2_latest.read_text(encoding="utf-8").split("## English")[1].split("---")[0])))
check("RELEASE_NOTES_v1.0.1.md exists and is BILINGUAL + symmetric",
      release_notes_v101.is_file()
      and all(s in release_notes_v101.read_text(encoding="utf-8")
              for s in ("HK ETA v1.0.1", "## 中文（繁體）", "## English",
                        "hketa-v1.0.1-release.apk", "versionCode = 2",
                        "GMB circular trip fix", "綠小巴循環線班次修正",
                        "FE:7B:35:E1", "RELEASE_NOTES_${TAG}.md")))
check("CURRENT gradle tag/version matches RELEASE_NOTES_v1.0.3.md (v1.0.2 kept as history)",
      bool(_cur_code) and bool(_cur_name)
      and _cur_tag == "v1.0.3"
      and _cur_code.group(1) == "4"
      and _cur_name.group(1) == "1.0.3"
      and ("`" + _cur_tag + "`") in release_notes_v103.read_text(encoding="utf-8")
      and "versionCode = 4" in release_notes_v103.read_text(encoding="utf-8")
      and "versionCode = 3" in release_notes_v102.read_text(encoding="utf-8"))
# RELEASE_NOTES_v1.0.1 YESTERDAY/TODAY — two-day split + standalone day notes.
_day1 = ROOT / "RELEASE_NOTES_v1.0.1_day1_2026-11-19.md"
_day2 = ROOT / "RELEASE_NOTES_v1.0.1_day2_2026-11-20.md"
# --- RELEASE_NOTES_v1.0.1：20 號至最新 + 兩份說明版本對應 ---------------------
_v101_text = release_notes_v101.read_text(encoding="utf-8")
check("RELEASE_NOTES_v1.0.1.md is the HISTORICAL 11-20-through-latest notes",
      all(s in _v101_text for s in (
          "收錄範圍：2026-11-20 至最新",
          "scope: 2026-11-20 through the latest",
          "RELEASE_NOTES_v1.0.0.md", "GMB circular trip fix",
          "綠小巴循環線班次修正", "hketa-v1.0.1-release.apk")))
check("RELEASE_NOTES_v1.0.1.md records that BOTH published notes are version-matched",
      all(s in _v101_text for s in (
          "版本分界", "Version boundary",
          "complementary", "never duplicated",
          "RELEASE_NOTES_${TAG}.md",
          "唔使 push code、唔使重跑 CI",
          "no code push, no CI re-run")))
check("RELEASE_NOTES_v1.0.1.md is grouped by 2026-11-20 / LATEST close-out",
      all(s in release_notes_v101.read_text(encoding="utf-8")
          for s in ("#### 📅 2026-11-20", "#### 📅 最新", "#### 📅 Latest",
                    "實際 commit／push 時間以 GitHub 為準",
                    "actual commit / push times are whatever GitHub shows"))
      and len(re.findall(r"^#### 📅 ", release_notes_v101.read_text(encoding="utf-8"), re.M)) == 4)
# 單日說明檔係「選用」純文字（workflow 只認 RELEASE_NOTES_${TAG}.md）。
# 唔硬性要求存在：避免「新 checker 配未全量 push 嘅舊 repo」又再 FAIL；
# 但只要檔存在，就必須係中英對稱、含正確日期同對應改動。
_day_ok = lambda f: (not f.is_file()) or (
    all(s in f.read_text(encoding="utf-8") for s in
        ("## 中文（繁體）", "## English", "hketa-v1.0.1-release.apk",
         "versionCode = 2", "FE:7B:35:E1", "RELEASE_NOTES_v1.0.1.md"))
    and (lambda x, y: (
        len(re.findall(r"^### ", x, re.M)) == len(re.findall(r"^### ", y, re.M))
        and len(re.findall(r"^- ", x, re.M)) == len(re.findall(r"^- ", y, re.M))
        and len(re.findall(r"^- ", x, re.M)) >= 19))(
            f.read_text(encoding="utf-8").split("## 中文（繁體）")[1].split("---")[0],
            f.read_text(encoding="utf-8").split("## English")[1].split("---")[0]))
check("Standalone single-day bilingual notes are OPTIONAL but must be symmetric when present",
      _day_ok(_day1) and _day_ok(_day2)
      and (not _day1.is_file() or "2026-11-19" in _day1.read_text(encoding="utf-8"))
      and (not _day2.is_file() or "2026-11-20" in _day2.read_text(encoding="utf-8"))
      and (not _day1.is_file() or "APP1933" in _day1.read_text(encoding="utf-8"))
      and (not _day2.is_file() or "GMB circular trip fix" in _day2.read_text(encoding="utf-8")))
check("RELEASE_NOTES_v1.0.1.md Chinese/English sections are symmetric",
      (lambda _t: (lambda zh, en: (
          len(re.findall(r"^### ", zh, re.M)) == len(re.findall(r"^### ", en, re.M))
          and len(re.findall(r"^- ", zh, re.M)) == len(re.findall(r"^- ", en, re.M))
          and len(re.findall(r"^- ", zh, re.M)) >= 20))(
              _t.split("## 中文（繁體）")[1].split("---")[0],
              _t.split("## English")[1].split("---")[0]))(
          release_notes_v101.read_text(encoding="utf-8")))
# --- RELEASE_NOTES_v1.0.2: 今日（2026-11-24）中英雙語說明 --------------------
_v102_text = release_notes_v102.read_text(encoding="utf-8") \
    if release_notes_v102.is_file() else ""
_v102_zh = _v102_text.split("## 中文（繁體）")[1].split("\n---\n")[0] \
    if "## 中文（繁體）" in _v102_text else ""
_v102_en = _v102_text.split("## English")[1].split("\n---\n")[0] \
    if "## English" in _v102_text else ""
check("RELEASE_NOTES_v1.0.2.md exists, is BILINGUAL and fully symmetric",
      release_notes_v102.is_file()
      and all(s in _v102_text for s in (
          "# HK ETA v1.0.2", "## 中文（繁體）", "## English",
          "hketa-v1.0.2-release.apk", "versionCode = 3",
          "FE:7B:35:E1", "RELEASE_NOTES_${TAG}.md"))
      and len(re.findall(r"^### ", _v102_zh, re.M)) == 5
      and len(re.findall(r"^### ", _v102_en, re.M)) == 5
      and len(re.findall(r"^#### 📅 ", _v102_zh, re.M)) == 2
      and len(re.findall(r"^#### 📅 ", _v102_en, re.M)) == 2
      and len(re.findall(r"^- ", _v102_zh, re.M)) >= 40
      and len(re.findall(r"^- ", _v102_zh, re.M))
          == len(re.findall(r"^- ", _v102_en, re.M)))
check("RELEASE_NOTES_v1.0.2.md scope is TODAY 2026-11-24 with a version boundary",
      all(s in _v102_text for s in (
          "收錄範圍：2026-11-24（今日）", "Scope: 2026-11-24 (today)",
          "版本分界", "Version boundary",
          "2026-11-24 上午", "2026-11-24 收尾",
          "2026-11-24 morning", "2026-11-24 wrap-up",
          "RELEASE_NOTES_v1.0.0.md", "RELEASE_NOTES_v1.0.1.md")))
check("RELEASE_NOTES_v1.0.2.md covers TODAY's search / CI / location / card / local-data work",
      all(s in _v102_text for s in (
          "截圖式分類搜尋", "Screenshot-style category search",
          "permissionHint", "hketa-release-apk",
          "自動定位加入更新定位位置", "UPDATES the device position",
          "同站更多路線", "MORE routes per stop",
          "SameRouteOperators.partition", "different bus operators shown separately",
          "取消搜尋「進階」", '"Advanced" removed',
          "SearchLocalData.kt", "Local route data restored",
          "399", "43 情境", "43 drift scenarios", "KOTLIN_APP_SCOPE",
          "CN_ANDROID17_GLASS", "NAV_NO_FAV_BUTTON",
          "LAUNCH_CATALOGUE_REFRESH", "HKE_MOBILITY_CATEGORIES")))
# 兩個 11-25 改動嘅現行描述已搬去 v1.0.3；v1.0.2 只保留版本分界指引，
# 唔再收錄嗰兩條 highlight（避免同一改動喺兩份 Release 說明重複）。
check("RELEASE_NOTES_v1.0.2.md hands the 11-25-and-later follow-up changes over to v1.0.3",
      all(s in _v102_text for s in (
          "RELEASE_NOTES_v1.0.3.md", "11-25",
          "11-25 起嘅後續改動", "`v1.0.3` 收錄 11-25 起至最新",
          "The 11-25-and-later app changes now live in",
          "`v1.0.3` covers the follow-up changes"))
      and all(s not in _v102_text for s in (
          "**🚇 還原香港出行易式分類**", "**🚀 開 App 就更新線路資料一次**",
          "**🚇 HKeMobility-style categories restored**",
          "**🚀 Refresh route data once on every app launch**",
          "MtrCatalog.LRT_ROUTES", "SearchCategoryRules.belongs()",
          "LaunchCatalogueRefresh.claimOnce()")))
check("RELEASE_NOTES_v1.0.2.md does NOT duplicate the 11-19 / 11-20 historical highlights",
      all(s not in _v102_text for s in (
          "昨天（2026-11-19）", "Yesterday (2026-11-19)",
          "2026-11-19 → 最新", "由 11-19 起至最新",
          "收錄範圍：2026-11-20 至最新", "scope: 2026-11-20 through the latest"))
      and "#### 📅 2026-11-24" in _v102_text
      and len(re.findall(r"^#### 📅 ", _v102_text, re.M)) == 4)
check("RELEASE_NOTES_v1.0.2.md install / asset / fingerprint / version facts are correct",
      all(s in _v102_text for s in (
          "hketa-v1.0.2-release.apk", "hketa-release-apk",
          "versionName = 1.0.2", "versionCode = 3",
          "FE:7B:35:E1…20:02:57", "SIGNING_FINGERPRINT.txt",
          "VERSION_INFO.txt", "applicationId = hk.eta.app", "minSdk 24",
          "target / compile SDK 34", "AGP 8.5.2", "Gradle 8.7")))
check("RELEASE_NOTES_v1.0.2.md is TEXT-ONLY and states the tag-source / no-APK caveat",
      all(s in _v102_text for s in (
          "純文字 Release 說明", "plain-text Release note",
          "改說明唔等於改 APK", "Editing notes never touches the APK",
          "唔使重跑 CI", "needs no CI re-run",
          "由 repo Actions 產出已簽名 APK", "repo Actions can build the signed release",
          "11-25 起嘅後續 App 改動已移往", "The 11-25-and-later app changes now live in")))
check("RELEASE_NOTES_v1.0.2.md lists honest limitations (ETA / best-effort / foreground-only)",
      all(s in _v102_text for s in (
          "實時 ETA 需要網絡", "Live ETA needs network",
          "best-effort", "前景 only 定位", "Foreground-only location",
          "唔捏造", "nothing is invented",
          "唔包含官方私有服務", "No official private services")))
check("v1.0.0 / v1.0.1 notes are preserved as HISTORY and point forward along the version chain",
      release_notes_v100.is_file() and release_notes_v101.is_file()
      and "歷史註記（2026-11-24）" in release_notes_v101.read_text(encoding="utf-8")
      and "Historical note (2026-11-24)" in release_notes_v101.read_text(encoding="utf-8")
      and "RELEASE_NOTES_v1.0.2.md" in release_notes_v101.read_text(encoding="utf-8"))
# --- RELEASE_NOTES_v1.0.3: 11-25 兩個改動嘅中英雙語說明 ---------------------
_v103_text = release_notes_v103.read_text(encoding="utf-8") \
    if release_notes_v103.is_file() else ""
_v103_zh = _v103_text.split("## 中文（繁體）")[1].split("\n---\n")[0] \
    if "## 中文（繁體）" in _v103_text else ""
_v103_en = _v103_text.split("## English")[1].split("\n---\n")[0] \
    if "## English" in _v103_text else ""
check("RELEASE_NOTES_v1.0.3.md exists, is BILINGUAL and fully symmetric",
      release_notes_v103.is_file()
      and all(s in _v103_text for s in (
          "# HK ETA v1.0.3", "## 中文（繁體）", "## English",
          "收錄範圍：2026-11-25 至最新（2026-11-26）", "Scope: 2026-11-25 through the latest (2026-11-26)",
          "RELEASE_NOTES_${TAG}.md", "中英雙語 Release 說明",
          "plain-text bilingual Release note"))
      and len(re.findall(r"^### ", _v103_zh, re.M)) == 5
      and len(re.findall(r"^### ", _v103_en, re.M)) == 5
      and len(re.findall(r"^#### 📅 ", _v103_zh, re.M)) == 2
      and len(re.findall(r"^#### 📅 ", _v103_en, re.M)) == 2
      and len(re.findall(r"^- ", _v103_zh, re.M)) >= 30
      and len(re.findall(r"^- ", _v103_zh, re.M))
          == len(re.findall(r"^- ", _v103_en, re.M)))
check("RELEASE_NOTES_v1.0.3.md covers ALL TEN v1.0.2 follow-up change groups",
      all(s in _v103_text for s in (
          "開 App 就更新線路資料一次", "還原香港出行易式分類",
          "refresh route data once on app launch",
          "HKeMobility-style search categories restored",
          "HkEtaApp.onCreate()", "LaunchCatalogueRefresh.claimOnce()",
          "CatalogueRefreshNotifier", "RouteDataPrefs.isDue()",
          "SearchCategoryRules.belongs()", "MtrCatalog.LRT_ROUTES",
          "全部官方目錄列", "EVERY official catalogue row",
          "take(6)",
          "港鐵類別顯示返重鐵", "MTR category shows heavy rail",
          "小巴路線歸小巴類別", "Minibus routes belong to the minibus category",
          # ③ 代碼優化
          "CODE_OPTIMISATION", "代碼優化", "Code optimisation",
          "PRELOAD_PER_COMPANY", "-12%", "memoise",
          # ④ 顯示所有路線
          "ALL_ROUTES", "顯示所有路線", "ALL routes shown",
          "Int.MAX_VALUE", "120ms", "53 列",
          # ⑤ 液態玻璃
          "NAV_LIQUID", "液態", "Liquid layer on the glass",
          "NavLiquid.springScale", "折射亮邊", "refraction rim", "1.032",
          # ⑥ 中國版 Android 17 進階材質
          "CN_ADVANCED_MATERIAL", "中國版 Android", "China-ROM Android OS 17",
          "NavGlassMaterial.resolve()", "materialProbe()",
          "三態 Spinner", "three-state spinner", "AOSP_NO_BLUR",
          # ⑦ 單一服務商只顯示一間
          "SINGLE_OPERATOR_DISPLAY", "單一服務商只顯示一間",
          "Single-operator routes show exactly one operator",
          "bothOperatorsPresent", "displayCompany", "markJoint()",
          "目錄交疊 ≠ 兩間服務商", "catalogue overlap ≠ two operators",
          # ⑧ 循環線頂欄兩顆動作
          "CIRCULAR_TWO_ACTIONS", "循環線頂欄兩顆動作",
          "Circular routes show exactly TWO header actions",
          "showDirectionAction(boolean circular)", "detailActionDirection",
          "三顆圓形動作", "three white circular actions",
          # ⑨ 三個圖標語義（日曆＝服務時間及班次）
          "DETAIL_THREE_ICONS", "三個圖標", "Three header icons",
          "服務時間及班次", "service hours & trips", "ic_detail_calendar",
          "showServiceHours(", "detail_calendar_no_timetable",
          "remark_tc", "通告", "notice",
          # 服務時間及班次由本地路線資料獲取
          "LOCAL_SERVICE_HOURS", "本地路線資料", "local route catalogue",
          "LocalServiceHours.lookup", "RouteDataStore.readText",
          "唔行即時 ETA", "no live ETA", "not invented", "唔捏造時間表",
          # ⑨ 數據來源升級：政府公開資料（運輸署 GTFS）
          "GOV_SERVICE_HOURS", "政府公開資料", "government open data",
          "pt-headway-tc", "DATA_LAST_UPDATED_DATE.csv", "frequencies.txt",
          "GovServiceHours.lookup", "refreshGovServiceHours",
          "數據修正截止日期", "data revision cut-off date",
          "服務日", "service days", "頭班／尾班", "first / last bus",
          # 政府班次資料加入「更新線路資料」
          "GOV_IN_ROUTE_UPDATE", "加入更新線路資料", "added to route catalogue update",
          "SOURCE_FILE", "EXTRA_SOURCES", "statuses.add(refreshGovServiceHours",
          "資料來源狀態", "source-status list", "sources_gov_files",
          "每兩星期更新", "bi-weekly",
          # 取消搜尋頁「我的書籤」
          "SEARCH_NO_BOOKMARK", "取消搜尋頁我的書籤",
          "bookmarks removed from search", "bookmarkRecycler",
          "收藏分頁", "favourites tab",
          # 取消所有頁面導航欄嘅「加入收藏」tab
          "NAV_NO_FAV_TAB", "取消導航欄收藏分頁",
          "Favourites tab removed from nav", "tabFav",
          "三分頁", "three tabs", "btnFavList", "chipFav",
          # 九巴／龍運分開顯示
          "KMB_LWB_SPLIT", "九巴路線只顯示九巴",
          "KMB routes show only KMB", "agency_id",
          "龍運循環線", "search_brand_unknown",
          # 單一公司嚴格分離 + 保留混合聯營標籤
          "STRICT_OPERATOR_SEPARATION", "城巴路線不顯示",
          "大嶼山巴士不顯示", "保留混合聯營標籤",
          "城巴/龍運", "keep the mixed joint label",
          "CTB routes never show", "NLB never shows",
          # 切換方向標題同步
          "DIRECTION_TITLE_SYNC", "切換方向", "activeDest",
          "switches the red header", "1 往 竹園邨",
          # API_AUDIT
          "API_AUDIT", "route/CTB/", "service_type", "stop-eta",
          "sha256", "188 情境", "425 static checks"))
      and "LOCAL_SERVICE_HOURS" in read(API / "LocalServiceHours.java")
      and len(re.findall(r"^\*\*[①-⑨] ", _v103_zh, re.M)) == 9
      and len(re.findall(r"^\*\*[①-⑨] ", _v103_en, re.M)) == 9)
check("RELEASE_NOTES_v1.0.3.md states the version boundary AND the completed bump honestly",
      all(s in _v103_text for s in (
          "RELEASE_NOTES_v1.0.0.md", "RELEASE_NOTES_v1.0.1.md",
          "RELEASE_NOTES_v1.0.2.md", "版本分界", "Version boundary",
          "版本已 bump", "Version bump complete",
          "versionName = 1.0.3", "versionCode = 4",
          "1.0.3` / `4", "update_pill.xml", "RELEASE_VERSION_GATE"))
      and "1.0.3` / `4" in _v103_text)
check("RELEASE_NOTES_v1.0.3.md install / asset / fingerprint facts are correct",
      all(s in _v103_text for s in (
          "hketa-v1.0.3-release.apk", "hketa-release-apk",
          "FE:7B:35:E1…20:02:57", "SIGNING_FINGERPRINT.txt",
          "VERSION_INFO.txt", "applicationId = hk.eta.app", "minSdk 24",
          "AGP 8.5.2", "Gradle 8.7", "Java 17", "Kotlin 1.9.24")))
check("RELEASE_NOTES_v1.0.3.md verification numbers match the repo and limitations are honest",
      all(s in _v103_text for s in (
          "ECJ 17", "68 class", "7 個 Java harness", "7 Java harnesses",
          "38 項", "38 published-Release", "188 情境", "188** inline-preflight", "185 情境",
          "6 份", "6** bilingual", "呢邊冇真跑 Kotlin 編譯",
          "Kotlin was not actually compiled here", "冷啟動", "cold start",
          "唔捏造", "nothing is invented")))
check("ALL 6 bilingual Release-note files are symmetric",
      (lambda pairs: all(x == y for x, y in pairs))(
          [(len(re.findall(r"^### ", f.read_text(encoding="utf-8")
                           .split("## 中文（繁體）")[1].split("\n---\n")[0], re.M)),
            len(re.findall(r"^### ", f.read_text(encoding="utf-8")
                           .split("## English")[1].split("\n---\n")[0], re.M)))
           for f in (release_notes_v100, release_notes_v101, release_notes_v102,
                     release_notes_v103)] +
          [(len(re.findall(r"^- ", f.read_text(encoding="utf-8")
                           .split("## 中文（繁體）")[1].split("\n---\n")[0], re.M)),
            len(re.findall(r"^- ", f.read_text(encoding="utf-8")
                           .split("## English")[1].split("\n---\n")[0], re.M)))
           for f in (release_notes_v100, release_notes_v101, release_notes_v102,
                     release_notes_v103)]))
check("v1.0.2 notes still point forward to v1.0.3 (no orphaned history)",
      "RELEASE_NOTES_v1.0.3.md" in _v102_text
      and "RELEASE_NOTES_v1.0.2.md" in _v103_text
      and "改說明唔等於改 APK" in _v103_text
      and "Editing notes never touches the APK" in _v103_text)

check("version bump did not break earlier features / data layer",
      "SEARCH_LOCAL_DATA" in main_kt
      and "SEARCH_NO_ADVANCED" in main_layout
      and "SameRouteOperators.partition(list, circularHint)" in read(UI / "App1933Format.kt")
      and "NearbyMoreRoutes.select(candidates)" in read(UI / "EtaViewModel.kt")
      and "fun signature(info: LocalDataInfo): String" in
          read(UI / "SearchLocalData.kt"))

# --- V103_VERSION_BUMP: 正式 bump 到 1.0.3 / 4 -------------------------------
check("V103_VERSION_BUMP: update_pill preview matches gradle 1.0.3 / 4",
      'android:text="v1.0.3"' in pill_xml
      and 'android:text="4"' in pill_xml
      and "versionCode = 4" in app_gradle
      and 'versionName = "1.0.3"' in app_gradle
      and "1.0.2" not in app_gradle)
check("V103_VERSION_BUMP: both drift gates require the bumped gradle needle",
      'versionName = "1.0.3"' in workflow
      and 'versionName = "1.0.3"' in read(ROOT / "scripts/preflight.py")
      and "versionCode = 4 / versionName" in workflow
      and "versionCode = 4 / versionName" in read(ROOT / "scripts/preflight.py"))

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

# --- CI: the "Android SDK" step failure (run 44055490312) --------------------
check("removed failing setup-android@v3 action",
      not re.search(r"uses:\s*android-actions/setup-android", workflow)
      and not re.search(r"^\s*-\s*name:\s*Android SDK\s*$", workflow, re.M))
check("runner pinned to Ubuntu 24.04",
      "runs-on: ubuntu-24.04" in workflow
      and "runs-on: ubuntu-latest" not in workflow)
check("Node 24-native action versions",
      "actions/checkout@v5" in workflow and "actions/setup-java@v5" in workflow)
check("uses runner-preinstalled Android SDK", all(
      s in workflow for s in ("Locate Android SDK (preinstalled on runner)",
                              "ANDROID_SDK_ROOT", "cmdline-tools/latest/bin/sdkmanager",
                              "SDKMANAGER=", '"$SDKMANAGER" --licenses',
                              "::error::搵唔到")))
check("SDK 34 still installed without the action",
      '"platforms;android-34" "build-tools;34.0.0" "platform-tools"' in workflow)
check("static check runs before toolchain setup",
      workflow.index("Check API field names against official samples")
      < workflow.index("JDK 17"))

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
# RELEASE_ONLY_ARTIFACT — 2026-11-24: Actions「Artifacts」區只准出正式版 APK。
# main push 都要出（唔再係「打 tag 先有」），debug APK 連組裝都唔做。
check("CI assembles ONLY the signed production APK",
      "gradle assembleRelease" in workflow
      and "gradle assembleDebug" not in workflow
      and "id: assemble_release" in workflow)
check("CI uploads ONLY the production APK artifact",
      "upload-artifact@v4" in workflow
      and "name: hketa-release-apk" in workflow
      and "hketa-release.apk" in workflow
      # 用 "name: " 前綴：drift 診斷訊息入面可以讲「唔再出 debug APK」，
      # 但唔准再有任何一個叫 hketa-debug-apk 嘅 Artifact step。
      and "name: hketa-debug-apk" not in workflow
      and "app-debug.apk" not in workflow
      and "build-debug.log" not in workflow
      and "gradle assembleDebug" not in workflow)
check("RELEASE_ONLY_ARTIFACT: release build runs on EVERY push, not only tags",
      "Prepare production APK artifact" in workflow
      and "cp \"$APK\" hketa-release.apk" in workflow
      and "refs/tags/*" in workflow
      and "- name: Assemble signed release APK\n        id: assemble_release\n        run: |" in workflow)
check("RELEASE_ONLY_ARTIFACT: upload step has NO tag-only guard",
      "- name: Upload signed release APK artifact\n        uses: actions/upload-artifact@v4" in workflow)

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
check("radius_labels has 7 entries incl. 50 m from the screenshot",
      len(arrays.get("radius_labels", [])) == 7
      and arrays["radius_labels"][0] == "50 m"
      and arrays["radius_labels"][-1] == "2000 m")
check("update_interval_labels has 4 entries",
      len(arrays.get("update_interval_labels", [])) == 4)
check("settings strings all defined", all(
      name in defined for name in ("settings", "settings_title", "route_data_section",
                                   "update_now", "auto_update", "sources_title",
                                   "clear_local", "settings_footer")))
main_kt = read(UI / "MainActivity.kt")
loc_kt = read(UI / "LocationHelper.kt")
check("relocate button REMOVED; auto-locate is gated by location permission",
      "btnNearby" not in main_layout and "btnNearby" not in main_kt
      and 'android:id="@+id/permissionHint"' in main_layout
      and "fun autoLocateIfAllowed" in main_kt
      and "fun requestLocationConsent" in main_kt
      and "override fun onResume" in main_kt
      and "LocationHelper.hasPermission" in main_kt
      and "RequestMultiplePermissions" in main_kt
      and "selectedRadius" in main_kt)
check("ViewModel refuses ANY nearby lookup before permission is granted",
      "fun awaitLocationPermission" in read(UI / "EtaViewModel.kt")
      and "private fun locationGranted" in read(UI / "EtaViewModel.kt")
      and read(UI / "EtaViewModel.kt").count("awaitLocationPermission()") >= 3)
check("LocationHelper exposes one shared permission gate",
      "fun hasPermission(context: Context): Boolean" in loc_kt
      and "private fun hasPermission" not in loc_kt
      and "IMPORTANT: callers MUST gate" in loc_kt)
# --- SEARCH_LOCAL_DATA: 恢復本地資料喺搜尋功能上 ----------------------------
local_kt = read(UI / "SearchLocalData.kt")
check("SEARCH_LOCAL_DATA: pure Kotlin decision layer, no Android / network / colour",
      "object SearchLocalData" in local_kt
      and "data class LocalDataInfo(" in local_kt
      and "import android." not in local_kt
      and "HttpJson" not in local_kt
      and "setTextColor" not in local_kt)
check("SEARCH_LOCAL_DATA: hasSnapshot / signature / shouldReload / sourceTag",
      all(s in local_kt for s in (
          "fun hasSnapshot(info: LocalDataInfo): Boolean",
          "info.updatedAt > 0L || info.filesPresent > 0",
          "fun signature(info: LocalDataInfo): String",
          "fun shouldReload(previous: String, current: String): Boolean",
          "previous.isNotEmpty() && previous != current",
          "fun sourceTag(info: LocalDataInfo, local: String, live: String): String",
          "fun offlineCapable(info: LocalDataInfo): Boolean"))
      and "捏造" in local_kt)
check("SEARCH_LOCAL_DATA: status row exists on the search page, fixed colours only",
      'android:id="@+id/tvLocalDataStatus"' in main_layout
      and 'android:text="@string/search_local_never"' in main_layout
      and '@color/search_field_bg' in main_layout
      and '@color/search_green' in main_layout
      and "SEARCH_LOCAL_DATA" in main_layout
      # 狀態列必須喺 pageSearch 內、routeInput 之後
      and main_layout.index('android:id="@+id/tvLocalDataStatus"')
          > main_layout.index('android:id="@+id/routeInput"')
      and main_layout.index('android:id="@+id/tvLocalDataStatus"')
          < main_layout.index('android:id="@+id/searchResultsView"'))
check("SEARCH_LOCAL_DATA: 5 new strings, no colour resource added",
      all(f'name="{n}"' in read(RES / "values/strings.xml") for n in (
          "search_local_never", "search_local_ok",
          "search_local_source_local", "search_local_source_live",
          "search_local_source_prefix"))
      and 'name="search_' in read(RES / "values/colors.xml")
      and 'name="search_' not in read(RES / "values-night/colors.xml"))
check("SEARCH_LOCAL_DATA: Activity renders status + source tag from RouteDataStore.meta()",
      all(s in main_kt for s in (
          "import hk.eta.app.api.RouteDataStore",
          "private lateinit var tvLocalDataStatus: TextView",
          "private var lastLocalSignature = """,
          "tvLocalDataStatus = findViewById(R.id.tvLocalDataStatus)",
          "private fun renderLocalDataStatus(): Boolean",
          "RouteDataStore.meta()",
          "RouteDataStore.readText(it) != null",
          "LocalDataInfo(",
          "SearchLocalData.signature(info)",
          "SearchLocalData.shouldReload(lastLocalSignature, sig)",
          "SearchLocalData.hasSnapshot(info)",
          "SearchLocalData.sourceTag(",
          "R.string.search_preload_hint"))
      and "tvLocalDataStatus.setOnClickListener" in main_kt
      and "SettingsActivity::class.java" in main_kt)
check("SEARCH_LOCAL_DATA: snapshot files covered (11 public dataset constants)",
      all(s in main_kt for s in (
          "RouteDataStore.KMB_ROUTES", "RouteDataStore.KMB_STOPS",
          "RouteDataStore.CTB_ROUTES", "RouteDataStore.CTB_STOPS",
          "RouteDataStore.NLB_ROUTES", "RouteDataStore.NLB_STOPS",
          "RouteDataStore.GMB_ROUTES", "RouteDataStore.GMB_STOPS",
          "RouteDataStore.MTR_LINES", "RouteDataStore.LRT_STOPS",
          "RouteDataStore.MTR_BUS_STOPS")))
# LAUNCH_CATALOGUE_REFRESH 之後 onCreate 嘅 observe callback 都會做一次指紋
# 比對；呢度用 finditer 搵「所有」比對點，要求至少一個位於 onResume..onPause，
# 唔再用 .index() 攞第一個（會被 onCreate 嗰個呃到）。
def _signature_reloads_in_resume():
    lo = main_kt.find("override fun onResume()")
    hi = main_kt.find("override fun onPause()")
    if lo < 0 or hi < 0 or hi <= lo:
        return False
    return any(lo < m.start() < hi
               for m in re.finditer(r"if \(renderLocalDataStatus\(\)\) \{", main_kt))

check("SEARCH_LOCAL_DATA: onResume re-preloads ONLY when the local signature changed",
      "if (renderLocalDataStatus()) {" in main_kt
      and 'vm.searchRoutes(routeInput.text?.toString() ?: "")' in main_kt
      and "refreshHistory()" in main_kt
      and _signature_reloads_in_resume())
check("SEARCH_LOCAL_DATA: data layer stays local-first, ETA still live-only",
      "public static JSONObject catalogueJson(String file, String... urls)" in
          read(API / "RouteDataStore.java")
      and "RouteDataStore.catalogueJson(" in read(API / "RouteSearch.java")
      and "Real-time ETA is NEVER cached" in read(API / "RouteDataStore.java")
      and "public static void invalidateDownstream()" in
          read(API / "RouteDataStore.java"))
check("SEARCH_LOCAL_DATA: no Advanced restored, previous rounds all intact",
      "SEARCH_NO_ADVANCED" in main_kt
      and "btnAdvanced" not in main_kt
      and "bottomTabs.visibility = View.VISIBLE" in main_kt
      and "LocationTracker.start(this, selectedRadius())" in main_kt
      and "NearbyMoreRoutes.select(candidates)" in read(UI / "EtaViewModel.kt")
      and "SameRouteOperators.partition(list, circularHint)" in
          read(UI / "App1933Format.kt"))

# --- KOTLIN_APP_SCOPE: 防止「建構參數當類別成員用」嘅 Unresolved reference ----
# 真實 CI 教訓：EtaViewModel(app: Application) 嘅 `app` 無 `val`，只係建構參數，
# 喺成員函數入面唔可見；loadNearbyRoutes() 直接用 app.getString(...) 令
# compileReleaseKotlin 報 "Unresolved reference: app"。舊符號對照把 `app`
# 放咗入白名單所以漏檢，呢度改做通用作用域掃描。
def kotlin_app_scope_violations():
    bad = []
    for f in sorted((ROOT / "app/src/main/java").rglob("*.kt")):
        lines = f.read_text(encoding="utf-8").splitlines()
        fun_starts = [i for i, l in enumerate(lines)
                      if re.match(r"^\s*(?:(?:private|public|internal|protected|"
                                 r"override|suspend|inline|open|final|abstract)\s+)*"
                                 r"fun\s+\w+", l)]
        first_fun = fun_starts[0] if fun_starts else len(lines)
        class_level = any(re.search(r"\b(?:val|var)\s+app\s*(?:=|:)", l)
                          for l in lines[:first_fun])
        bounds = [0] + fun_starts + [len(lines)]
        for bi in range(len(bounds) - 1):
            lo, hi = bounds[bi], bounds[bi + 1]
            block = lines[lo:hi]
            decl = [i for i, l in enumerate(block)
                    if re.search(r"\b(?:val|var)\s+app\s*(?:=|:)", l)]
            for i, l in enumerate(block):
                if l.lstrip().startswith(("//", "*", "/*")):
                    continue
                if not re.search(r"(?<![A-Za-z0-9_.])app\s*\.", l):
                    continue
                if not class_level and (not decl or min(decl) > i):
                    bad.append((f.name, lo + i + 1, l.strip()[:90]))
                    break
    return bad

check("KOTLIN_APP_SCOPE: every app. receiver is declared in scope (no ctor-param leak)",
      not kotlin_app_scope_violations(),
      str(kotlin_app_scope_violations()))
check("KOTLIN_APP_SCOPE: loadNearbyRoutes declares app before use, like refreshNearbyCards",
      (lambda vm: (
          "fun loadNearbyRoutes(radiusMeters: Int)" in vm
          # maybeAutoUpdateCatalogue 改用 CatalogueRefreshRunner 之後少咗一個
          # 局部 app 宣告；真正嘅防線係上面嘅通用作用域掃描，呢個 count 只做
          # 「同檔案既有寫法一致」嘅輔助斷言。
          and vm.count("val app = getApplication<Application>()") >= 4
          and (lambda block: "val app = getApplication<Application>()" in block
               and block.index("val app = getApplication<Application>()")
                   < block.index("app.getString(R.string.nearby_more_routes_tag)"))(
                   vm[vm.index("fun loadNearbyRoutes(radiusMeters: Int)"):
                      vm.index("fun queryDirect(")])))(
          read(UI / "EtaViewModel.kt")))

# --- LAUNCH_CATALOGUE_REFRESH: 開 App 就更新線路資料一次 ---------------------
launch_decision = ROOT / "app/src/main/java/hk/eta/app/ui/LaunchCatalogueRefresh.kt"
launch_notifier = ROOT / "app/src/main/java/hk/eta/app/ui/CatalogueRefreshNotifier.kt"
launch_runner = ROOT / "app/src/main/java/hk/eta/app/ui/CatalogueRefreshRunner.kt"
_launch_dec = read(launch_decision) if launch_decision.is_file() else ""
_launch_not = read(launch_notifier) if launch_notifier.is_file() else ""
_launch_run = read(launch_runner) if launch_runner.is_file() else ""
check("LAUNCH_CATALOGUE_REFRESH: pure decision layer has NO Android / network / colour",
      launch_decision.is_file()
      and all(s in _launch_dec for s in (
          "object LaunchCatalogueRefresh", "AtomicBoolean",
          "fun claimOnce(): Boolean", "fun shouldRefresh(",
          "fun shouldMarkChecked(", "fun resetForTest()"))
      and all(s not in _launch_dec for s in (
          "import android", "import androidx", "import hk.eta.app.api",
          "RouteDataStore", "getString(", "@color/")))
check("LAUNCH_CATALOGUE_REFRESH: claimOnce is process-level (one auto-refresh per cold start)",
      "private val claimed = AtomicBoolean(false)" in _launch_dec
      and "claimed.compareAndSet(false, true)" in _launch_dec
      and "CatalogueRefreshRunner.runOnce" in app_kt
      and "CatalogueRefreshRunner.runOnce" in viewmodel_kt
      and "RouteDataStore.init(this)" in app_kt
      and app_kt.index("RouteDataStore.init(this)")
          < app_kt.index("CatalogueRefreshRunner.runOnce"))
def _kt_code_only(s):
    """Strip KDoc / line comments so doc text never trips a code-level assertion."""
    return "\n".join(
        l.split("//")[0] for l in s.splitlines()
        if not l.lstrip().startswith(("*", "/*"))
    )

check("LAUNCH_CATALOGUE_REFRESH: startup refresh is NOT throttled by interval / isDue",
      all("RouteDataPrefs.isDue" not in _kt_code_only(x)
          and "intervalDays" not in _kt_code_only(x)
          for x in (viewmodel_kt, app_kt, _launch_run))
      and "CatalogueRefreshRunner.runOnce" in _kt_code_only(viewmodel_kt)
      and "每次開 App" in _launch_dec and "都更新一次" in _launch_dec)
check("LAUNCH_CATALOGUE_REFRESH: Wi-Fi-only preference and BUSY gate are respected",
      all(s in _launch_run for s in (
          "RouteDataPrefs.wifiOnly(app)", "RouteDataStore.onWifi()",
          "RouteDataStore.refreshBusy()", "RouteDataStore.refresh(false)"))
      and "(!wifiOnly || onWifi) && !alreadyBusy" in _launch_dec
      and "!busy && !skipped && okCount > 0" in _launch_dec)
check("LAUNCH_CATALOGUE_REFRESH: manual Settings update is NOT blocked by the gate",
      "RouteDataStore.refresh(false)" in settings_kt
      and "LaunchCatalogueRefresh.claimOnce" not in settings_kt
      and "CatalogueRefreshRunner" not in settings_kt
      and "btnUpdate" in settings_layout)
check("LAUNCH_CATALOGUE_REFRESH: notifier posts completion and search page reloads immediately",
      launch_notifier.is_file()
      and "MutableLiveData<Long>" in _launch_not
      and "_completed.postValue(atMillis)" in _launch_not
      and "CatalogueRefreshNotifier.notifyCompleted" in _launch_run
      and "RouteDataPrefs.markChecked(app)" in _launch_run
      and "CatalogueRefreshNotifier.completed.observe(this)" in main_kt
      and "if (renderLocalDataStatus()) {" in main_kt
      and "vm.searchRoutes(routeInput.text?.toString() ?: \"\")" in main_kt
      and "refreshHistory()" in main_kt)
check("LAUNCH_CATALOGUE_REFRESH: refresh internals / data layer / colours untouched",
      "public static Report refresh(boolean wifiOnly)" in store_java
      and "BUSY.compareAndSet(false, true)" in store_java
      and "invalidateDownstream()" in store_java
      and "writeAtomic" in store_java
      and "catalogueJson" in store_java
      and 'name="search_bg"' in read(RES / "values/colors.xml")
      and "name=\"search_" not in read(RES / "values-night/colors.xml"))
check("LAUNCH_CATALOGUE_REFRESH: previous rounds all intact",
      all(s in main_kt for s in ("SEARCH_LOCAL_DATA", "SEARCH_NO_ADVANCED"))
      and "KOTLIN_APP_SCOPE" in viewmodel_kt
      and "fun signature(info: LocalDataInfo): String" in
          read(UI / "SearchLocalData.kt")
      and "SameRouteOperators.partition(list, circularHint)" in
          read(UI / "App1933Format.kt")
      and "NearbyMoreRoutes.select(candidates)" in viewmodel_kt
      and "val app = getApplication<Application>()" in viewmodel_kt)

# --- SEARCH_NO_ADVANCED: 取消進階、導航欄移到主搜尋頁 ----------------------
noadv_settings = read(RES / "layout/activity_settings.xml")
check("SEARCH_NO_ADVANCED: advanced BUTTON removed from search page only",
      'android:id="@+id/btnAdvanced"' not in main_layout
      and "@string/search_advanced" not in main_layout
      and 'name="search_advanced"' not in read(RES / "values/strings.xml")
      # 設定頁「更新應用程式」嘅同名進階抽屜掣必須保留，唔好誤刪
      and 'android:id="@+id/btnAdvanced"' in noadv_settings
      and "SEARCH_NO_ADVANCED" in main_layout)
check("SEARCH_NO_ADVANCED: sequence mode / state / back button all gone from Activity",
      all(s not in main_kt for s in (
          "btnAdvanced", "inSequence", "showSequence",
          "btnBackToSearch", "R.id.btnAdvanced"))
      and "SEARCH_NO_ADVANCED" in main_kt
      and "showSearchHome()" in main_kt)
check("SEARCH_NO_ADVANCED: hidden 8-mode block stays GONE, no visible entry point",
      'android:id="@+id/stopSequenceView"' in main_layout
      and 'android:visibility="gone"' in main_layout
      and 'android:id="@+id/btnBackToSearch"' not in main_layout
      and "stopSequenceView.visibility = View.VISIBLE" not in main_kt
      and "stopSequenceView.visibility = View.GONE" in main_kt)
check("SEARCH_NO_ADVANCED: bottom nav bar is now VISIBLE on the MAIN search page",
      "bottomTabs.visibility = View.VISIBLE" in main_kt
      and main_kt.count("bottomTabs.visibility = View.VISIBLE") == 2
      and "bottomTabs.visibility = View.GONE" not in main_kt
      and (lambda s: "bottomTabs.visibility = View.VISIBLE" in s)(
          main_kt[main_kt.index("private fun hideChrome()"):
                  main_kt.index("private fun restoreChrome()")])
      and (lambda s: "showSearchHome()" in s and "if (index == 2)" in s)(
          main_kt[main_kt.index("private fun selectTab(index: Int)"):
                  main_kt.index("private fun selectTab(index: Int)") + 1000]))
check("SEARCH_NO_ADVANCED: screenshot header still clean (top chrome hidden)",
      "mainHeader.visibility = View.GONE" in main_kt
      and "statusText.visibility = View.GONE" in main_kt
      and "tvAutoRefreshStatus.visibility = View.GONE" in main_kt
      and "mainHeader.visibility = View.VISIBLE" in main_kt)
check("SEARCH_NO_ADVANCED: floating glass nav unchanged (geometry / 3 tabs / colours)",
      all(s in main_layout for s in (
          'android:id="@+id/bottomTabs"',
          'android:layout_gravity="bottom|center_horizontal"',
          'android:id="@+id/tabHome"',
          'android:id="@+id/tabSearch"', 'android:id="@+id/tabMore"'))
      # NAV_NO_FAV_TAB: 導航欄唔再有「★ 收藏」tab
      and 'android:id="@+id/tabFav"' not in main_layout
      and "NavGlass.apply(this, bottomTabs)" in main_kt
      and "NavGlass.selectTab(this, tabs[i], tabPages[i] == index)" in main_kt)
check("SEARCH_NO_ADVANCED: no colour change, 8-mode data bindings kept compatible",
      "companySpinner = findViewById(R.id.companySpinner)" in main_kt
      and "routeEdit = findViewById(R.id.routeEdit)" in main_kt
      and "findViewById<RecyclerView>(R.id.stopsRecycler)" in main_kt
      and 'name="search_' in read(RES / "values/colors.xml")
      and 'name="search_' not in read(RES / "values-night/colors.xml"))
check("SEARCH_NO_ADVANCED: location / more-routes / separate-operators all intact",
      "LocationTracker.start(this, selectedRadius())" in main_kt
      and "NearbyMoreRoutes.select(candidates)" in read(UI / "EtaViewModel.kt")
      and "SameRouteOperators.partition(list, circularHint)" in
          read(UI / "App1933Format.kt"))

# --- SEPARATE_OPERATORS: 同路線號碼不同巴士營運商分開顯示 ------------------
sep_kt = read(UI / "SameRouteOperators.kt")
sep_fmt_kt = read(UI / "App1933Format.kt")
sep_card_kt = read(UI / "CardAdapters.kt")
check("SEPARATE_OPERATORS: pure Kotlin decision layer, no Android / network / colour",
      "object SameRouteOperators" in sep_kt
      and "import android." not in sep_kt
      and "HttpJson" not in sep_kt
      and "setTextColor" not in sep_kt
      and "search_" not in sep_kt
      and "data class OperatorGroup(" in sep_kt)
check("SEPARATE_OPERATORS: ONLY an exact KMB+CTB non-circular pair merges",
      all(s in sep_kt for s in (
          "fun isJointPair(a: String, b: String): Boolean",
          "names.size == 2",
          "isJointPair(names[0], names[1])",
          "circular[names[0]] != true",
          "circular[names[1]] != true",
          "OperatorGroup(JointRoutes.JOINT, etas, false)",
          # SINGLE_OPERATOR_DISPLAY: 單公司 -> 1 張卡 / 1 個 label
          "SINGLE_OPERATOR_DISPLAY"))
      and sep_kt.count("return listOf(OperatorGroup(JointRoutes.JOINT") == 1
      and all(s in read(API / "JointRoutes.java") for s in (
          "public static boolean bothOperatorsPresent(Set<String> companies)",
          "public static String displayCompany(Set<String> companies, String fallback)",
          "if (!bothOperatorsPresent(companies))"))
      and all(s in read(API / "RouteSearch.java") for s in (
          "Map<String, Set<String>> companiesByKey",
          "JointRoutes.bothOperatorsPresent(rowCompanies)",
          "JointRoutes.displayCompany(rowCompanies, r.company)"))
      # 反向：唔可以再用「目錄交疊就單列升級 JOINT」嘅舊寫法
      and "String company = joint ? JointRoutes.JOINT : r.company;" not in read(API / "RouteSearch.java"))
check("SEPARATE_OPERATORS: every OTHER company combination is split into its OWN card",
      "val byCompany = LinkedHashMap<String, MutableList<EtaItem>>()" in sep_kt
      and "byCompany.getOrPut(e.company)" in sep_kt
      and "OperatorGroup(company, list, circular[company] == true)" in sep_kt
      and "fun partition(etas: List<EtaItem>, circularHint: Boolean)" in sep_kt)
check("SEPARATE_OPERATORS: circular flag is per-COMPANY, not bucket-wide",
      "val single = byCompany.size == 1" in sep_kt
      and "(circularHint && single) || isCircular(list)" in sep_kt
      and "etas.any { CircularRoutes.isCircularDest(it.destTc) }" in sep_kt)
check("SEPARATE_OPERATORS: cardsFrom delegates and keeps 3 trips of ITS OWN company",
      "for (group in SameRouteOperators.partition(list, circularHint))" in sep_fmt_kt
      and "val cardCompany = group.company" in sep_fmt_kt
      and "val joint = cardCompany == JointRoutes.JOINT" in sep_fmt_kt
      and "val showOperator = joint || jointRoutes.contains(first.route)" in sep_fmt_kt
      and "tripOf(it, cardCompany, showOperator)" in sep_fmt_kt
      and "val companies = sorted.map" not in sep_fmt_kt)
check("SEPARATE_OPERATORS: split card falls back to ITS OWN stopId, not the bucket KMB id",
      'val fallbackId = if (stopId.isEmpty() || !joint) {' in sep_fmt_kt
      and "first.stopId" in sep_fmt_kt
      and "resolveStopDisplay(cardCompany, displays, fallback)" in sep_fmt_kt)
check("SEPARATE_OPERATORS: same route number but different companies sort adjacent",
      ".thenBy { it.company }" in sep_fmt_kt
      and ".thenBy { it.company }" in read(UI / "NearbyMoreRoutes.kt")
      and "compareBy<RouteCard> { it.route }" in sep_fmt_kt)
check("SEPARATE_OPERATORS: card meta already shows the operator label, NO UI/colour change",
      "cardOperatorLabel(card.company, card.route)" in sep_card_kt
      and 'companyLabel(company: String, route: String = "")' in read(UI / "App1933Format.kt")
      and "public static String routeCompanyLabel(" in read(API / "JointRoutes.java")
      and 'return GovServiceHours.Brand.label(company, "")' in read(API / "JointRoutes.java")
      and 'return "嶼巴"' in read(API / "JointRoutes.java")
      and "btnNearby" not in main_layout)
check("SEPARATE_OPERATORS: search rows still only collapse KMB/CTB joint, others separate",
      "public final boolean joint" in read(API / "RouteSearch.java")
      and "markJoint(out)" in read(API / "RouteSearch.java"))
check("SEPARATE_OPERATORS: joint / circular / nearby-more-routes / data layer intact",
      "JOINT_KMB_CTB_MERGE" in read(UI / "EtaViewModel.kt")
      and "NearbyMoreRoutes.select(candidates)" in read(UI / "EtaViewModel.kt")
      and "LocationTracker.start(this, selectedRadius())" in main_kt
      and "public static List<EtaItem> augment" in read(API / "JointRoutes.java")
      and "public static List<StopOption> nearby(double lat" in read(API / "GeoStops.java"))

# --- KMB_LWB_SPLIT: 九巴路線唔顯示龍運，龍運路線唔顯示九巴 ------------------
_brand_java = read(API / "GovServiceHours.java")
_hke2 = read(UI / "SearchCategoryRules.kt")
_fmt2 = read(UI / "App1933Format.kt")
_card2 = read(UI / "CardAdapters.kt")
_sa2 = read(UI / "SearchAdapters.kt")
_ad2 = read(UI / "Adapters.kt")
check("KMB_LWB_SPLIT: brand decided ONLY by official GTFS agency_id (no route list)",
      "public static final class Brand" in _brand_java
      and 'a.equals("KMB") || a.equals("KMB+CTB")' in _brand_java
      and 'a.equals("LWB") || a.equals("LWB+CTB")' in _brand_java
      and "route_short_name" in _brand_java
      and "agency_id" in _brand_java
      and 'startsWith("A")' not in _brand_java
      and 'startsWith("E")' not in _brand_java
      and "S64" not in _brand_java)
check("KMB_LWB_SPLIT: KMB route says 九巴, LWB route says 龍運, UNKNOWN says nothing",
      'return "九巴"' in _brand_java
      and 'return "龍運"' in _brand_java
      and "return UNKNOWN;" in _brand_java
      and '"城巴/龍運"' in _brand_java
      and 'return "龍運循環線"' in _brand_java
      and 'return "九巴 / 龍運"' not in read(API / "JointRoutes.java"))
check("KMB_LWB_SPLIT: GTFS refresh invalidates the brand cache",
      "GovServiceHours.Brand.invalidate();" in read(API / "RouteDataStore.java"))
check("KMB_LWB_SPLIT: 九巴 / 龍運 chips are SEPARATE, unknown rows only in ALL",
      'brand == "KMB"' in _hke2
      and 'brand == "LWB"' in _hke2
      and "CHIP_KMB, CHIP_LWB" not in _hke2
      and 'brand: String = ""' in _hke2)
check("KMB_LWB_SPLIT: Activity passes the route brand into the rule layer",
      "GovServiceHours.Brand.brand(route)" in main_kt
      and "matchesCategory(it.company, it.route)" in main_kt
      and "GovServiceHours.Brand.hasData()" in main_kt
      and "search_brand_unknown" in main_kt)
check("KMB_LWB_SPLIT: every ROUTE-level label goes through Brand.label",
      "GovServiceHours.Brand.label(company, route)" in _fmt2
      and "cardOperatorLabel(card.company, card.route)" in _card2
      and "GovServiceHours.Brand.badge(company, route)" in _sa2
      and "App1933Format.companyLabel(item.company, item.route)" in _sa2
      and "operatorLabel(eta.company, eta.route)" in _ad2
      and "operatorLabel(stop.company, stop.route)" in _ad2
      and "九巴 / 龍運" not in _ad2)
check("KMB_LWB_SPLIT: trips, joint caption and circular badge follow the brand",
      "GovServiceHours.Brand.tripLabel" in _fmt2
      and "App1933Format.tripOperatorText(t, card.route)" in _card2
      and "GovServiceHours.Brand.label(company, route)" in detail_kt
      and "styleLabel(String company, String route)" in read(API / "CircularRoutes.java")
      and "displayBadge(String company, String route, String dest)" in
          read(API / "CircularRoutes.java"))
check("KMB_LWB_SPLIT: honest degradation strings + no colour / version change",
      all(f'<string name="{n}">' in read(RES / "values/strings.xml") for n in
          ("search_kmb_hint", "search_lwb_hint", "search_brand_unknown"))
      and "龍運路線唔會出現" in read(RES / "values/strings.xml")
      and "九巴路線唔會出現" in read(RES / "values/strings.xml")
      and 'name="search_' not in read(RES / "values-night/colors.xml")
      and "versionCode = 4" in read(ROOT / "app/build.gradle.kts"))

# --- JOINT_EVERY_TRIP_OPERATOR: 聯營「6 分鐘 11:27」後面逐班顯示營運商 --------
_fmt3 = read(UI / "App1933Format.kt")
_det3 = read(UI / "RouteDetailActivity.kt")
_card3 = read(UI / "CardAdapters.kt")
_stop3 = read(RES / "layout/item_detail_stop.xml")
check("JOINT_EVERY_TRIP_OPERATOR: joint routes detected from the ETA batch itself, zero IO",
      "companiesByRoute" in _fmt3
      and "jointRoutes" in _fmt3
      and "cs.contains(EtaRepository.KMB)" in _fmt3
      and "cs.contains(EtaRepository.CTB)" in _fmt3
      and "JointRoutes.isJointRoute(" not in _fmt3)
check("JOINT_EVERY_TRIP_OPERATOR: EVERY trip carries a non-empty operator label",
      "val showOperator = joint || jointRoutes.contains(first.route)" in _fmt3
      and "tripOf(it, cardCompany, showOperator)" in _fmt3
      and "tripOf(e: EtaItem, cardCompany: String, showOperator: Boolean)" in _fmt3
      and _fmt3.count("JointRoutes.operatorLabel(") >= 2
      and "GovServiceHours.Brand.tripLabel(e.company, e.route)" in _fmt3
      and ".ifEmpty { JointRoutes.operatorLabel(trip.operator) }" in _fmt3)
check("JOINT_EVERY_TRIP_OPERATOR: detail row + home chip both render it",
      'joinToString(" · ")' in _det3
      and "App1933Format.tripOperatorText(trip, route)" in _det3
      and "App1933Format.tripOperatorText(t, card.route)" in _card3
      and _stop3.count('android:layout_weight="1"') >= 3
      and _stop3.count('android:maxLines="1"') == 3
      and _stop3.count('android:ellipsize="end"') == 3)

# --- API_AUDIT: 真實官方 API 相容 / 效能 / 安全 --------------------------------
_rs = read(API / "RouteSearch.java")
_ds = read(API / "RouteDataStore.java")
_hj = read(API / "HttpJson.java")
_er = read(API / "EtaRepository.java")
_au = read(API / "AppUpdate.java")
_sa = read(UI / "SettingsActivity.kt")
_vm = read(UI / "EtaViewModel.kt")
_dt = read(UI / "RouteDetailActivity.kt")
check("API_AUDIT_A1: Citybus route catalogue uses the trailing-slash URL first",
      'CTB_BASE + "/route/CTB/"' in _rs
      and '"https://rt.data.gov.hk/v2/transport/citybus/route/CTB/"' in _ds
      and '"https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB/"' in _ds
      and _ds.index('"https://rt.data.gov.hk/v2/transport/citybus/route/CTB/"')
          < _ds.index('"https://rt.data.gov.hk/v1/transport/citybus-nwfb/route/CTB/"')
      and '"https://rt.data.gov.hk/v2/transport/citybus/route/CTB",' in _ds
      and 'CTB_BASE + "/route/CTB",' in _rs)
check("API_AUDIT_A2: NLB ' > ' ends split, raw routeName never used as dest",
      'splitEnds(name)' in _rs
      and '\\\\s*[>-]\\\\s+' in _rs
      and 'afterDash(HttpJson.str(o, "routeName_c")));' in _rs
      and '（未查方向）' in _rs
      and 'Collections.emptyList();' in _rs
      and '"往 " + code + " 總站"' not in _rs)
check("API_AUDIT_A3: CTB direction never invented, conflicting dests degrade",
      'HttpJson.str(o, "dir"), HttpJson.str(o, "bound"));' in _rs
      and 'HttpJson.str(o, "dir"), HttpJson.str(o, "bound"), "O"' not in _rs
      and 'b.isEmpty() || !bound.equalsIgnoreCase(b)' in _rs
      and 'dests.size() == 1' in _rs)
check("API_AUDIT_A4: all KMB service_types loaded and honestly labelled",
      'kmbServiceTypes(route, direction)' in _er
      and 'HttpJson.intOr(o, "service_type", 1)' in _er
      and 'out.size() >= 6' in _er
      and '.thenBy { it.serviceType }' in _vm
      and '特別班次 ${stop.serviceType}' in _dt)
check("API_AUDIT_A5: valid stop-eta tried first, HTTP 422 stops UA retry",
      _er.index('"/v1/transport/batch/stop-eta/"')
          < _er.index('"/v1/transport/batch/stopeta/"')
      and 'e.getMessage().startsWith("HTTP 422")' in _hj
      and 'readAll(stream,' in _hj)
check("API_AUDIT_B/C: dest cache, 8KB error cap, APK + pinned Gradle sha256",
      'invalidateDestCache()' in _rs
      and 'RouteSearch.invalidateDestCache();' in _ds
      and 'GMB_DEST_CACHE' in _rs
      and 'Collections.emptyList();' in _rs
      and 'Integer.MAX_VALUE : 8192' in _hj
      and 'downloadVerified(' in _au
      and 'AppUpdate.downloadVerified(' in _sa
      and 'sha256Hex(f)' in _au
      and '544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d' in workflow
      and workflow.count('sha256sum "$RUNNER_TEMP/gradle-8.7-bin.zip"') >= 2)

# --- DIRECTION_TITLE_SYNC: 撳 ⇅ 之後紅頂標題要跟住方向轉 --------------------
check("DIRECTION_TITLE_SYNC: ⇅ switches the red header destination too",
      "public static String destFor(String company, String route, String bound)" in
          read(API / "RouteSearch.java")
      and "RouteDataStore.readJson(file)" in read(API / "RouteSearch.java")
      and "fun destinationForBound(stops: List<StopOption>, bound: String)" in _fmt2
      and 'val activeDest: String = ""' in read(UI / "EtaViewModel.kt")
      and "App1933Format.destinationForBound(stops, next)" in read(UI / "EtaViewModel.kt")
      and "syncDirectionTitle(state)" in detail_kt
      and "tvTitle.text = buildTitle()" in detail_kt
      and "private fun buildCaption()" in detail_kt
      and "titleBound = requestedBound" in detail_kt)
check("DIRECTION_TITLE_SYNC: official dest first, terminal stop name fallback, never invented",
      "RouteSearch.destFor(" in read(UI / "EtaViewModel.kt")
      and "official.ifEmpty" in read(UI / "EtaViewModel.kt")
      and "maxByOrNull { it.seq }" in _fmt2
      and "catalogueJson 會喺缺檔時落網絡" in read(API / "RouteSearch.java")
      and 'return "";' in read(API / "RouteSearch.java")
      and "if (circular) return" in detail_kt
      and "next.isEmpty() || next == dest" in detail_kt)

# --- STRICT_OPERATOR_SEPARATION: 單一公司路線只顯示自己 ----------------------
check("STRICT_OPERATOR_SEPARATION: CTB / NLB / GMB rows never carry KMB or LWB brand",
      "CHIP_CTB -> company == CTB || company == JOINT" in _hke2
      and "CHIP_NLB -> company == NLB" in _hke2
      and "CAT_MINIBUS -> company == GMB" in _hke2
      and 'CHIP_KMB -> (company == KMB || company == JOINT) && brand == "KMB"' in _hke2
      and 'CHIP_LWB -> (company == KMB || company == JOINT) && brand == "LWB"' in _hke2
      and 'brand == "KMB" || company == CTB' not in _hke2)
check("STRICT_OPERATOR_SEPARATION: only real JOINT rows may show two company names",
      "public static String jointLabel(String route)" in _brand_java
      and 'return "城巴/龍運"' in _brand_java
      and 'return "聯營路線"' in _brand_java
      and "GovServiceHours.Brand.jointLabel(r.route)" in read(API / "RouteSearch.java")
      and "GovServiceHours.Brand.jointLabel(item.route)" in _card2
      and "GovServiceHours.Brand.jointLabel(item.route)" in _sa2
      and "JointRoutes.JOINT_LABEL" not in _card2
      and "JointRoutes.JOINT_LABEL" not in _sa2)
check("STRICT_OPERATOR_SEPARATION: unknown-brand KMB routes show NO company name",
      'return UNKNOWN;                     // 特別路線：唔顯示公司名' in _brand_java
      and 'return "J/C"' in _brand_java
      and "badge.isEmpty()" in _sa2
      and 'name="search_brand_unknown"' in read(RES / "values/strings.xml"))
check("STRICT_OPERATOR_SEPARATION: detail trip labels are route-aware too",
      "App1933Format.tripOperatorText(trip, route)" in detail_kt)

# --- MORE_ROUTES_PER_STOP: 自動定位顯示同站更多路線 -------------------------
more_kt = read(UI / "NearbyMoreRoutes.kt")
more_vm_kt = read(UI / "EtaViewModel.kt")
check("MORE_ROUTES_PER_STOP: pure Kotlin layer, no Android / network / colour",
      "object NearbyMoreRoutes" in more_kt
      and "import android." not in more_kt
      and "HttpJson" not in more_kt
      and "setTextColor" not in more_kt
      and "search_" not in more_kt)
check("ALL_ROUTES: unlimited candidate pool / entities / queries + batched ETA",
      all(s in more_kt for s in (
          "const val CANDIDATE_LIMIT = Int.MAX_VALUE",
          "const val ENTITY_LIMIT = Int.MAX_VALUE",
          "const val MAX_QUERIES = Int.MAX_VALUE",
          "const val ETA_BATCH_SIZE = 12",
          "const val BATCH_DELAY_MS = 120L",
          "fun batches(",
          "fun select(candidates: List<StopOption>): List<StopOption>",
          "fun entityKey(stop: StopOption): String",
          "fun entityCount(stops: List<StopOption>): Int",
          "fun sortCards(cards: List<RouteCard>): List<RouteCard>"))
      and more_kt.index("fun select(") < more_kt.index("fun sortCards("))
check("MORE_ROUTES_PER_STOP: entity key matches fetchCards bucket (name else stopId)",
      'stop.stopName.trim()' in more_kt
      and 'if (name.isEmpty()) stop.stopId else name' in more_kt
      and "stop.company +" not in more_kt)
check("ALL_ROUTES: keeps ALL entities and ALL aliases (no 6 / 12 caps)",
      "if (!entities.contains(key))" in more_kt
      and "if (entities.size >= ENTITY_LIMIT) break" in more_kt
      and "if (out.size >= MAX_QUERIES) break" in more_kt
      and "LinkedHashSet<String>()" in more_kt)
check("MORE_ROUTES_PER_STOP: cards are NOT truncated, sorted by rank+route+dest",
      "compareBy<RouteCard> { it.rank }" in more_kt
      and ".thenBy { it.route }" in more_kt
      and ".thenBy { it.dest }" in more_kt
      and ".take(" not in more_kt)
check("MORE_ROUTES_PER_STOP: ViewModel uses widened pool + select + sort in BOTH loads",
      all(s in more_vm_kt for s in (
          "repo.nearbyStops(fix.lat, fix.lng, radiusMeters,\n                        NearbyMoreRoutes.CANDIDATE_LIMIT)",
          "NearbyMoreRoutes.select(candidates)",
          "NearbyMoreRoutes.sortCards(fetchCards(stops))",
          "NearbyMoreRoutes.entityCount(stops)",
          "NearbyMoreRoutes.batches(indexed)",
          "BATCH_DELAY_MS",
          "R.string.nearby_more_routes_tag"))
      and more_vm_kt.count("NearbyMoreRoutes.sortCards") == 2
      and "cards.sortedBy { it.rank }" not in more_vm_kt
      and "stops.take(6)" not in more_vm_kt
      and "radiusMeters, 6)" not in more_vm_kt)
check("MORE_ROUTES_PER_STOP: status string exists, no new colour / View / button",
      "nearby_more_routes_tag" in read(RES / "values/strings.xml")
      and "btnNearby" not in main_layout and "btnNearby" not in main_kt
      and "📍 重新定位" not in read(RES / "values/strings.xml"))
check("MORE_ROUTES_PER_STOP: location permission + foreground tracker intact",
      "fun autoLocateIfAllowed" in main_kt
      and "LocationTracker.start(this, selectedRadius())" in main_kt
      and "fun awaitLocationPermission" in more_vm_kt
      and "private fun locationGranted" in more_vm_kt)
check("MORE_ROUTES_PER_STOP: 8-operator data layer untouched",
      "nearbyStops(double lat, double lng, int radiusMeters," in
          read(API / "EtaRepository.java")
      and "public static List<StopOption> nearby(double lat, double lng, int radiusMeters, int limit)" in
          read(API / "GeoStops.java"))

# --- LOCATION_AUTO_UPDATE: 自動定位之上加入「更新定位位置」 -------------------
tracker_kt = read(UI / "LocationTracker.kt")
check("LOCATION_AUTO_UPDATE: tracker file exists with platform LocationManager",
      "object LocationTracker" in tracker_kt
      and "LocationManager" in tracker_kt
      and "requestLocationUpdates(" in tracker_kt
      and "removeUpdates(" in tracker_kt
      and "com.google.android.gms" not in tracker_kt
      and "FusedLocationProviderClient" not in tracker_kt
      and "androidx.work" not in tracker_kt
      and "import com.google" not in tracker_kt)
check("LOCATION_AUTO_UPDATE: tracker is permission-gated before registering",
      "if (!LocationHelper.hasPermission(context))" in tracker_kt
      and tracker_kt.index("if (!LocationHelper.hasPermission(context))") <
          tracker_kt.index("requestLocationUpdates(")
      and '@SuppressLint("MissingPermission")' in tracker_kt)
check("LOCATION_AUTO_UPDATE: foreground-only, idempotent start / stop",
      "fun start(context: Context" in tracker_kt
      and "fun stop(context: Context" in tracker_kt
      and "stop(context)" in tracker_kt
      and "onMoved = null" in tracker_kt
      and "Looper.getMainLooper()" in tracker_kt)
check("LOCATION_AUTO_UPDATE: distance + time de-bounce before reload",
      "fun minDistanceForRadius(radiusMeters: Int): Float" in tracker_kt
      and "Location.distanceBetween(" in tracker_kt
      and "MIN_TIME_MS = 30_000L" in tracker_kt
      and "MIN_DISTANCE_M = 50f" in tracker_kt
      and "fun reset()" in tracker_kt)
check("LOCATION_AUTO_UPDATE: new listener keeps API 34 non-null callbacks + 3 legacy",
      all(s in tracker_kt for s in (
          "override fun onStatusChanged(provider: String?",
          "override fun onProviderEnabled(provider: String)",
          "override fun onProviderDisabled(provider: String)",
          "override fun onLocationChanged(location: Location)"))
      and "onProviderEnabled(provider: String?)" not in tracker_kt)
check("LOCATION_AUTO_UPDATE: MainActivity registers on resume / auto-locate, stops on pause+destroy",
      all(s in main_kt for s in (
          "LocationTracker.start(this, selectedRadius())",
          "LocationTracker.stop(this)",
          "override fun onDestroy()",
          "override fun onResume()",
          "override fun onPause()"))
      and main_kt.count("startLocationUpdates()") >= 4
      and main_kt.count("stopLocationUpdates()") >= 5
      and (lambda s: "stopLocationUpdates()" in s and "vm.onBackground()" in s)(
          main_kt[main_kt.index("override fun onPause()"):main_kt.index("override fun onDestroy()")])
      and (lambda s: "stopLocationUpdates()" in s)(
          main_kt[main_kt.index("override fun onDestroy()"):main_kt.index("override fun onConfigurationChanged")])
      and (lambda s: "startLocationUpdates()" in s and "vm.onForeground()" in s)(
          main_kt[main_kt.index("override fun onResume()"):main_kt.index("override fun onPause()")]))
check("LOCATION_AUTO_UPDATE: still NO relocate button; hint is still permission consent",
      "btnNearby" not in main_layout and "btnNearby" not in main_kt
      and "📍 重新定位" not in read(RES / "values/strings.xml")
      and "location_permission_hint" in read(RES / "values/strings.xml")
      and "permissionHint" in main_kt)
check("LOCATION_AUTO_UPDATE: status line shows tracking without new View or colour",
      "location_tracking_on" in read(RES / "values/strings.xml")
      and "R.string.location_tracking_on" in main_kt
      and "locationUpdatesRunning" in main_kt
      and "tvAutoRefreshStatus.text = EtaRefreshPrefs.statusLabel(this) + suffix" in main_kt
      and 'android:id="@+id/tvAutoRefreshStatus"' in main_layout)
check("LOCATION_AUTO_UPDATE: manifest declares both location permissions",
      "android.permission.ACCESS_FINE_LOCATION" in read(MANIFEST)
      and "android.permission.ACCESS_COARSE_LOCATION" in read(MANIFEST))
check("LOCATION_AUTO_UPDATE: no new colour / no search-page change",
      "search_" not in tracker_kt and "setTextColor" not in tracker_kt)

check("no stale relocate strings / hints remain",
      "📍 重新定位" not in read(RES / "values/strings.xml")
      and 'name="locate"' not in read(RES / "values/strings.xml")
      and "location_permission_hint" in read(RES / "values/strings.xml"))
vm_kt = read(UI / "EtaViewModel.kt")
check("ViewModel has loadNearby", "fun loadNearby" in vm_kt)
check("ViewModel maps all 8 spinner positions",
      all(f"{i} -> EtaRepository." in vm_kt for i in range(1, 8)))

# --- APP1933 shell: UI only, 8-operator data layer untouched ------------------
fmt_kt = read(UI / "App1933Format.kt")
sep_kt = read(UI / "SameRouteOperators.kt")
card_kt = read(UI / "CardAdapters.kt")
check("APP1933 shell has 3 bottom tabs + 4 pages (favourites via Settings)", all(
      f'android:id="@+id/{n}"' in main_layout for n in
      ("tabHome", "tabSearch", "tabMore",
       "pageHome", "pageFav", "pageSearch", "pageMore"))
      and 'android:id="@+id/tabFav"' not in main_layout
      and "NAV_NO_FAV_TAB" in main_layout)
check("APP1933 home card with 3 trip chips + big minutes", all(
      s in main_layout for s in
      ('android:id="@+id/homeRecycler"', 'android:id="@+id/tvHomeEmpty"'))
      and all(s in read(RES / "layout/item_route_card.xml") for s in
              ('android:id="@+id/cardRoot"', 'android:id="@+id/cardTrip1"',
               'android:id="@+id/cardTrip2"', 'android:id="@+id/cardTrip3"',
               'android:id="@+id/cardMinutes"', 'android:id="@+id/cardRoute"')))
check("Home has NO refresh button / radius spinner; pull-to-refresh only",
      'android:id="@+id/btnRefresh"' not in main_layout
      and 'android:id="@+id/radiusSpinner"' not in main_layout
      and 'btnRefresh' not in main_kt
      and 'radiusSpinner' not in main_kt
      and 'android:id="@+id/swipe"' in main_layout
      and 'HOME_CLEAN' in main_layout
      and 'swipe.setOnRefreshListener' in main_kt
      and 'name="refresh"' not in read(RES / "values/strings.xml"))
check("three-arrival formatting with official marks", all(
      s in fmt_kt for s in
      ("cardsFrom", "stopLabel", "take(3)", "剛到站", "沒有服務", "預定班次")))
check("route stop sequence + direction switch + stop filter", all(
      s in main_layout for s in
      ("btnDirection", "etFilter", "companySpinner", "routeEdit", "stopsRecycler"))
      and all(s in vm_kt for s in
              ("fun toggleDirection", "fun filterStops",
               "fun loadNearbyRoutes", "fun removeFavorite")))
check("card + favourite adapters wired", all(
      s in card_kt for s in
      ("class RouteCardAdapter", "class FavoriteAdapter", "cardTrip1",
       "setOnLongClickListener"))
      and all(s in main_layout for s in ("favRecycler", "tvFavEmpty"))
      and "RouteCardAdapter" in main_kt)
check("proprietary APP1933 features are NOT implemented", all(
      s in read(RES / "values/strings.xml") for s in
      ("AI 智能問路", "實時載客量", "九巴月票", "遊戲室", "街景"))
      and not any(x in main_layout for x in
                  ("btnMonthlyPass", "btnAiRoute", "btnGameRoom",
                   "btnPetBus", "btnBot1933")))
check("8-operator data layer is unchanged", all(
      s in read(API / "EtaRepository.java") for s in
      ("OPERATORS", "case KMB:", "case CTB:", "case NLB:", "case GMB:",
       "case MTR:", "case LRT:", "case MTRB:"))
      and "new Dataset(" in read(API / "RouteDataStore.java"))

# --- SEARCH_UI_2026-11-23：截圖式分類搜尋頁，顏色固定唔變 ---------------------
search_java = read(API / "RouteSearch.java")
result_row = read(RES / "layout/item_route_result.xml")
keypad_xml = read(RES / "layout/keypad.xml")
search_colors = read(RES / "values/colors.xml")
night_colors = read(RES / "values-night/colors.xml")
search_adapters = read(UI / "SearchAdapters.kt")
search_history_kt = read(UI / "SearchHistory.kt")
bookmark_row = read(RES / "layout/item_bookmark.xml")
search_strings = read(RES / "values/strings.xml")

check("SEARCH_UI: screenshot-style category search page replaces the keypad",
      all(s in main_layout for s in (
          'android:id="@+id/pageSearch"', 'android:id="@+id/searchCatScroll"',
          'android:id="@+id/searchChipScroll"', 'android:id="@+id/routeInput"',
          'android:id="@+id/searchHomeView"', 'android:id="@+id/searchResultsView"',
          'android:id="@+id/historyRecycler"', 'android:id="@+id/tvHistoryEmpty"',
          'android:id="@+id/tvCategoryNotice"'))
      # SEARCH_NO_BOOKMARK: 搜尋頁嘅「我的書籤」區塊已取消
      and 'android:id="@+id/bookmarkRecycler"' not in main_layout
      and 'android:id="@+id/tvBookmarkEmpty"' not in main_layout
      and 'android:text="@string/search_bookmark_title"' not in main_layout
      # SEARCH_NO_ADVANCED: 搜尋頁嘅「進階」掣已取消
      and 'android:id="@+id/btnAdvanced"' not in main_layout
      and '@layout/keypad' not in main_layout
      and 'android:id="@+id/searchBar"' not in main_layout
      and 'android:id="@+id/keypadRoot"' not in main_layout)
check("SEARCH_UI: 8 category tabs + 6 operator chips with exact screenshot labels",
      all(f'android:id="@+id/{n}"' in main_layout for n in (
          "catMtr", "catBus", "catGmb", "catFerry", "catTram", "catTaxi",
          "catNplb", "catResident", "chipAll", "chipCtb", "chipKmb",
          "chipLwb", "chipNlb", "chipDb"))
      and all(s in search_strings for s in (
          "港鐵", "巴士", "專線小巴", "渡輪", "電車", "的士", "非專線小巴",
          "居民巴士服務", "所有巴士類別", "城巴", "九巴", "龍運巴士",
          "新大嶼山巴士", "愉景灣/馬灣巴士", "路線號碼", "搜尋紀錄"))
      # SEARCH_NO_BOOKMARK: 書籤標題同空態文案已移除
      and "我的書籤" not in search_strings
      and "沒有資料記錄" not in search_strings
      and 'name="search_bookmark_title"' not in search_strings
      and 'name="search_bookmark_empty"' not in search_strings)
check("SEARCH_UI: activity wires the screenshot page, NOT the old keypad",
      all(s in main_kt for s in (
          "bindSearchCategories", "bindSearchChips", "selectCategory",
          "selectChip", "matchesCategory", "renderSearch", "refreshHistory",
          "openHistoryItem", "showSearchHome",
          "hideChrome", "restoreChrome", "SearchHistoryAdapter",
          "SearchHistoryPrefs", "toHistoryItem",
          "routeInput.addTextChangedListener", "vm.searchRoutes"))
      # SEARCH_NO_BOOKMARK: 書籤 adapter／綁定／刷新函式全部移除
      and "refreshBookmarks" not in main_kt
      and "BookmarkAdapter" not in main_kt
      and "R.id.bookmarkRecycler" not in main_kt
      and "tvBookmarkEmpty" not in main_kt
      and all(s not in main_kt for s in (
           "bindKeypad", "keypadRoot", "searchBar", "keyLetter",
           "inputBuffer", "updateLetterKeys", "R.id.searchHome)"))
      and "class SearchHistoryAdapter" in search_adapters
      and "class BookmarkAdapter" not in search_adapters
      and "object SearchHistoryPrefs" in search_history_kt)
# SEARCH_UI_KOTLIN_FIX: 上一版 cut 走舊 APP1933 欄位區塊時，連仍被 7 處引用嘅
# permissionHint 一齊移走 → Kotlin「Unresolved reference」令 assembleRelease 失敗。
# Python checker 只查 R.id / 文字 needle，唔查 Kotlin 符號，所以加返兩條硬閘。
check("SEARCH_UI_KOTLIN_FIX: permissionHint is DECLARED and BOUND (not just referenced)",
      "private lateinit var permissionHint: TextView" in main_kt
      and "permissionHint = findViewById(R.id.permissionHint)" in main_kt
      and 'android:id="@+id/permissionHint"' in main_layout
      and main_kt.count("permissionHint") >= 8)
# 收窄版符號檢查：只驗 MainActivity 入面「單純 identifier.<member>」嘅 receiver，
# 避開 holder.xxx / chips[i].xxx / import 行呢類複合或唔相干嘅寫法。
_no_imports = "\n".join(
    line for line in main_kt.splitlines() if not line.lstrip().startswith("import ")
)
_declared = set(re.findall(
    r"^\s*private\s+(?:lateinit\s+)?(?:val|var)\s+([A-Za-z_]\w*)",
    main_kt, re.M))
_locals = set(re.findall(r"\b(?:val|var)\s+([A-Za-z_]\w*)\s*=", main_kt))
_locals |= set(re.findall(r"\bfor\s*\(\s*([A-Za-z_]\w*)\s+in\b", main_kt))
_locals |= set(re.findall(r"\{\s*([A-Za-z_]\w*)\s*->", main_kt))
_locals |= set(re.findall(
    r"^\s*private fun \w+\(\s*([A-Za-z_]\w*)\s*:", main_kt, re.M))
_imports = set(re.findall(r"^import\s+[\w.]+\.([A-Za-z_]\w*)", main_kt, re.M))
_builtin = {
    "this", "it", "holder", "view", "parent", "s", "state", "item", "card",
    "stop", "result", "key", "intent", "swipe", "vm", "chips", "catTabs",
    "catUnderlines", "tabs", "pages", "rows", "favs", "history", "note",
    "cards", "badge", "selection", "query", "visibleStops", "dirLabel",
    "chips", "catTabs", "catUnderlines",
}
_receivers = set(re.findall(
    r"(?<![.\w])([A-Za-z_]\w*)\s*\.\s*"
    r"(?:visibility|text|hint|isRefreshing|submit|setOnClickListener|"
    r"setOnLongClickListener|setBackgroundResource|setTextColor|"
    r"addTextChangedListener|setColorSchemeResources|setOnRefreshListener|"
    r"setDropDownViewResource|setEnabled)\b",
    _no_imports, re.M))
_undeclared = sorted(_receivers - _declared - _locals - _imports - _builtin)
check("SEARCH_UI_KOTLIN_FIX: every bare <field>.<member> receiver is declared",
      not _undeclared, f"undeclared={_undeclared}")

check("SEARCH_UI: history rows show operator badge / clock / removable ✕",
      all(f'android:id="@+id/{n}"' in result_row for n in (
          "resultRoot", "resultClock", "resultBadge", "resultRoute",
          "resultDest", "resultNote", "resultRemove"))
      and '@color/search_bg' in result_row
      and all(Path(RES / f"drawable/{n}.xml").is_file() for n in (
          "bg_search_badge", "ic_clock", "ic_close", "ic_search")))
check("SEARCH_NO_BOOKMARK: search page no longer shows 我的書籤; favourites stay",
      # 停用資產保留（preflight 141 不變），但再無任何引用／文案／綁定
      Path(RES / "layout/item_bookmark.xml").is_file()
      and "R.layout.item_bookmark" not in main_kt
      and "R.layout.item_bookmark" not in search_adapters
      and '@+id/bookmarkRoot' not in main_layout
      and 'SEARCH_NO_BOOKMARK' in main_kt
      and 'SEARCH_NO_BOOKMARK' in main_layout
      # 收藏功能本身冇取消：設定頁 btnFavList + 收藏頁 + 詳情頁 chipFav 照舊
      and "favoriteAdapter" in main_kt
      and "vm.favorites()" in main_kt
      and "R.id.favRecycler" in main_kt
      and "R.id.btnFavList" in main_kt
      and "selectTab(1)" in main_kt
      and "class FavoriteAdapter" in read(UI / "CardAdapters.kt")
      and "vm.addCurrentFavorite()" in read(UI / "RouteDetailActivity.kt")
      and "R.id.chipFav" in detail_kt
      # 搜尋紀錄、固定色盤、其餘功能一個都冇郁
      and "class SearchHistoryAdapter" in search_adapters
      and "R.id.historyRecycler" in main_kt
      and '@color/search_bg' in bookmark_row
      and '@color/search_green' in bookmark_row
      and "搜尋紀錄" in search_strings)
check("SEARCH_UI: colours are LOCKED — no value changed, no night override",
      all(f'<color name="{n}">{v}</color>' in search_colors for n, v in {
          "search_bg": "#1D1D1F",
          "search_field_bg": "#2C2C2E",
          "search_green": "#3DDC84",
          "search_green_soft": "#DDF5E7",
          "search_green_text": "#1B6B3A",
          "search_text": "#F2F2F7",
          "search_muted": "#AEAEB2",
          "search_hint": "#8E8E93",
          "search_divider": "#48484A",
          "search_chip_border": "#D9DEE4",
      }.items())
      and 'name="search_' not in night_colors
      and "SEARCH_UI_2026-11-23" in search_colors
      and "刻意唔喺 values-night 覆寫" in search_colors)
check("SEARCH_UI: pageSearch uses ONLY the fixed search_* colours",
      (lambda block: (
          "@color/" in block
          and all(c.startswith("search_") for c in
                  re.findall(r'@color/([A-Za-z0-9_]+)', block))
          and "search_bg" in block))(
          main_layout[main_layout.index('android:id="@+id/pageSearch"'):
                      main_layout.index('android:id="@+id/stopSequenceView"')]))
check("SEARCH_UI: activity only APPLIES existing colours, never defines new ones",
      all(s in main_kt for s in (
          "R.color.search_green", "R.color.search_text",
          "R.color.search_green_text", "R.drawable.bg_chip_selected",
          "R.drawable.bg_chip"))
      and "R.color.text" not in main_kt
      and "setTextColor(0x" not in main_kt
      and "setBackgroundColor(0x" not in main_kt
      and "Color.parseColor" not in main_kt)
check("SEARCH_UI: drawables keep their fixed fills / strokes",
      all(s in read(RES / f"drawable/{n}.xml") for n, s in (
          ("bg_search_field", "@color/search_field_bg"),
          ("bg_search_field", "@color/search_green"),
          ("bg_search_cat_underline", "@color/search_green"),
          ("bg_chip", "@color/search_chip_border"),
          ("bg_chip_selected", "@color/search_green_soft"),
          ("bg_search_badge", "#3A3A3C"),
          ("ic_clock", "#3DDC84"),
          ("ic_close", "#8E8E93"),
          ("ic_search", "#AEB4BC"))))
check("legacy keypad.xml is kept as a file but NOT included by the search page",
      all(f'android:tag="{k}"' in keypad_xml for k in
          ("1", "DEL", "ENTER", "L0", "PREV"))
      and '@layout/keypad' not in main_layout)
check("route search uses official catalogues for all 7 operator modes",
      all(s in search_java for s in (
          "RouteDataStore.KMB_ROUTES", "RouteDataStore.CTB_ROUTES",
          "RouteDataStore.NLB_ROUTES", "RouteDataStore.GMB_ROUTES",
          "MtrCatalog.isLineCode", "EtaRepository.MTRB",
          "資料由DATA.GOV.HK提供", "public static List<Result> search"))
      and "HttpJson.getObject" in search_java)
check("route search still runs through the unchanged ViewModel",
      all(s in vm_kt for s in ("fun searchRoutes", "RouteSearch.search",
                               "onlyBound", "searchJob"))
      and "class RouteResultAdapter" in card_kt)

# --- JOINT_KMB_CTB: 城巴/九巴 聯營路線 + 每班次公司 --------------------------
joint_java = read(API / "JointRoutes.java")
detail_layout = read(RES / "layout/activity_route_detail.xml")
check("JOINT_KMB_CTB: joint routes are DETECTED FROM BOTH official catalogues",
      "JOINT_KMB_CTB" in joint_java
      and "RouteDataStore.KMB_ROUTES" in joint_java
      and "RouteDataStore.CTB_ROUTES" in joint_java
      and "public static boolean bothContain" in joint_java
      and "public static boolean isJointRoute" in joint_java
      and 'public static final String JOINT_LABEL = "城巴/九巴"' in joint_java
      and 'public static final String TRIP_PREFIX = "該班次："' in joint_java)
check("JOINT_KMB_CTB: JOINT is a UI marker only, NOT a new spinner operator",
      'public static final String JOINT = "JOINT"' in joint_java
      and "JointRoutes.JOINT" not in read(API / "EtaRepository.java")
      and 'OPERATORS = {' in read(API / "EtaRepository.java")
      and "JOINT" not in read(RES / "values/strings.xml").split(
          '<string-array name="company_labels">')[1].split('</string-array>')[0])
check("JOINT_KMB_CTB: search + preload collapse KMB/CTB rows into one label",
      all(s in search_java for s in (
          "public final boolean joint",
          "private static List<Result> markJoint",
          "JointRoutes.isJointRoute(r.route)",
          "GovServiceHours.Brand.jointNote(r.route)",
          "return -1;",
          # SINGLE_OPERATOR_DISPLAY: 要實際兩間先合併
          "companiesByKey",
          "boolean eligible",
          "JointRoutes.bothOperatorsPresent(rowCompanies)"))
      and search_java.count("List<Result> merged = markJoint(out);") == 2
      and "SINGLE_OPERATOR_DISPLAY" in joint_java
      and "SINGLE_OPERATOR_DISPLAY" in sep_kt)
check("JOINT_KMB_CTB: ETA layer best-effort merges the partner operator",
      "JointRoutes.augment(primary, stop, this)" in read(API / "EtaRepository.java")
      and "public static List<EtaItem> augment" in joint_java
      and "repo.kmbEta(route" in joint_java
      and "repo.ctbEta(route" in joint_java)
check("JOINT_KMB_CTB: ONE card labelled 城巴/九巴, three per-trip labels",
      all(s in fmt_kt for s in (
          "val operatorText: String", "fun companyLabel",
          "fun tripOperatorText", "JointRoutes.JOINT"))
      # SEPARATE_OPERATORS: the exact KMB+CTB pair test moved to a pure layer.
      and all(s in sep_kt for s in (
          "isJointPair(a: String, b: String): Boolean",
          "a == EtaRepository.KMB", "b == EtaRepository.CTB",
          "names.size == 2", "JointRoutes.JOINT"))
      and "SameRouteOperators.partition(list, circularHint)" in fmt_kt
      # grouping key must NOT include company any more
      and 'e.company + "|" + e.route' not in fmt_kt)
check("JOINT_KMB_CTB: card meta / chips / search note / detail label all wired",
      "App1933Format.companyLabel" in card_kt
      and "App1933Format.tripOperatorText(t, card.route)" in card_kt
      and "item.joint" in card_kt
      and "GovServiceHours.Brand.jointLabel(item.route)" in card_kt
      and "JointRoutes.isJointCompany(company)" in detail_kt
      and "App1933Format.tripOperatorText(trip, route)" in detail_kt
      and 'android:id="@+id/tvNoMapLabel"' in detail_layout)
check("JOINT_KMB_CTB: detail page queries KMB + CTB only (never all 8 modes)",
      "JointRoutes.isJointCompany(company)" in vm_kt
      and "listOf(EtaRepository.KMB, EtaRepository.CTB)" in vm_kt)
check("JOINT_KMB_CTB_MERGE: nearby KMB/CTB stops merge into ONE home card",
      "JOINT_KMB_CTB_MERGE" in vm_kt
      and "buckets.getOrPut(key)" in vm_kt
      and "indices.flatMap { pairs[it].second }" in vm_kt
      and "val groupStop = if (stopId.isNotEmpty()) stopId else e.stopId" in fmt_kt
      and 'e.route + "|" + groupStop + "|" + e.destTc' in fmt_kt)
check("JOINT_KMB_CTB_MERGE: unmatched stop names keep the old per-stop behaviour",
      "ifEmpty {" in vm_kt
      and "for ((bucketKey, indices) in buckets)" in vm_kt
      and "circularFlags[key]" in vm_kt
      and "App1933Format.cardsFrom(" in vm_kt)
circ_java = read(API / "CircularRoutes.java")
check("CIRCULAR_ROUTES: detection is DATA-DRIVEN, no hard-coded route list",
      Path(API / "CircularRoutes.java").is_file()
      and "isCircularDest(" in circ_java
      and "isCircularSequence(" in circ_java
      and "anyCircularNote(" in circ_java
      and "循環線" in circ_java
      and "CIRCULAR" in circ_java
      and "supports(" in circ_java
      and "RouteDataStore.KMB_STOPS" in circ_java)
check("CIRCULAR_DISPLAY: KMB shown the App1933 way, CTB the Citybus way",
      "displayTitle(" in circ_java
      and "styleLabel(" in circ_java
      and "displayBadge(" in circ_java
      and "九巴循環線" in circ_java
      and "城巴循環線" in circ_java
      and 'route + " 往 " + d' in circ_java
      and 'route + " " + d' in circ_java
      and "CircularRoutes.displayTitle(company, route, dest)" in detail_kt
      and "CircularRoutes.styleLabel(company, route)" in detail_kt
      and "card.style" in card_kt)
check("CIRCULAR_ONLY: only circular lines keep EACH operator's OWN stop name",
      "stopNameFor(" in circ_java
      and "stopIdFor(" in circ_java
      and "CircularRoutes.isCircularSequence(raw)" in vm_kt
      and "!circular && JointRoutes.isJointCompany(company)" in vm_kt
      and "fun isCircular(etas: List<EtaItem>): Boolean" in sep_kt
      and "circularHint && single" in sep_kt
      and "group.circular" in fmt_kt
      and "CircularRoutes.styleLabel(cardCompany, first.route)" in fmt_kt
      and "circularHint: Boolean = false" in fmt_kt)
check("CIRCULAR_ONLY: circular rows are NOT collapsed into a joint row",
      "markCircular(" in search_java
      and "if (r.circular)" in search_java
      and "List<Result> marked = markCircular(out)" in search_java
      and 'r.circular' in search_java)
check("CIRCULAR_NAME_CHECK: stop names are audited, never invented",
      "public static final class Audit" in circ_java
      and "public static Audit audit(" in circ_java
      and "officialNames(" in circ_java
      and "FOREIGN_SUSPECT" in circ_java
      and "MISMATCH" in circ_java
      and "未載入官方站名對照表" in circ_java
      and "CircularRoutes.audit(" in vm_kt
      and "stopAudit = audit" in vm_kt
      and "state.stopAudit" in detail_kt)
check("CIRCULAR_ROUTES: wording resources + detail caption are wired",
      all(f'<string name="{n}">' in read(RES / "values/strings.xml")
          for n in ("circular_kmb_style", "circular_ctb_style",
                    "circular_name_check", "circular_name_hint"))
      and "CircularRoutes.displayBadge(company, route, dest)" in detail_kt
      and "renderFooter()" in detail_kt)
# KOTLIN_TOPLEVEL — App1933Format.kt 有 3 個 TOP-LEVEL data class
# (Trip / StopDisplay / RouteCard)，只有 object App1933Format 嘅成員先可以
# 用 "App1933Format.xxx" 前綴。呢條 check 正正防止 CI 真實 kotlinc 先爆
# "Unresolved reference: StopDisplay"。
all_kt = "\n".join(read(p) for p in sorted(UI.glob("*.kt")))
# 通用符號解析：任何 App1933Format.X 都要係 object 入面嘅 fun/val，
# 唔可以係頂層 data class（真實 kotlinc 會報 Unresolved reference）。
_obj = fmt_kt.split("object App1933Format {")[1]
_members = set(re.findall(r"\bfun\s+(\w+)\s*\(", _obj)) | \
           set(re.findall(r"\bval\s+(\w+)\s*=", _obj))
_used = set()
for _p in sorted(UI.glob("*.kt")):
    _used |= set(re.findall(r"App1933Format\.(\w+)", read(_p)))
check("KOTLIN_SYMBOLS: every App1933Format.* reference resolves to a member",
      bool(_members) and bool(_used) and not (_used - _members),
      "unresolved: " + ", ".join(sorted(_used - _members))
      if (_used - _members) else "")
check("KOTLIN_TOPLEVEL: no App1933Format.<top-level data class> misuse",
      "App1933Format.StopDisplay" not in all_kt
      and "App1933Format.Trip" not in all_kt
      and "App1933Format.RouteCard" not in all_kt
      and "data class StopDisplay(val name: String, val id: String)" in fmt_kt
      and "data class Trip(" in fmt_kt
      and "data class RouteCard(" in fmt_kt
      and "object App1933Format {" in fmt_kt
      and "LinkedHashMap<String, StopDisplay>()" in vm_kt
      and "StopDisplay(" in vm_kt)
check("AAPT_FMT: percent literals in non-format strings are marked",
      '<string name="nav_glass_hint" formatted="false">' in read(RES / "values/strings.xml")
      and "%1$" in read(RES / "values/strings.xml"))
# --- 已發佈版本補正式版 APK（PUBLISHED_RELEASE_BACKFILL）---------------------
published = read(ROOT / "scripts/published_release.py")
check("PUBLISHED_RELEASE_BACKFILL: workflow_dispatch inputs + dedicated job",
      "PUBLISHED_RELEASE_BACKFILL" in workflow
      and "backfill-published-release:" in workflow
      and "if: github.event_name == 'workflow_dispatch'" in workflow
      and "inputs:" in workflow
      and "include_prerelease:" in workflow
      and "已發佈版本 tag" in workflow
      and "scripts/published_release.py" in workflow)
check("PUBLISHED_RELEASE_BACKFILL: builds from the TAG's own source, not main",
      "git fetch origin tag" in workflow
      and "git worktree add --detach" in workflow
      and "cp -r scripts/." in workflow
      and "Materialise the PUBLISHED tag source" in workflow
      and "用 tag「{tag}」本身嘅 source 構建" in published)
check("PUBLISHED_RELEASE_BACKFILL: reuses the SAME version + signing gates",
      "Verify release version matches the PUBLISHED tag (backfill)" in workflow
      and "Verify APK signing certificate (backfill)" in workflow
      and "scripts/verify_release_version.py" in workflow
      and "dump badging" in workflow
      and "SIGNING_FINGERPRINT.txt" in workflow
      and "VERSION_INFO.txt" in workflow)
check("PUBLISHED_RELEASE_BACKFILL: only ONE production .apk, body/tag preserved",
      "gh release upload" in workflow
      and "--clobber" in workflow
      and "scripts/published_release.py prune" in workflow
      and "scripts/published_release.py final" in workflow
      and "Release 內文／tag／published_at 不變" in workflow
      and "def final_audit" in published
      and "def prune_ids" in published
      and "PRODUCTION_MARKER" in published
      and "is_production_apk" in published)
check("PUBLISHED_RELEASE_BACKFILL: never creates a Release or moves a tag",
      "softprops/action-gh-release@v2" in workflow
      and workflow.count("softprops/action-gh-release@v2") == 1
      and "def select_target" in published
      and "draft" in published
      and "唔會新建 Release" in published)
check("PUBLISHED_RELEASE_BACKFILL: pure functions are offline-testable",
      all(s in published for s in (
          "def parse_releases", "def is_published", "def is_prerelease",
          "def tag_of", "def select_target", "def apk_assets",
          "def prune_ids", "def final_audit", "def version_source_hint"))
      and "argparse" in published and "json" in published)
# --- 綠小巴循環線班次去重／首末站分離（GMB_CIRCULAR_ETA）---------------------
gmb_circ = read(API / "GmbCircularEta.java")
models_java = read(API / "EtaModels.java")
check("GMB_CIRCULAR_ETA: pure, data-driven selector exists",
      Path(API / "GmbCircularEta.java").is_file()
      and "GMB_CIRCULAR_ETA" in gmb_circ
      and "public static final class Raw" in gmb_circ
      and "public static String tripKey(" in gmb_circ
      and "public static List<Raw> distinct(" in gmb_circ
      and "public static List<Raw> select(" in gmb_circ
      and "public static String audit(" in gmb_circ
      and "import java.util." in gmb_circ
      and "import android." not in gmb_circ
      and "import org.json." not in gmb_circ
      and "2000410" not in gmb_circ)   # no hard-coded route ids
check("GMB_CIRCULAR_ETA: circularity comes from OFFICIAL stop_seq, not route numbers",
      all(s in gmb_circ for s in (
          "public static List<Integer> stopSeqs(",
          "public static boolean isCircularPayload(",
          "public static List<Raw> forStop(",
          "stopSeqs(all).size() >= 2",
          "r.stopSeq == wantedSeq",
          "isCircularPayload(all)"))
      and "route.equals(" not in gmb_circ
      and "route_code" not in gmb_circ)
check("GMB_CIRCULAR_ETA: three DIFFERENT trips, repeated eta_seq is collapsed",
      "etaSeq is excluded on purpose" in gmb_circ
      and "public static final int MAX_TRIPS = 3" in gmb_circ
      and "if (!seen.containsKey(k))" in gmb_circ
      and "sortByMillis(" in gmb_circ)
check("GMB_CIRCULAR_ETA: LAST terminus never reuses the FIRST terminus trips",
      all(s in gmb_circ for s in (
          "excludeFirstStopTrips(",
          "excludeFirstStopTrips(lastPool, firstPool)",
          "without(deduped, keys(firstPool))",
          "boolean atLast",
          "loopLastSeq"))
      and "never fabricated" in gmb_circ)
check("GMB_CIRCULAR_ETA: StopOption carries loopLastSeq, favourites stay compatible",
      "private final int loopLastSeq" in models_java
      and "public int getLoopLastSeq()" in models_java
      and "public StopOption(String company, String route, String bound, int seq,"
      in models_java
      and "this(company, route, bound, seq, stopId, stopName, serviceType,"
      in models_java
      and "Kept OUT of key()" in models_java
      and "COMPANY|ROUTE|STOP_ID" not in models_java)
check("GMB_CIRCULAR_ETA: gmbStops sets loopLastSeq from official route-stop data",
      all(s in repo_java for s in (
          "CircularRoutes.isCircularDest(v.note)",
          "int loopLastSeq = circular ? maxSeq : 0",
          "v.routeId, v.note, loopLastSeq")))
check("GMB_CIRCULAR_ETA: gmbEta selects BEFORE emitting any EtaItem",
      all(s in repo_java for s in (
          "new GmbCircularEta.Raw(",
          "occurrence.optInt(\"stop_seq\", 0)",
          "GmbCircularEta.select(",
          "stop.getLoopLastSeq()",
          "for (GmbCircularEta.Raw r : chosen)"))
      and repo_java.find("GmbCircularEta.select(")
      < repo_java.find("for (GmbCircularEta.Raw r : chosen)"))
check("GMB_CIRCULAR_ETA: official circular ETA sample has two stop_seq occurrences",
      len(SAMPLES["gmb_eta_stop_circular"]["data"]) == 2
      and SAMPLES["gmb_eta_stop_circular"]["data"][0]["stop_seq"]
      != SAMPLES["gmb_eta_stop_circular"]["data"][1]["stop_seq"]
      and "eta_seq" in SAMPLES["gmb_eta_stop_circular"]["data"][1]["eta"][0])
check("CTB_ONLY_NAME: ONLY pure-Citybus cards / stops use Citybus wording",
      "CTB_ONLY_NAME" in joint_java
      and "CTB_ONLY_NAME" in fmt_kt
      and "s.getStopName(), s.getServiceType()" in joint_java
      and "kmbNameByBoundSeq" not in joint_java
      and "kmbBySeq" not in joint_java
      and "if (!hasKmb)" in joint_java
      and "return stops;" in joint_java
      and "JointRoutes.isJointCompany(company)" in vm_kt
      and "JointRoutes.preferKmbStops(raw)" in vm_kt)
check("CTB_ONLY_NAME: per-card stop label is resolved by ONE pure function",
      "fun resolveStopDisplay(" in fmt_kt
      and "displays[cardCompany] ?: fallback" in fmt_kt
      and "resolveStopDisplay(cardCompany, displays, fallback)" in fmt_kt
      and "displays[EtaRepository.CTB]" not in fmt_kt)
check("CTB_ONLY_NAME: JOINT keeps KMB, CTB-only sequence is returned untouched",
      "displays[JointRoutes.JOINT] = kmbDisplay" in vm_kt
      and "displays[own.company] = StopDisplay(" in vm_kt
      and "CTB_STOP_NAME" in vm_kt
      and "CTB_STOP_NAME" in fmt_kt)
check("CTB_ONLY_NAME: no KMB wording is ever copied onto a Citybus-only stop",
      "String kmbName =" not in joint_java
      and "? kmbName : s.getStopName()" not in joint_java
      and "kmbNameByBoundSeq" not in joint_java
      and "s.getStopId(), s.getStopName(), s.getServiceType()" in joint_java
      and "return stops" in joint_java)
check("JOINT_KMB_NAME: joint route stop labels use the KMB / LWB name",
      "JOINT_KMB_NAME" in joint_java
      and "public static List<StopOption> preferKmbStops" in joint_java
      and "claimedSeq.contains(k)" in joint_java
      and "kmbNames.contains" in joint_java
      and "JointRoutes.preferKmbStops(raw)" in vm_kt
      and "pairs[it].first.company == EtaRepository.KMB" in vm_kt)
check("JOINT_KMB_NAME: no KMB data -> honest fallback, no invented names",
      "if (!hasKmb)" in joint_java
      and "return stops;" in joint_java
      and "?: indices.first()" in vm_kt)
check("CTB_STOP_NAME: Citybus cards use the CITYBUS stop name / stop id",
      "CTB_STOP_NAME" in fmt_kt
      and "data class StopDisplay(val name: String, val id: String)" in fmt_kt
      and "displays: Map<String, StopDisplay> = emptyMap()" in fmt_kt
      and "val display = resolveStopDisplay(cardCompany, displays, fallback)" in fmt_kt
      and "display.name.ifEmpty { fallback.name }" in fmt_kt
      and "display.id.ifEmpty { fallback.id }" in fmt_kt
      and "CTB_STOP_NAME" in vm_kt
      and "displays[own.company] = StopDisplay(" in vm_kt
      and "displays[JointRoutes.JOINT] = kmbDisplay" in vm_kt)
check("CTB_STOP_NAME: JOINT keeps the KMB label, other callers unchanged",
      "displays[EtaRepository.KMB]" in vm_kt
      and "cardsFrom(" in fmt_kt
      and "rank: Int = 0," in fmt_kt
      and "App1933Format.cardsFrom(state.etas" in main_kt
      and "App1933Format.cardsFrom(" in detail_kt
      and "state.etas" in detail_kt)
check("CTB_STOP_NAME: pure CTB stop sequence is NOT rewritten to KMB names",
      "JointRoutes.isJointCompany(company)" in vm_kt
      and "JointRoutes.preferKmbStops(raw)" in vm_kt
      and "else -> listOf(company)" in vm_kt
      and "safeNames(CTB)" in read(API / "EtaRepository.java")
      and "RouteDataStore.CTB_STOPS" in read(API / "EtaRepository.java")
      and "case CTB:" in read(API / "EtaRepository.java"))
check("JOINT_KMB_CTB: wording resources match the Java constants",
      all(s in settings_strings for s in (
          'name="joint_route_label"', 'name="joint_route_note"',
          'name="joint_trip_prefix"', 'name="joint_operator_ctb"',
          'name="joint_operator_kmb"'))
      and "<string name=\"joint_route_label\">城巴/九巴</string>" in settings_strings
      and "<string name=\"joint_trip_prefix\">該班次：</string>" in settings_strings)

if failures:
    print(f"\n{len(failures)} check(s) failed: {', '.join(failures)}")
    sys.exit(1)
print("\nAll static checks passed.")
