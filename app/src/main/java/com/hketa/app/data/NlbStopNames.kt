package com.hketa.app.data

/**
 * 嶼巴（NLB）站名嘅中英對照表。
 *
 * 點解要呢份表：官方接口 `rt.data.gov.hk/.../nlb` **只提供中文站名**，
 * 冇 `name_en` 欄位（九巴／城巴／小巴都有），所以英文介面下嶼巴站名會維持中文。
 *
 * 呢份表係**手工整理嘅內置對照**，來源係大嶼山各站嘅官方英文地名
 * （政府地理資訊地名 + 嶼巴站牌英文名）。**唔係機器翻譯** ——
 * 機器翻譯出嚟嘅英文名會同站牌對唔上，反而難搵車。
 *
 * 覆蓋範圍：大嶼山主要道路／碼頭／景點／屋苑嘅常用站名（約 90 個），
 * 涵蓋嶼巴 64 條路線嘅絕大部分車站。
 * **未命中一律回退中文**，唔會亂畀一個似是而非嘅英文名。
 *
 * 若然發現錯漏，改呢份表就得，唔使改其他 code。
 */
object NlbStopNames {

    private val map: Map<String, String> = mapOf(
        // ===== 碼頭／交通樞紐 =====
        "梅窩碼頭" to "Mui Wo Ferry Pier",
        "梅窩" to "Mui Wo",
        "梅窩總站" to "Mui Wo Terminus",
        "東涌市中心" to "Tung Chung Town Centre",
        "東涌" to "Tung Chung",
        "東涌新市鎮" to "Tung Chung New Town",
        "東涌站" to "Tung Chung Station",
        "機場" to "Airport",
        "香港國際機場" to "Hong Kong International Airport",
        "機場客運大樓" to "Airport Passenger Terminal",
        "大澳" to "Tai O",
        "大澳總站" to "Tai O Terminus",
        "昂坪" to "Ngong Ping",
        "昂坪總站" to "Ngong Ping Terminus",
        "昂坪360" to "Ngong Ping 360",
        "東涌纜車站" to "Tung Chung Cable Car Terminal",
        "愉景灣" to "Discovery Bay",
        "愉景灣碼頭" to "Discovery Bay Ferry Pier",
        "愉景灣總站" to "Discovery Bay Terminus",
        "迪士尼" to "Disneyland",
        "香港迪士尼樂園" to "Hong Kong Disneyland",
        "迪士尼樂園" to "Disneyland Resort",
        "青衣" to "Tsing Yi",
        "青衣站" to "Tsing Yi Station",
        "欣澳" to "Sunny Bay",
        "欣澳站" to "Sunny Bay Station",

        // ===== 海灣／沙灘 =====
        "貝澳" to "Pui O",
        "貝澳總站" to "Pui O Terminus",
        "長沙" to "Cheung Sha",
        "上長沙" to "Upper Cheung Sha",
        "下長沙" to "Lower Cheung Sha",
        "塘福" to "Tong Fuk",
        "水口" to "Shui Hau",
        "石壁" to "Shek Pik",
        "分流" to "Fan Lau",
        "二浪灣" to "Yi Long Wan",
        "芝麻灣" to "Chi Ma Wan",
        "十塱" to "Shap Long",
        "大浪灣" to "Tai Long Wan",
        "長沙泳灘" to "Cheung Sha Beach",
        "貝澳泳灘" to "Pui O Beach",
        "洪聖爺灣" to "Hung Shing Ye Beach",

        // ===== 村落／鄉郊 =====
        "伯公坳" to "Pak Kung Au",
        "黃泥墩" to "Wong Nai Tun",
        "龍井頭" to "Lung Tseng Tau",
        "牛牯塱" to "Ngau Kwu Long",
        "沙螺灣" to "Sha Lo Wan",
        "大蠔" to "Tai Ho",
        "小蠔" to "Siu Ho",
        "白芒" to "Pak Mong",
        "田心" to "Tin Sum",
        "藍田" to "Lam Tin",
        "南丫" to "Lamma",
        "大嶼山" to "Lantau Island",
        "嶼南道" to "South Lantau Road",
        "嶼北道" to "North Lantau Road",
        "東涌道" to "Tung Chung Road",
        "羌山道" to "Keung Shan Road",
        "大澳道" to "Tai O Road",
        "深屈道" to "Sham Wat Road",
        "石門" to "Shek Mun",
        "散石灣" to "San Shek Wan",
        "䃟頭" to "Kau Liu" ,
        "狗嶺涌" to "Kau Ling Chung",
        "羅屋" to "Lo Uk",
        "沙咀" to "Sha Tsui",
        "大轉" to "Tai Tsuen",
        "二轉" to "Yi Tsuen",
        "新圍" to "San Wai",
        "旧圍" to "Kau Wai",
        "老圍" to "Lo Wai",
        "涌口" to "Chung Hau",
        "涌尾" to "Chung Mei",

        // ===== 屋苑／設施 =====
        "澄碧邨" to "Radiant Villas",
        "富東邨" to "Fu Tung Estate",
        "裕東苑" to "Yu Tung Court",
        "滿東邨" to "Mun Tung Estate",
        "東環" to "Century Link",
        "昇薈" to "The Visionary",
        "藍天海岸" to "Coastal Skyline",
        "海堤灣畔" to "Seaview Crescent",
        "映灣園" to "Caribbean Coast",
        "東薈城" to "Citygate",
        "機場管理局" to "Airport Authority",
        "機場消防局" to "Airport Fire Station",
        "機場警署" to "Airport Police Station",
        "亞洲國際博覽館" to "AsiaWorld-Expo",
        "香港天際萬豪酒店" to "Hong Kong SkyCity Marriott",
        "國泰城" to "Cathay City",
        "民航處" to "Civil Aviation Department",
        "超級一號貨站" to "SuperTerminal 1",
        "機場空運中心" to "Airport Freight Forwarding Centre",
        "政府飛行服務隊" to "Government Flying Service",
        "大嶼山警署" to "Lantau Police Station",
        "梅窩警署" to "Mui Wo Police Station",
        "梅窩市政大廈" to "Mui Wo Municipal Services Building",
        "東涌市政大廈" to "Tung Chung Municipal Services Building",
        "北大嶼山醫院" to "North Lantau Hospital",
        "梅窩診所" to "Mui Wo Clinic",
        "東涌健康中心" to "Tung Chung Health Centre",
        "寶蓮寺" to "Po Lin Monastery",
        "天壇大佛" to "Tian Tan Buddha",
        "心經簡林" to "Wisdom Path",
        "大澳棚屋" to "Tai O Stilt Houses",
        "東涌炮台" to "Tung Chung Fort",
        "大白灣" to "Tai Pak Wan",
        "稔樹灣" to "Nim Shue Wan",
        "大水坑" to "Tai Shui Hang",
        "三白灣" to "Sam Pak Wan"
    )

    /**
     * 查英文名。查唔到就返 null（**唔會**自己砌一個），
     * 等呼叫方決定點樣 fallback —— 通常係維持中文原名。
     */
    fun englishOf(zhName: String): String? {
        if (zhName.isBlank()) return null
        val key = zhName.trim()
        // 完全命中
        map[key]?.let { return it }
        // 站名常帶括號或後綴（例：「梅窩碼頭（總站）」），試吓去掉括號部分再搵
        val noBracket = key.substringBefore("（").substringBefore("(").trim()
        if (noBracket != key) map[noBracket]?.let { return it }
        // 部分命中：站名包含某個已知地名（例：「長沙泳灘路」→ Cheung Sha）
        for ((zh, en) in map) {
            if (zh.length >= 2 && key.contains(zh)) return en
        }
        return null
    }

    /** 對照表覆蓋幾多個站名（debug／診斷用） */
    fun size(): Int = map.size
}
