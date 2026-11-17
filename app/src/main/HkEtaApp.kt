package hk.eta.app

import android.app.Application
import hk.eta.app.api.RouteDataStore

/** Initialises the private route-data directory once, before any Activity starts. */
class HkEtaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RouteDataStore.init(this)
    }
}
