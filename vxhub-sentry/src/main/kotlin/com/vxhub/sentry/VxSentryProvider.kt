package com.vxhub.sentry

import android.content.Context
import com.vxhub.providers.VxCrashReportingProvider
import com.vxhub.providers.VxSentryConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User

/** Sentry crash reporting with the panel's DSN — iOS's `VxSentryManager`. */
public class VxSentryProvider : VxCrashReportingProvider {
    override fun start(context: Context, dsn: String, config: VxSentryConfig) {
        SentryAndroid.init(context.applicationContext) { options ->
            options.dsn = dsn
            options.environment = config.environment
            options.isDebug = config.enableDebug
            options.tracesSampleRate = config.tracesSampleRate
            options.profileSessionSampleRate = config.profilesSampleRate
            options.isAttachScreenshot = config.attachScreenshot
            options.isAttachViewHierarchy = config.attachViewHierarchy
        }
    }

    override fun stop() {
        Sentry.close()
    }

    override fun setUserId(userId: String) {
        Sentry.setUser(User().apply { id = userId })
    }
}
