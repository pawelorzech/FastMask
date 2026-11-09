package com.fastmask.ui.navigation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.fastmask.R
import com.fastmask.domain.share.ShareInbox
import com.fastmask.domain.share.ShareIntentPolicy
import com.fastmask.domain.share.ShareRequest
import com.fastmask.domain.share.ShareRoute
import com.fastmask.domain.share.ShareRouter
import com.fastmask.domain.share.SharedLinkParser

/**
 * Text shared into the app (ACTION_SEND) that has not been routed yet. A rotation
 * re-runs onCreate with the same intent, so whether the launch intent's share was
 * already routed is kept in the instance state, or every rotation would reopen a
 * create form the user had filled in or dismissed.
 */
class PendingShare {
    var request by mutableStateOf<ShareRequest?>(null)
        private set

    private var consumed = false
    private val inbox = ShareInbox()

    fun onCreate(intent: Intent?, savedInstanceState: Bundle?) {
        consumed = savedInstanceState?.getBoolean(KEY_CONSUMED) ?: false
        request = if (consumed) null else fromIntent(intent)
    }

    fun onNewIntent(intent: Intent) {
        val share = fromIntent(intent) ?: return
        consumed = false
        request = share
    }

    fun save(outState: Bundle) {
        outState.putBoolean(KEY_CONSUMED, consumed)
    }

    internal fun onRouted(route: ShareRoute) {
        if (ShareRouter.consumes(route)) {
            consumed = true
            request = null
        }
    }

    private fun fromIntent(intent: Intent?): ShareRequest? {
        // getCharSequenceExtra, not getStringExtra: EXTRA_TEXT is a CharSequence
        // and a Spanned from the sending app makes getStringExtra return null.
        val sharedText: String = ShareIntentPolicy.sharedText(
            isSend = intent?.action == Intent.ACTION_SEND,
            type = intent?.type,
            text = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT),
        ) ?: return null
        return inbox.offer(prefill = SharedLinkParser.parse(sharedText))
    }

    private companion object {
        const val KEY_CONSUMED = "fastmask_share_consumed"
    }
}

/** Must be composed only behind the app lock: a share waits there and must never become a way past it. */
@Composable
fun RoutePendingShare(
    pending: PendingShare,
    signedIn: Boolean,
    locked: Boolean,
    navController: NavController,
) {
    val context = LocalContext.current
    LaunchedEffect(pending.request, signedIn, locked) {
        val route = ShareRouter.route(request = pending.request, signedIn = signedIn, locked = locked)
        when (route) {
            is ShareRoute.OpenCreate ->
                navController.navigate(NavRoutes.createEmail(route.prefill)) { launchSingleTop = true }
            // Dropped, but never in silence: replaying the text after an external
            // sign-in round trip would be a surprise nobody can connect to anything
            // they did. A Toast because there is no SnackbarHost above the NavHost.
            ShareRoute.RejectSignedOut ->
                Toast.makeText(context, R.string.share_requires_sign_in, Toast.LENGTH_LONG).show()
            ShareRoute.WaitForUnlock -> Unit
            ShareRoute.Idle -> Unit
        }
        pending.onRouted(route)
    }
}
