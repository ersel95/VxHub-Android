package com.vxhub.internal.network

import android.content.Context
import com.vxhub.internal.VxLog
import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The VxHub API sits behind Cloudflare mutual TLS: every request presents the client certificate
 * the iOS SDK bundles as `vx_mtls_certificate.p12`.
 *
 * The Android copy (`assets/vxhub/mtls.p12`) holds the same key and certificate re-wrapped with
 * PBE-SHA1-3DES instead of the original 40-bit RC2, which newer Android releases can no longer
 * decrypt. Server trust is untouched: the platform's default trust store still validates the API.
 */
internal object MutualTls {
    private const val ASSET = "vxhub/mtls.p12"

    // Same value as MTLS_PASS in the iOS SDK's VxInfo plists; it protects nothing the binary does
    // not already contain, and is kept only because PKCS#12 requires a password.
    private val PASSWORD = "GWyHPpF5UEvQn1VHKZqxE".toCharArray()

    /**
     * Installs the client certificate on [builder]. When the certificate cannot be loaded the
     * builder is left alone, so the request still goes out and fails with a readable 4xx instead
     * of the SDK failing to construct a client at all.
     */
    fun apply(context: Context, builder: OkHttpClient.Builder) {
        try {
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                context.assets.open(ASSET).use { load(it, PASSWORD) }
            }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore, PASSWORD) }
                .keyManagers

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
            val trustManager = trustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().first()

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(keyManagers, arrayOf(trustManager), null)
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            VxLog.e("mTLS client certificate could not be loaded; requests will be rejected by the API", e)
        }
    }
}
