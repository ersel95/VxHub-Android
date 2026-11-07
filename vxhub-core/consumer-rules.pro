# VxHub core. Everything else it needs is kept by the libraries' own rules (kotlinx.serialization,
# OkHttp, AndroidX Startup).

# The Google Advertising ID is read reflectively (VxHubConfig.collectAdvertisingId) so core does not
# force play-services-ads-identifier on apps that do not collect it.
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    public static *** getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    public java.lang.String getId();
    public boolean isLimitAdTrackingEnabled();
}
-dontwarn com.google.android.gms.ads.identifier.**

# play review-ktx's generated SAM adapter carries an annotation from a newer play-services-basement
# than it declares; it has no runtime role.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
