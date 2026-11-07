package com.vxhub.sample

import android.app.Application
import android.util.Log
import com.vxhub.VxEnvironment
import com.vxhub.VxHub
import com.vxhub.VxHubConfig
import com.vxhub.VxHubException
import com.vxhub.VxHubListener
import com.vxhub.VxLogLevel

/** The whole integration: one call in Application.onCreate. */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VxHub.initialize(
            application = this,
            config = VxHubConfig(
                hubId = BuildConfig.VXHUB_HUB_ID,
                environment = VxEnvironment.STAGE,
                logLevel = VxLogLevel.DEBUG,
            ),
            listener = object : VxHubListener {
                override fun onInitialized() {
                    Log.i(TAG, "VxHub ready: ${VxHub.deviceInfo.value}")
                }

                override fun onFailed(error: VxHubException) {
                    Log.w(TAG, "VxHub failed: ${error.message}")
                }

                override fun onUserSessionExpired() {
                    Log.i(TAG, "Account session expired")
                }
            },
        )
    }

    private companion object {
        const val TAG = "VxHubSample"
    }
}
