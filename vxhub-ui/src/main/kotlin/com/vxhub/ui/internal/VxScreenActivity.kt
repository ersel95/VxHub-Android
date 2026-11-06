package com.vxhub.ui.internal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What a hosted screen receives: call [dismiss] to close it. */
internal class VxScreenScope(val activity: ComponentActivity, val dismiss: () -> Unit)

/**
 * Presents a composable full screen from any context — the Android counterpart of iOS presenting
 * a view controller `overFullScreen`.
 *
 * Screen configurations hold lambdas, colours and fonts, none of which can travel in an Intent, so
 * the content stays in memory under an id and only the id goes into the Intent. If the process
 * dies while the screen is open, the restored activity finds no content and closes itself; the
 * app's own state decides what to show next, exactly as after any process death.
 */
internal object VxScreenHost {
    private val pending = ConcurrentHashMap<String, @Composable VxScreenScope.() -> Unit>()

    fun launch(context: Context, content: @Composable VxScreenScope.() -> Unit) {
        val id = UUID.randomUUID().toString()
        pending[id] = content
        val intent = Intent(context, VxScreenActivity::class.java).putExtra(EXTRA_ID, id)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun content(id: String?): (@Composable VxScreenScope.() -> Unit)? = id?.let(pending::get)

    fun release(id: String?) {
        if (id != null) pending.remove(id)
    }

    const val EXTRA_ID: String = "com.vxhub.ui.screen.id"
}

internal class VxScreenActivity : ComponentActivity() {
    private val screenId: String? get() = intent.getStringExtra(VxScreenHost.EXTRA_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val content = VxScreenHost.content(screenId)
        if (content == null) {
            finish()
            return
        }
        val scope = VxScreenScope(this) { finish() }
        setContent { content(scope) }
    }

    override fun onDestroy() {
        if (isFinishing) VxScreenHost.release(screenId)
        super.onDestroy()
    }
}
