package com.vxhub.appsflyer

import android.content.Context
import com.appsflyer.AppsFlyerLib
import com.appsflyer.share.AppsFlyerConversionListener
import com.vxhub.providers.VxAttributionListener
import com.vxhub.providers.VxAttributionProvider

/**
 * AppsFlyer install attribution — iOS's `VxAppsFlyerManager`. Conversion data is forwarded to the
 * VxHub backend by core and to the app's listener.
 */
public class VxAppsFlyerProvider : VxAttributionProvider {
    @Volatile
    private var appContext: Context? = null

    override fun initialize(
        context: Context,
        devKey: String,
        appId: String,
        customerUserId: String,
        language: String,
        listener: VxAttributionListener,
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        val appsFlyer = AppsFlyerLib.getInstance()
        appsFlyer.setCustomerUserId(customerUserId)
        appsFlyer.init(
            devKey,
            object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: Map<String, Any>?) {
                    listener.onConversionDataSuccess(data.orEmpty())
                }

                override fun onConversionDataFail(error: String?) {
                    listener.onConversionDataFail(IllegalStateException(error ?: "AppsFlyer conversion data failed"))
                }
            },
            ctx,
        )
    }

    override fun start(context: Context) {
        AppsFlyerLib.getInstance().start()
    }

    override fun logEvent(name: String, values: Map<String, Any?>?) {
        val ctx = appContext ?: return
        val clean: Map<String, Any> = values.orEmpty().mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        AppsFlyerLib.getInstance().logEvent(ctx, name, clean)
    }

    override fun changeCustomerUserId(customerUserId: String) {
        AppsFlyerLib.getInstance().setCustomerUserId(customerUserId)
    }

    override val attributionId: String
        get() = appContext?.let { AppsFlyerLib.getInstance().getAppsFlyerUID(it) }.orEmpty()
}
