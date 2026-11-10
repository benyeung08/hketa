#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
擴充 hketa.json：為所有路線補上「模式／收費／服務班次」，並加入
巴士、專線小巴、地鐵、輕鐵四類路線示範資料。

用法（喺 repo 根目錄）：
    python3 scripts/gen_data.py

會就地更新 app/src/main/assets/hketa.json。
原檔嘅 14 條路線、175 個站位、10 個 ETA 站全部保留，只係補欄位。
所有新增站位座標為線性插值嘅約略值，收費／班次為示範約值，
以各營運商官方公布為準（apiDocs 附官方連結）。
"""
import json
import os
import random

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(ROOT, "app/src/main/assets/hketa.json")

with open(PATH, encoding="utf-8") as f:
    db = json.load(f)

# ── 1. 模式與營運商 ───────────────────────────────────────────
db["modes"] = [
    {"id": "bus", "zh": "巴士", "en": "Bus"},
    {"id": "minibus", "zh": "專線小巴", "en": "Minibus"},
    {"id": "metro", "zh": "地鐵", "en": "Metro"},
    {"id": "light_rail", "zh": "輕鐵", "en": "Light Rail"},
]
ops = {o["id"] for o in db["operators"]}
for o in [
    {"id": "GMB", "zh": "專線小巴", "en": "GMB"},
    {"id": "LRT", "zh": "港鐵輕鐵", "en": "MTR Light Rail"},
]:
    if o["id"] not in ops:
        db["operators"].append(o)

db["apiDocs"].update({
    "td_gmb": "https://www.td.gov.hk/en/transport_in_hong_kong/public_transport/buses/public_light_buses/index.html",
    "mtr_official": "https://www.mtr.com.hk/",
    "mtr_light_rail": "https://www.mtr.com.hk/en/main/mtr-light-rail.html",
    "kmb_fares": "https://www.kmb.hk/",
    "ctb_fares": "https://www.citybus.com.hk/",
    "nlb_fares": "https://www.nlb.com.hk/",
    "data_gov_hk_transport": "https://data.gov.hk/en-data/dataset/hk-td-tis_21-eta",
})

# ── 2. 既有 14 條路線補欄位 ────────────────────────────────────
MODE_OF_CO = {"KMB": "bus", "CTB": "bus", "NLB": "bus", "MTR": "metro"}

FARE_OF_CO = {
    "KMB": (6.7, 3.4, 2.0, "分段收費，上車付款", "Section fare, pay on boarding"),
    "CTB": (8.4, 4.2, 2.0, "分段收費，八達通／現金", "Section fare, Octopus / Cash"),
    "NLB": (11.8, 5.9, 2.0, "分段收費，假日票價或有所不同", "Section fare; holiday fares may differ"),
    "MTR": (8.5, 4.3, 2.0, "八達通按車程收費，單程票較高", "Octopus distance-based fare; single-journey ticket costs more"),
}
SVC_OF_MODE = {
    "bus": ("05:30", "00:20", 5, 10, 45),
    "metro": ("05:55", "00:45", 2, 5, 32),
}


def bi(zh, en):
    return {"zh": zh, "en": en}


def make_fare(full, child, senior, note_zh, note_en, method=("八達通／現金", "Octopus / Cash")):
    return {
        "currency": "HKD",
        "full": full,
        "child": child,
        "senior": senior,
        "method": bi(*method),
        "note": bi(note_zh, note_en),
        "sections": [],
    }


def make_services(first, last, peak, offpeak, journey, mode):
    peak_note = (
        "繁忙時間 07:00–09:30、17:00–19:30",
        "Peak hours 07:00–09:30, 17:00–19:30",
    )
    return [
        {
            "days": bi("星期一至五", "Mon–Fri"),
            "first": first, "last": last,
            "peakMin": peak, "offPeakMin": offpeak, "journeyMin": journey,
            "note": bi(*peak_note),
        },
        {
            "days": bi("星期六、日及公眾假期", "Sat, Sun & Public Holidays"),
            "first": first, "last": last,
            "peakMin": peak + 2, "offPeakMin": offpeak + 3, "journeyMin": journey,
            "note": bi("班次較疏，以現場公布為準", "Less frequent; subject to on-site notice"),
        },
    ]


for r in db["routes"]:
    co = r["co"]
    mode = MODE_OF_CO.get(co, "bus")
    r["mode"] = mode
    b0 = r["bounds"][0]
    r["name"] = bi(
        b0["orig"]["zh"] + " ↔ " + b0["dest"]["zh"],
        b0["orig"]["en"] + " ↔ " + b0["dest"]["en"],
    )
    full, child, senior, nzh, nen = FARE_OF_CO[co]
    r["fare"] = make_fare(full, child, senior, nzh, nen)
    f, l, p, op, j = SVC_OF_MODE[mode]
    r["services"] = make_services(f, l, p, op, j, mode)

# ── 3. 新增路線定義 ───────────────────────────────────────────
# (id, co, mode, number, zh名, en名, 收費, 小童, 長者, 收費備註zh, en,
#  頭班, 尾班, 繁忙, 非繁忙, 全程, 起點座標, 終點座標, 去程站(中,英), 回程站)
NEW = [
    # 巴士
    ("KMB_6C", "KMB", "bus", "6C", "九龍城碼頭 ↔ 美孚", "Kowloon City Ferry ↔ Mei Foo",
     6.7, 3.4, 2.0, "分段收費 $5.2 起", "Section fare from $5.2",
     "05:25", "00:15", 6, 11, 52, (22.3250, 114.1910), (22.3380, 114.1510),
     [("九龍城碼頭", "KOWLOON CITY FERRY"), ("馬頭角道", "MA TAU KOK ROAD"),
      ("土瓜灣", "TO KWA WAN"), ("紅磡", "HUNG HOM"), ("佐敦", "JORDAN"),
      ("油麻地", "YAU MA TEI"), ("旺角", "MONG KOK"), ("深水埗", "SHAM SHUI PO"),
      ("美孚", "MEI FOO")],
     [("美孚", "MEI FOO"), ("深水埗", "SHAM SHUI PO"), ("旺角", "MONG KOK"),
      ("油麻地", "YAU MA TEI"), ("紅磡", "HUNG HOM"), ("九龍城碼頭", "KOWLOON CITY FERRY")]),
    ("CTB_780", "CTB", "bus", "780", "柴灣（東）↔ 中環（港澳碼頭）", "Chai Wan (E) ↔ Central (Macau Ferry)",
     8.4, 4.2, 2.0, "過東區走廊後分段收費", "Section fare after Eastern Corridor",
     "05:40", "00:10", 7, 12, 40, (22.2650, 114.2380), (22.2880, 114.1510),
     [("柴灣（東）總站", "CHAI WAN (E) BUS TERMINUS"), ("興華邨", "HING WAH ESTATE"),
      ("東區走廊", "EASTERN CORRIDOR"), ("銅鑼灣", "CAUSEWAY BAY"),
      ("灣仔", "WAN CHAI"), ("金鐘", "ADMIRALTY"), ("中環（港澳碼頭）", "CENTRAL (MACAU FERRY)")],
     [("中環（港澳碼頭）", "CENTRAL (MACAU FERRY)"), ("金鐘", "ADMIRALTY"),
      ("灣仔", "WAN CHAI"), ("銅鑼灣", "CAUSEWAY BAY"), ("柴灣（東）總站", "CHAI WAN (E) BUS TERMINUS")]),
    ("NLB_11", "NLB", "bus", "11", "東涌市中心 ↔ 大澳", "Tung Chung Town Centre ↔ Tai O",
     11.8, 5.9, 2.0, "假日票價或有所不同", "Holiday fares may differ",
     "06:00", "23:30", 20, 30, 35, (22.2890, 113.9410), (22.2560, 113.8630),
     [("東涌市中心", "TUNG CHUNG TOWN CENTRE"), ("馬灣新村", "MA WAN NEW VILLAGE"),
      ("䃟石灣", "SAN SHEK WAN"), ("深屈", "SHAM WAT"), ("大澳", "TAI O")],
     [("大澳", "TAI O"), ("深屈", "SHAM WAT"), ("䃟石灣", "SAN SHEK WAN"),
      ("東涌市中心", "TUNG CHUNG TOWN CENTRE")]),
    ("KMB_A41", "KMB", "bus", "A41", "沙田（愉翠苑）↔ 機場", "Sha Tin (Yu Chui Court) ↔ Airport",
     23.4, 11.7, 2.0, "龍運機場線，分段收費", "LWB airport route, section fare",
     "05:30", "23:50", 15, 20, 65, (22.3830, 114.1960), (22.3080, 113.9180),
     [("愉翠苑", "YU CHUI COURT"), ("沙田市中心", "SHA TIN TOWN CENTRE"),
      ("青衣", "TSING YI"), ("機場一號客運大樓", "AIRPORT TERMINAL 1"),
      ("機場地面運輸中心", "AIRPORT GROUND TRANSPORTATION CENTRE")],
     [("機場地面運輸中心", "AIRPORT GROUND TRANSPORTATION CENTRE"),
      ("青衣", "TSING YI"), ("沙田市中心", "SHA TIN TOWN CENTRE"), ("愉翠苑", "YU CHUI COURT")]),
    # 專線小巴
    ("GMB_1", "GMB", "minibus", "1", "中環（香港站）↔ 山頂", "Central (Hong Kong Station) ↔ The Peak",
     11.5, 5.8, 2.0, "分段收費 $6.5 起", "Section fare from $6.5",
     "06:00", "00:15", 4, 8, 18, (22.2860, 114.1580), (22.2760, 114.1500),
     [("香港站", "HONG KONG STATION"), ("畢打街", "PEDDER STREET"),
      ("堅道", "CAINE ROAD"), ("寶雲道", "BOWEN ROAD"), ("山頂總站", "THE PEAK TERMINUS")],
     [("山頂總站", "THE PEAK TERMINUS"), ("寶雲道", "BOWEN ROAD"),
      ("堅道", "CAINE ROAD"), ("香港站", "HONG KONG STATION")]),
    ("GMB_8", "GMB", "minibus", "8", "尖沙咀 ↔ 何文田邨", "Tsim Sha Tsui ↔ Ho Man Tin Estate",
     8.9, 4.5, 2.0, "八達通／現金，分段收費", "Octopus / Cash, section fare",
     "06:15", "23:45", 5, 9, 16, (22.2970, 114.1720), (22.3100, 114.1830),
     [("尖沙咀（漢口道）", "TSIM SHA TSUI (HANKOW ROAD)"), ("金馬倫道", "CAMERON ROAD"),
      ("柯士甸道", "AUSTIN ROAD"), ("何文田邨", "HO MAN TIN ESTATE"), ("皓畋", "MANTIN HEIGHTS")],
     [("皓畋", "MANTIN HEIGHTS"), ("何文田邨", "HO MAN TIN ESTATE"),
      ("柯士甸道", "AUSTIN ROAD"), ("尖沙咀（漢口道）", "TSIM SHA TSUI (HANKOW ROAD)")]),
    ("GMB_25M", "GMB", "minibus", "25M", "九龍塘站 ↔ 東頭邨", "Kowloon Tong Station ↔ Tung Tau Estate",
     6.5, 3.3, 2.0, "短程分段收費", "Short-hop section fare",
     "06:20", "23:30", 4, 7, 12, (22.3370, 114.1760), (22.3300, 114.1920),
     [("九龍塘站", "KOWLOON TONG STATION"), ("聯合道", "JUNCTION ROAD"),
      ("東頭邨", "TUNG TAU ESTATE"), ("美東邨", "MEI TUNG ESTATE")],
     [("美東邨", "MEI TUNG ESTATE"), ("東頭邨", "TUNG TAU ESTATE"),
      ("聯合道", "JUNCTION ROAD"), ("九龍塘站", "KOWLOON TONG STATION")]),
    ("GMB_40", "GMB", "minibus", "40", "屯門市中心 ↔ 小坑村", "Tuen Mun Town Centre ↔ Siu Hang Tsuen",
     9.2, 4.6, 2.0, "分段收費", "Section fare",
     "05:50", "23:20", 6, 10, 22, (22.3910, 113.9760), (22.4120, 113.9820),
     [("屯門市中心", "TUEN MUN TOWN CENTRE"), ("屯門站", "TUEN MUN STATION"),
      ("兆康", "SIU HONG"), ("小坑村", "SIU HANG TSUEN")],
     [("小坑村", "SIU HANG TSUEN"), ("兆康", "SIU HONG"),
      ("屯門站", "TUEN MUN STATION"), ("屯門市中心", "TUEN MUN TOWN CENTRE")]),
    # 地鐵
    ("MTR_TCL", "MTR", "metro", "TCL", "東涌綫", "Tung Chung Line",
     16.0, 8.0, 2.0, "八達通按車程收費", "Octopus distance-based fare",
     "05:55", "01:10", 3, 6, 28, (22.2860, 114.1580), (22.2890, 113.9410),
     [("香港", "HONG KONG"), ("九龍", "KOWLOON"), ("青衣", "TSING YI"),
      ("欣澳", "SUNNY BAY"), ("東涌", "TUNG CHUNG")],
     [("東涌", "TUNG CHUNG"), ("欣澳", "SUNNY BAY"), ("青衣", "TSING YI"),
      ("九龍", "KOWLOON"), ("香港", "HONG KONG")]),
    ("MTR_AEL", "MTR", "metro", "AEL", "機場快綫", "Airport Express",
     115.0, 57.5, 2.0, "機場快綫獨立票價", "Airport Express separate fare",
     "05:50", "00:48", 10, 12, 24, (22.2860, 114.1580), (22.3110, 113.9130),
     [("香港", "HONG KONG"), ("九龍", "KOWLOON"), ("青衣", "TSING YI"),
      ("機場", "AIRPORT"), ("博覽館", "ASIAWORLD-EXPO")],
     [("博覽館", "ASIAWORLD-EXPO"), ("機場", "AIRPORT"), ("青衣", "TSING YI"),
      ("九龍", "KOWLOON"), ("香港", "HONG KONG")]),
    ("MTR_DRL", "MTR", "metro", "DRL", "迪士尼綫", "Disneyland Resort Line",
     5.5, 2.8, 2.0, "八達通單程收費", "Octopus single fare",
     "06:15", "00:45", 5, 8, 4, (22.3330, 114.0290), (22.3130, 114.0410),
     [("欣澳", "SUNNY BAY"), ("迪士尼", "DISNEYLAND RESORT")],
     [("迪士尼", "DISNEYLAND RESORT"), ("欣澳", "SUNNY BAY")]),
    ("MTR_EAL", "MTR", "metro", "EAL", "東鐵綫", "East Rail Line",
     12.5, 6.3, 2.0, "八達通按車程收費，過海附加費另計", "Octopus distance-based; cross-harbour supplement may apply",
     "05:35", "00:35", 3, 6, 42, (22.3030, 114.1810), (22.5010, 114.1260),
     [("紅磡", "HUNG HOM"), ("旺角東", "MONG KOK EAST"), ("九龍塘", "KOWLOON TONG"),
      ("大圍", "TAI WAI"), ("沙田", "SHA TIN"), ("火炭", "FO TAN"),
      ("大埔墟", "TAI PO MARKET"), ("太和", "TAI WO"), ("粉嶺", "FANLING"), ("上水", "SHEUNG SHUI")],
     [("上水", "SHEUNG SHUI"), ("粉嶺", "FANLING"), ("太和", "TAI WO"),
      ("大埔墟", "TAI PO MARKET"), ("沙田", "SHA TIN"), ("大圍", "TAI WAI"),
      ("九龍塘", "KOWLOON TONG"), ("旺角東", "MONG KOK EAST"), ("紅磡", "HUNG HOM")]),
    # 輕鐵
    ("LRT_505", "LRT", "light_rail", "505", "三聖 ↔ 兆康", "Sam Shing ↔ Siu Hong",
     5.5, 3.0, 2.0, "八達通按收費區，單程票另計", "Octopus fare zones; single-journey ticket costs more",
     "05:30", "00:40", 5, 9, 20, (22.3830, 113.9760), (22.4120, 113.9820),
     [("三聖", "SAM SHING"), ("屯門市中心", "TUEN MUN TOWN CENTRE"),
      ("市中心", "TOWN CENTRE"), ("安定", "ON TING"), ("友愛", "YAU OI"), ("兆康", "SIU HONG")],
     [("兆康", "SIU HONG"), ("友愛", "YAU OI"), ("安定", "ON TING"),
      ("市中心", "TOWN CENTRE"), ("三聖", "SAM SHING")]),
    ("LRT_507", "LRT", "light_rail", "507", "屯門碼頭 ↔ 田景", "Tuen Mun Ferry Pier ↔ Tin King",
     5.5, 3.0, 2.0, "八達通按收費區", "Octopus fare zones",
     "05:35", "00:35", 6, 10, 18, (22.3730, 113.9690), (22.4060, 113.9640),
     [("屯門碼頭", "TUEN MUN FERRY PIER"), ("美樂", "MEI LOK"),
      ("蝴蝶", "BUTTERFLY"), ("新屯門中心", "SUN TUEN MUN CENTRE"), ("田景", "TIN KING")],
     [("田景", "TIN KING"), ("新屯門中心", "SUN TUEN MUN CENTRE"),
      ("蝴蝶", "BUTTERFLY"), ("美樂", "MEI LOK"), ("屯門碼頭", "TUEN MUN FERRY PIER")]),
    ("LRT_610", "LRT", "light_rail", "610", "屯門碼頭 ↔ 元朗", "Tuen Mun Ferry Pier ↔ Yuen Long",
     7.5, 3.8, 2.0, "跨收費區，八達通按區收費", "Cross fare zones, Octopus zone-based",
     "05:25", "00:30", 7, 11, 35, (22.3730, 113.9690), (22.4440, 114.0220),
     [("屯門碼頭", "TUEN MUN FERRY PIER"), ("美樂", "MEI LOK"), ("蝴蝶", "BUTTERFLY"),
      ("輕鐵車廠", "LIGHT RAIL DEPOT"), ("龍門", "LUNG MUN"), ("青山村", "TSING SHAN TSUEN"),
      ("藍地", "LAM TEI"), ("洪水橋", "HUNG SHUI KIU"), ("屏山", "PING SHAN"), ("元朗", "YUEN LONG")],
     [("元朗", "YUEN LONG"), ("屏山", "PING SHAN"), ("洪水橋", "HUNG SHUI KIU"),
      ("藍地", "LAM TEI"), ("青山村", "TSING SHAN TSUEN"), ("龍門", "LUNG MUN"),
      ("蝴蝶", "BUTTERFLY"), ("美樂", "MEI LOK"), ("屯門碼頭", "TUEN MUN FERRY PIER")]),
    ("LRT_751", "LRT", "light_rail", "751", "天逸 ↔ 友愛", "Tin Yat ↔ Yau Oi",
     7.5, 3.8, 2.0, "跨收費區，八達通按區收費", "Cross fare zones, Octopus zone-based",
     "05:28", "00:38", 6, 10, 38, (22.4660, 113.9960), (22.3910, 113.9760),
     [("天逸", "TIN YAT"), ("天恆", "TIN HENG"), ("濕地公園", "WETLAND PARK"),
      ("天秀", "TIN SAU"), ("天水圍", "TIN SHUI WAI"), ("兆康", "SIU HONG"),
      ("屯門", "TUEN MUN"), ("市中心", "TOWN CENTRE"), ("友愛", "YAU OI")],
     [("友愛", "YAU OI"), ("市中心", "TOWN CENTRE"), ("屯門", "TUEN MUN"),
      ("兆康", "SIU HONG"), ("天水圍", "TIN SHUI WAI"), ("天秀", "TIN SAU"),
      ("濕地公園", "WETLAND PARK"), ("天恆", "TIN HENG"), ("天逸", "TIN YAT")]),
]

random.seed(20261110)


def interp(a, b, t, jitter=0.0012):
    return round(a + (b - a) * t + random.uniform(-jitter, jitter), 6)


def add_stops(route_id, direction, names, p0, p1):
    ids = []
    n = max(len(names) - 1, 1)
    for i, (zh, en) in enumerate(names):
        sid = "%s_%s_%02d" % (route_id, direction, i + 1)
        t = i / n
        db["stops"][sid] = {
            "name": bi(zh, en),
            "lat": interp(p0[0], p1[0], t),
            "lng": interp(p0[1], p1[1], t),
        }
        ids.append(sid)
    return ids


for spec in NEW:
    (rid, co, mode, number, zhname, enname, full, child, senior, fnzh, fnen,
     first, last, peak, offpeak, journey, p0, p1, stops_o, stops_i) = spec
    ids_o = add_stops(rid, "O", stops_o, p0, p1)
    ids_i = add_stops(rid, "I", stops_i, p1, p0)
    db["routes"].append({
        "id": rid, "co": co, "mode": mode, "number": number,
        "name": bi(zhname, enname),
        "fare": make_fare(full, child, senior, fnzh, fnen),
        "services": make_services(first, last, peak, offpeak, journey, mode),
        "bounds": [
            {"dir": "O", "label": bi("去程", "Outbound"),
             "orig": bi(*stops_o[0]), "dest": bi(*stops_o[-1]), "stopIds": ids_o},
            {"dir": "I", "label": bi("回程", "Inbound"),
             "orig": bi(*stops_i[0]), "dest": bi(*stops_i[-1]), "stopIds": ids_i},
        ],
    })

# ── 4. 為新四類各補示範 ETA ────────────────────────────────────
ETA_SEED = [
    ("KMB_6C", "O", 1),
    ("CTB_780", "O", 1),
    ("GMB_1", "O", 1),
    ("GMB_25M", "O", 1),
    ("MTR_TCL", "O", 1),
    ("MTR_AEL", "O", 1),
    ("LRT_505", "O", 1),
    ("LRT_610", "O", 1),
]
for rid, d, seq in ETA_SEED:
    sid = "%s_%s_%02d" % (rid, d, seq)
    if sid not in db["stops"] or sid in db["etas"]:
        continue
    r = next(x for x in db["routes"] if x["id"] == rid)
    dest = r["bounds"][0]["dest"]
    is_rail = r["mode"] in ("metro", "light_rail")
    db["etas"][sid] = {
        "stopId": sid,
        "generatedTimestamp": "2026-11-10T19:00:00+08:00",
        "etas": [
            {"seq": 1, "time": "19:04", "minutes": 4, "dest": dest,
             "scheduled": True, "remark": bi("原定班次", "Scheduled"),
             **({"platform": "1"} if is_rail else {})},
            {"seq": 2, "time": "19:%02d" % (10 if is_rail else 16),
             "minutes": 10 if is_rail else 16, "dest": dest,
             "scheduled": True, "remark": bi("原定班次", "Scheduled"),
             **({"platform": "2"} if is_rail else {})},
            {"seq": 3, "time": "19:%02d" % (16 if is_rail else 28),
             "minutes": 16 if is_rail else 28, "dest": dest,
             "scheduled": True, "remark": bi("原定班次", "Scheduled")},
        ],
    }

# ── 5. 版本與說明 ─────────────────────────────────────────────
db["version"] = "2.0.0"
db["generatedTimestamp"] = "2026-11-10T19:00:00+08:00"
db["note"] = bi(
    "示範資料，非即時；收費／班次為約值，站位座標為約略值，以營運商官方公布為準",
    "Demo data, not live; fares/frequencies are approximate, stop coordinates are rough. Refer to official operators.",
)
db["fareDisclaimer"] = bi(
    "收費僅作示範，八達通／現金、分段、假日及優惠票價以官方為準",
    "Fares are illustrative only; Octopus/cash, section, holiday and concessionary fares follow official sources.",
)

with open(PATH, "w", encoding="utf-8") as f:
    json.dump(db, f, ensure_ascii=False, indent=2)
    f.write("\n")

print("routes=%d stops=%d etas=%d ops=%d modes=%d" % (
    len(db["routes"]), len(db["stops"]), len(db["etas"]),
    len(db["operators"]), len(db["modes"])))
from collections import Counter
print("by mode:", dict(Counter(r["mode"] for r in db["routes"])))
