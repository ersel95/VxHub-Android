package com.vxhub.firebase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GoogleServicesJsonTest {
    private val json = """
        {"project_info":{"project_number":"1234567890","project_id":"vx-demo","storage_bucket":"vx-demo.appspot.com"},
         "client":[
           {"client_info":{"mobilesdk_app_id":"1:1234567890:android:aaa","android_client_info":{"package_name":"com.other.app"}},
            "api_key":[{"current_key":"KEY_OTHER"}]},
           {"client_info":{"mobilesdk_app_id":"1:1234567890:android:bbb","android_client_info":{"package_name":"com.vx.app"}},
            "api_key":[{"current_key":"KEY_APP"}]}],
         "configuration_version":"1"}
    """.trimIndent()

    @Test
    fun picksTheClientOfThisPackage() {
        val config = GoogleServicesJson.parse(json, "com.vx.app")!!
        assertThat(config.applicationId).isEqualTo("1:1234567890:android:bbb")
        assertThat(config.apiKey).isEqualTo("KEY_APP")
        assertThat(config.projectId).isEqualTo("vx-demo")
        assertThat(config.gcmSenderId).isEqualTo("1234567890")
        assertThat(config.storageBucket).isEqualTo("vx-demo.appspot.com")
        assertThat(config.databaseUrl).isNull()
    }

    @Test
    fun neverBorrowsAnotherAppsClient() {
        assertThat(GoogleServicesJson.parse(json, "com.unknown.app")).isNull()
    }

    @Test
    fun anIosPlistIsRejected() {
        assertThat(GoogleServicesJson.parse("<?xml version=\"1.0\"?><plist/>", "com.vx.app")).isNull()
    }
}
