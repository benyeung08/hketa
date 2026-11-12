package com.hketa.app

import android.app.Application

/**
 * Application entry point. Holds the base URL of the TypeScript API server.
 *
 * For an emulator, point BASE_URL at your dev machine:
 *   - http://10.0.2.2:3000/api   (standard Android emulator)
 *   - http://192.168.x.x:3000/api (physical device, replace with your IP)
 */
class HkEtaApp : Application() {

    companion object {
        const val BASE_URL = "http://10.0.2.2:3000/api/"
    }
}
