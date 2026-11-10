package hk.eta.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * 以 Android 內建 org.json 把 hketa.json 轉成 Kotlin data class。
 * 不引 kotlinx.serialization / Gson / Moshi，減少 GitHub Actions 的依賴下載。
 *
 * 舊版 JSON（冇 mode / fare / services）仍可讀取：mode 會按 co 推斷，
 * fare 為 null，services 為空，唔會閃退。
 */
object JsonParser {

    fun parse(text: String): HketaData {
        val root = JSONObject(text)

        val note = bi(root.optJSONObject("note"))
        val fareDisclaimer = bi(root.optJSONObject("fareDisclaimer"))

        val apiDocs = mutableMapOf<String, String>()
        val apiObj = root.optJSONObject("apiDocs") ?: JSONObject()
        val apiKeys = apiObj.keys()
        while (apiKeys.hasNext()) {
            val k = apiKeys.next()
            apiDocs[k] = apiObj.optString(k, "")
        }

        val modeArr = root.optJSONArray("modes") ?: JSONArray()
        val modes = (0 until modeArr.length()).map { i ->
            val m = modeArr.getJSONObject(i)
            TransportMode(
                m.optString("id", ""),
                m.optString("zh", ""),
                m.optString("en", "")
            )
        }

        val opArr = root.optJSONArray("operators") ?: JSONArray()
        val operators = (0 until opArr.length()).map { i ->
            val o = opArr.getJSONObject(i)
            Operator(
                o.optString("id", ""),
                o.optString("zh", ""),
                o.optString("en", "")
            )
        }

        val stopObj = root.optJSONObject("stops") ?: JSONObject()
        val stops = mutableMapOf<String, Stop>()
        val stopKeys = stopObj.keys()
        while (stopKeys.hasNext()) {
            val id = stopKeys.next()
            val s = stopObj.getJSONObject(id)
            stops[id] = Stop(
                id = id,
                name = bi(s.optJSONObject("name")),
                lat = s.optDouble("lat", 0.0),
                lng = s.optDouble("lng", 0.0)
            )
        }

        val routeArr = root.optJSONArray("routes") ?: JSONArray()
        val routes = (0 until routeArr.length()).map { i ->
            val r = routeArr.getJSONObject(i)
            val co = r.optString("co", "")
            val bArr = r.optJSONArray("bounds") ?: JSONArray()
            val bounds = (0 until bArr.length()).map { j ->
                val b = bArr.getJSONObject(j)
                val idArr = b.optJSONArray("stopIds") ?: JSONArray()
                Bound(
                    dir = b.optString("dir", "O"),
                    label = bi(b.optJSONObject("label")),
                    orig = bi(b.optJSONObject("orig")),
                    dest = bi(b.optJSONObject("dest")),
                    stopIds = (0 until idArr.length()).map { k -> idArr.optString(k, "") }
                )
            }
            val mode = r.optString("mode", "").ifBlank { inferMode(co) }
            Route(
                id = r.optString("id", ""),
                co = co,
                mode = mode,
                number = r.optString("number", ""),
                name = bi(r.optJSONObject("name")).let {
                    if (it.zh.isBlank() && bounds.isNotEmpty())
                        BiText(
                            bounds[0].orig.zh + " ↔ " + bounds[0].dest.zh,
                            bounds[0].orig.en + " ↔ " + bounds[0].dest.en
                        )
                    else it
                },
                bounds = bounds,
                fare = parseFare(r.optJSONObject("fare")),
                services = parseServices(r.optJSONArray("services"))
            )
        }

        val etaObj = root.optJSONObject("etas") ?: JSONObject()
        val etas = mutableMapOf<String, StopEta>()
        val etaKeys = etaObj.keys()
        while (etaKeys.hasNext()) {
            val id = etaKeys.next()
            val se = etaObj.getJSONObject(id)
            val eArr = se.optJSONArray("etas") ?: JSONArray()
            val list = (0 until eArr.length()).map { k ->
                val e = eArr.getJSONObject(k)
                Eta(
                    seq = e.optInt("seq", k + 1),
                    time = nullableString(e, "time"),
                    minutes = nullableInt(e, "minutes"),
                    dest = if (!e.has("dest") || e.isNull("dest")) null else bi(e.optJSONObject("dest")),
                    remark = if (!e.has("remark") || e.isNull("remark")) null else bi(e.optJSONObject("remark")),
                    platform = nullableString(e, "platform"),
                    scheduled = e.optBoolean("scheduled", false)
                )
            }
            etas[id] = StopEta(
                stopId = id,
                generatedTimestamp = se.optString("generatedTimestamp", ""),
                etas = list
            )
        }

        return HketaData(
            version = root.optString("version", "1.0"),
            generatedTimestamp = root.optString("generatedTimestamp", ""),
            demo = root.optBoolean("demo", true),
            note = note,
            fareDisclaimer = fareDisclaimer,
            apiDocs = apiDocs,
            modes = modes,
            operators = operators,
            stops = stops,
            routes = routes,
            etas = etas
        )
    }

    /** 舊版 JSON 冇 mode 時按營運商推斷。 */
    private fun inferMode(co: String): String = when (co) {
        "GMB" -> "minibus"
        "LRT" -> "light_rail"
        "MTR" -> "metro"
        else -> "bus"
    }

    private fun parseFare(o: JSONObject?): Fare? {
        if (o == null) return null
        val secArr = o.optJSONArray("sections") ?: JSONArray()
        val sections = (0 until secArr.length()).map { i ->
            val s = secArr.getJSONObject(i)
            FareSection(
                from = bi(s.optJSONObject("from")),
                to = bi(s.optJSONObject("to")),
                full = s.optDouble("full", 0.0)
            )
        }
        return Fare(
            currency = o.optString("currency", "HKD"),
            full = o.optDouble("full", 0.0),
            child = nullableDouble(o, "child"),
            senior = nullableDouble(o, "senior"),
            method = bi(o.optJSONObject("method")),
            note = bi(o.optJSONObject("note")),
            sections = sections
        )
    }

    private fun parseServices(arr: JSONArray?): List<Service> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Service(
                days = bi(s.optJSONObject("days")),
                first = s.optString("first", ""),
                last = s.optString("last", ""),
                peakMin = nullableInt(s, "peakMin"),
                offPeakMin = nullableInt(s, "offPeakMin"),
                journeyMin = nullableInt(s, "journeyMin"),
                note = bi(s.optJSONObject("note"))
            )
        }
    }

    private fun bi(o: JSONObject?): BiText =
        if (o == null) BiText("", "")
        else BiText(o.optString("zh", ""), o.optString("en", ""))

    private fun nullableString(o: JSONObject, key: String): String? =
        if (o.isNull(key) || !o.has(key)) null else o.optString(key, "")

    private fun nullableInt(o: JSONObject, key: String): Int? =
        if (o.isNull(key) || !o.has(key)) null else o.optInt(key, 0)

    private fun nullableDouble(o: JSONObject, key: String): Double? =
        if (o.isNull(key) || !o.has(key)) null else o.optDouble(key, 0.0)
}
