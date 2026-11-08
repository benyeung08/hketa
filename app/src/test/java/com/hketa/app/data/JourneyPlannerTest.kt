package com.hketa.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 純 JVM 邏輯測試：唔使 Android SDK／Robolectric */
class JourneyPlannerTest {

    private fun fixture(): IndexData {
        val stops = listOf("A", "B", "C", "D", "E", "F", "X", "Z").map {
            StopDef(op = Operator.KMB, id = it, name = it)
        }
        // 1A: A→B→C→D ; 6: C→E→F ; 2: X→Z（CTB）
        val rs = mutableListOf<RouteStopDef>()
        listOf("A", "B", "C", "D").forEachIndexed { i, s ->
            rs.add(RouteStopDef(Operator.KMB, "1A", "1", "01", "", i + 1, s))
        }
        listOf("C", "E", "F").forEachIndexed { i, s ->
            rs.add(RouteStopDef(Operator.KMB, "6", "1", "01", "", i + 1, s))
        }
        listOf("X", "Z").forEachIndexed { i, s ->
            rs.add(RouteStopDef(Operator.CTB, "2", "I", "01", "", i + 1, s))
        }
        return IndexData(
            routes = listOf(
                RouteDef(Operator.KMB, "1A", "1"),
                RouteDef(Operator.KMB, "6", "1"),
                RouteDef(Operator.CTB, "2", "I")
            ),
            stops = stops,
            routeStops = rs
        )
    }

    @Test fun directRouteIsFound() {
        val r = JourneyPlanner.plan(fixture(), "A", "D")
        assertTrue(r.any { it.kind == JourneyKind.DIRECT && it.firstRoute == "1A" })
        assertEquals(3, r.first { it.kind == JourneyKind.DIRECT }.stops)
    }

    @Test fun oneTransferIsFound() {
        val r = JourneyPlanner.plan(fixture(), "A", "F")
        val t = r.firstOrNull { it.kind == JourneyKind.TRANSFER }
        assertTrue("應該搵到 1A→C→6", t != null)
        assertEquals("C", t!!.interchange)
        assertEquals("1A", t.firstRoute)
        assertEquals("6", t.secondRoute)
    }

    @Test fun unrelatedStopsReturnEmpty() {
        assertTrue(JourneyPlanner.plan(fixture(), "A", "Z").isEmpty())
    }

    @Test fun sameStationReturnsEmpty() {
        assertTrue(JourneyPlanner.plan(fixture(), "A", "A").isEmpty())
    }

    @Test fun emptyIndexReturnsEmpty() {
        assertTrue(JourneyPlanner.plan(IndexData(), "A", "D").isEmpty())
    }

    @Test fun wrongDirectionIsNotSuggested() {
        // D 係 1A 尾站，唔應該建議「D → A」倒頭車
        assertTrue(JourneyPlanner.plan(fixture(), "D", "A")
            .none { it.kind == JourneyKind.DIRECT })
    }
}
