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
      "val cards = fetchCards(stops)" in eta_vm_kt
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
check("navbar GEOMETRY: smaller, centred, bottom-hugging capsule",
      'NAV_GEOMETRY' in main_layout
      and '@drawable/bg_nav_glass' in main_layout
      and 'android:layout_height="56dp"' in main_layout
      and 'android:layout_gravity="center_horizontal"' in main_layout
      and 'android:layout_marginStart="28dp"' in main_layout
      and 'android:layout_marginEnd="28dp"' in main_layout
      and 'android:layout_marginBottom="8dp"' in main_layout
      and 'android:elevation="6dp"' in main_layout
      and 'android:id="@+id/bottomTabs"' in main_layout
      and all(old not in main_layout[main_layout.index('NAV_GEOMETRY'):]
              for old in ('android:layout_marginStart="12dp"',
                          'android:layout_marginBottom="12dp"',
                          'android:layout_height="64dp"'))
      and all(Path(RES / f"drawable/{n}.xml").is_file() for n in (
          "bg_nav_glass", "bg_nav_glass_dark", "bg_nav_glass_contrast",
          "bg_tab_glass_selected")))
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
      and main_layout.count('@color/nav_tab_muted') == 3)
check("geometry scales with screen width; re-applied on rotation / resume",
      all(s in glass_kt for s in (
          "fun geometry(", "SIDE_RATIO_PHONE = 0.06f",
          "SIDE_RATIO_WIDE = 0.18f", "WIDE_BREAKPOINT_DP = 600f",
          "SIDE_MIN_DP = 16f", "SIDE_MAX_DP = 256f",
          "BAR_HEIGHT_DP = 56", "BAR_BOTTOM_DP = 8",
          "widthDp >= WIDE_BREAKPOINT_DP",
          "lp.marginStart = sidePx", "lp.marginEnd = sidePx",
          "lp.bottomMargin", "Gravity.CENTER_HORIZONTAL",
          "geometry(activity, bar)", "ELEVATION_DP = 6f"))
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
          "NavGlass.selectTab(this, tabs[i], i == index)"))
      and "setBackgroundColor(" not in main_kt
      and "fun setupNavGlassSection" in settings_kt
      and "R.id.swNavGlass" in settings_kt
      and "NavGlass.status(this)" in settings_kt)
check("nav glass strings all defined", all(
      f'<string name="{name}">' in read(RES / "values/strings.xml")
      for name in ("nav_glass_section", "nav_glass_follow", "nav_glass_hint",
                   "nav_glass_status", "nav_glass_state_follow",
                   "nav_glass_state_fixed", "nav_glass_state_night",
                   "nav_glass_state_contrast", "nav_glass_state_reduced",
                   "nav_glass_state_day")))

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
          'android:id="@+id/btnDetailBack"', 'android:id="@+id/detailActionTrips"',
          'android:id="@+id/detailActionFav"', 'android:id="@+id/detailActionDirection"',
          'android:id="@+id/detailSideChips"', 'android:id="@+id/chipBoard"',
          'android:id="@+id/chipNearby"', 'android:id="@+id/chipFav"',
          'android:id="@+id/chipMore"', 'android:id="@+id/detailStopsRecycler"',
          'android:id="@+id/tvStopBubble"', 'android:id="@+id/tvDetailFooter"'))
      and '@color/brand' in detail_xml)
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
check("BOTH Home cards and search rows open the NO-MAP RouteDetailActivity",
      main_kt.count('Intent(this, RouteDetailActivity::class.java)') >= 2
      and 'RouteDetailActivity.EXTRA_STOP_ID' in main_kt
      and 'fun openResult(result: RouteSearch.Result)' in main_kt
      and 'RouteDetailActivity.EXTRA_BOUND, result.bound' in main_kt
      and 'result.dest.removePrefix("往 ").trim()' in main_kt
      and 'vm.loadStops(result.route, result.company, result.bound)' not in main_kt)
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
          "MtrCatalog.LINE_CODES", "MtrCatalog.MTR_BUS_ROUTES",
          "目錄預載（未查方向）"))
      and 'new ArrayList<>(out.subList(0, 24))'
      in read(API / "RouteSearch.java"))
check("detail reuses open-data layer; every chip / header action has a real action",
      all(s in detail_kt for s in (
          "ViewModelProvider(this)[EtaViewModel::class.java]",
          "vm.loadStops(route, company, requestedBound)", "vm.selectStop(", "vm.refresh()",
          "vm.toggleDirection()", "vm.addCurrentFavorite()",
          "App1933Format.cardsFrom", "App1933Format.stopLabel",
          "class DetailStopAdapter", "fun submit(", "fun boardAt(",
          "fun buildTitle(", "Intent(this, SettingsActivity::class.java)"))
      and all(f'R.id.{n}' in detail_kt for n in (
          "btnDetailBack", "detailActionTrips", "detailActionFav",
          "detailActionDirection", "chipBoard", "chipNearby", "chipFav", "chipMore")))
check("detail strings all defined", all(
      f'<string name="{name}">' in read(RES / "values/strings.xml")
      for name in ("detail_title", "detail_no_map_label", "detail_footer_honest",
                   "detail_fare_na", "detail_alight", "detail_na", "detail_no_eta",
                   "detail_chip_board", "detail_chip_nearby", "detail_chip_fav",
                   "detail_chip_more", "detail_action_trips_cd",
                   "detail_action_fav_cd", "detail_action_direction_cd",
                   "detail_fav_ok", "detail_fav_need",
                   "detail_trips_refreshed", "detail_no_stops")))

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

# --- CI: surface the REAL Gradle error (Assemble debug APK 1m42s) ------------
build_error = read(ROOT / "scripts/show_build_error.py")
check("Gradle build logs are captured with pipefail + tee", all(
      s in workflow for s in ("set -o pipefail", "tee build-debug.log",
                              "tee build-release.log", "id: assemble_debug")))
# --- Kotlin compile pitfalls (real build-debug.log: compileDebugKotlin FAILED) -
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

# --- Java compile pitfall (build-debug.log: compileDebugJavaWithJavac FAILED) -
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
      and "gradle assembleRelease --no-daemon --stacktrace" not in workflow)
check("distiller warns when old stacktrace-shaped log is seen",
      "新 workflow 未生效" in build_error,
      "→ scripts/show_build_error.py 唔見咗或仍係舊版；由完整 zip 覆蓋，"
      "或全量 git add -A && git commit && git push")
check("failure steps distil the real error instead of stack frames", all(
      s in workflow for s in ("Show real Gradle error",
                              "steps.assemble_debug.outcome",
                              "scripts/show_build_error.py",
                              "Upload full Gradle error log",
                              "hketa-gradle-logs")))
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

release_notes_file = ROOT / "RELEASE_NOTES_v1.0.0.md"
if not release_notes_file.is_file():
    # 唔係錯誤：workflow 設計上容許新版未有專屬 notes，會自動產生簡短說明。
    print("[INFO] RELEASE_NOTES_v1.0.0.md 未上傳；"
          "workflow 會用版本對應嘅簡短 Release 內文（fallback），唔影響發布。")


def wpos(needle):
    """Safe .index() — returns -1 instead of raising on an old workflow."""
    return workflow.find(needle)


def one_apk_asset():
    start = wpos("Attach signed release APK")
    if start < 0 or "files: |" not in workflow[start:]:
        return False
    tail = workflow[start:].split("files: |", 1)[1]
    return len([l for l in tail.splitlines()
                if ".apk" in l and l.strip()
                and not l.strip().startswith("#")]) == 1


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
      and (not release_notes_file.is_file()
           or all(s in release_notes_file.read_text(encoding="utf-8")
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
check("gradle version fields are the single source of truth",
      'versionCode = 1' in app_gradle
      and 'versionName = "1.0.0"' in app_gradle
      and "in-app updater compares" in app_gradle)
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
card_kt = read(UI / "CardAdapters.kt")
check("APP1933 shell has 4 bottom tabs + 4 pages", all(
      f'android:id="@+id/{n}"' in main_layout for n in
      ("tabHome", "tabFav", "tabSearch", "tabMore",
       "pageHome", "pageFav", "pageSearch", "pageMore")))
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

# --- APP1933 搜尋頁：紅 bar / 路線結果 / 自訂鍵盤 -------------------------------
search_java = read(API / "RouteSearch.java")
result_row = read(RES / "layout/item_route_result.xml")
keypad_xml = read(RES / "layout/keypad.xml")
check("APP1933 route-search full screen (red bar + input + results + keypad)",
      all(s in main_layout for s in (
          'android:id="@+id/searchBar"', 'android:id="@+id/routeInput"',
          'android:id="@+id/searchResultsRecycler"',
          'android:id="@+id/stopSequenceView"',
          'android:id="@+id/keypadRoot"', 'android:id="@+id/mainHeader"',
          'android:id="@+id/bottomTabs"', 'android:id="@+id/btnBackToSearch"'))
      and '@layout/keypad' in main_layout)
check("APP1933 keypad: 1-9 / 0 / DEL / ENTER / 4 letter keys / page arrows",
      all(f'android:tag="{k}"' in keypad_xml for k in
          ("1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
           "DEL", "ENTER", "L0", "L1", "L2", "L3", "PREV", "NEXT"))
      and all(f'android:id="@+id/keyLetter{i}"' in keypad_xml
              for i in range(4))
      and '@drawable/bg_key' in keypad_xml
      and '@drawable/bg_key_circle' in keypad_xml)
check("route-search row shows big route no., 往 destination, attribution, chevron",
      all(s in result_row for s in (
          'android:id="@+id/resultRoute"', 'android:id="@+id/resultDest"',
          'android:id="@+id/resultNote"', 'android:id="@+id/resultChevron"'))
      and '往 尖沙咀碼頭' in result_row)
check("route search uses official catalogues for all 7 operator modes",
      all(s in search_java for s in (
          "RouteDataStore.KMB_ROUTES", "RouteDataStore.CTB_ROUTES",
          "RouteDataStore.NLB_ROUTES", "RouteDataStore.GMB_ROUTES",
          "MtrCatalog.isLineCode", "EtaRepository.MTRB",
          "資料由DATA.GOV.HK提供", "public static List<Result> search"))
      and "HttpJson.getObject" in search_java)
check("route search wired into ViewModel and Activity",
      all(s in vm_kt for s in ("fun searchRoutes", "RouteSearch.search",
                               "onlyBound", "searchJob"))
      and all(s in main_kt for s in (
          "bindKeypad", "fun onKey", "fun openResult",
          "showResults", "showSequence", "RouteResultAdapter",
          "vm.searchRoutes", "updateLetterKeys"))
      and "class RouteResultAdapter" in card_kt)

if failures:
    print(f"\n{len(failures)} check(s) failed: {', '.join(failures)}")
    sys.exit(1)
print("\nAll static checks passed.")
