package com.fastmask

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fastmask.domain.repository.SessionRepository
import com.fastmask.testutil.awaitNonNull
import com.fastmask.testutil.enterDemoAndDismissTutorial
import com.fastmask.testutil.launchOnFreshInstall
import com.fastmask.testutil.string
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Text shared into FastMask (ACTION_SEND, text/plain) as the user experiences it:
 * a link goes in, the create form comes up with the domain filled in, and the share
 * is neither lost, replayed, nor allowed to open a form the user cannot submit.
 *
 * The routing rules themselves are unit-tested as pure logic (`ShareRouter`,
 * `SharedLinkParser`, `ShareIntentPolicy`); what only a running Activity can show is
 * that `MainActivity` actually hands the intent to that logic, on `onCreate`, on
 * `onNewIntent` and across recreation. This replaces a unit test that grepped
 * `MainActivity`'s source for those calls.
 *
 * Not covered: a share arriving while the biometric app lock is up. Arming the lock
 * is possible (the setting is a DataStore flag), but leaving it again needs a
 * successful `BiometricPrompt`, which an unenrolled test device cannot give, and a
 * failed teardown would leave the lock armed for every later test. "A held share
 * survives the gate" stays covered by `ShareRouter`'s unit tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ShareIntoAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Empty rule, not createAndroidComposeRule: the Activity reads persisted state in
    // onCreate to pick its start destination, so app state has to be wiped BEFORE it
    // launches, not after (see MainFlowsTest).
    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var sessionRepository: SessionRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchOnAFreshInstall() {
        hiltRule.inject()
        scenario = composeRule.launchOnFreshInstall(sessionRepository)
    }

    @After
    fun tearDown() {
        if (!::scenario.isInitialized) return
        // ActivityScenario finds its activity by intent, and onNewIntent replaced it with
        // the share (as it must in production); hand the launch intent back so close()
        // can still see the activity finish.
        launchIntent?.let { original -> scenario.onActivity { it.intent = original } }
        scenario.close()
    }

    private var launchIntent: android.content.Intent? = null

    // --- tests ------------------------------------------------------------

    @Test
    fun aSharedLinkOpensTheCreateFormWithItsDomainFilledIn() {
        composeRule.enterDemoAndDismissTutorial()

        deliverShare("Worth a look: $SHARED_LINK")

        awaitCreateScreen()
        awaitDomainContaining(SHARED_DOMAIN)
    }

    @Test
    fun theSameLinkSharedTwiceOpensTheFormTwice() {
        composeRule.enterDemoAndDismissTutorial()

        deliverShare(SHARED_LINK)
        awaitCreateScreen()
        awaitDomainContaining(SHARED_DOMAIN)
        leaveCreateScreen()

        // Identical text, identical prefill: only a delivery of its own can reopen it.
        deliverShare(SHARED_LINK)
        awaitCreateScreen()
        awaitDomainContaining(SHARED_DOMAIN)
    }

    @Test
    fun aShareThatLaunchedTheActivityIsNotReplayedAfterRecreation() {
        composeRule.enterDemoAndDismissTutorial()
        // Entering the demo is how this test gets a session that outlives the scenario;
        // the tutorial flag is written asynchronously, and an overlay on the relaunch
        // would hide the very screen under test.
        awaitNonNull("the demo tutorial to be recorded as finished") {
            true.takeIf { runBlocking { sessionRepository.tutorialCompleted.first() } }
        }
        scenario.close()

        // Cold start FROM a share: the launch intent stays the activity's intent, so a
        // rotation re-runs onCreate with it again. This is the replay the instance
        // state exists to stop.
        scenario = ActivityScenario.launch<MainActivity>(shareLaunchIntent(SHARED_LINK))
        awaitCreateScreen()
        awaitDomainContaining(SHARED_DOMAIN)
        leaveCreateScreen()

        scenario.recreate()
        awaitListScreen()
        composeRule.waitForIdle()

        assertFalse(
            "recreating the activity replayed a share that had already been routed",
            createScreenShown(),
        )
    }

    @Test
    fun aShareWhileSignedOutStaysOnWelcomeAndIsNotRememberedForLater() {
        deliverShare(SHARED_LINK)
        composeRule.waitForIdle()

        assertFalse("a signed-out share must not open the create form", createScreenShown())
        composeRule.onNodeWithText(string(R.string.welcome_signin_cta)).assertIsDisplayed()

        // The share was told "no" (a toast), not parked: the demo entered afterwards
        // must not suddenly act on a link shared minutes earlier.
        composeRule.enterDemoAndDismissTutorial()
        composeRule.waitForIdle()
        assertFalse("the rejected share came back after sign-in", createScreenShown())
    }

    // --- helpers ----------------------------------------------------------

    private fun shareIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)

    private fun shareLaunchIntent(text: String): Intent =
        shareIntent(text).setClass(
            ApplicationProvider.getApplicationContext<Context>(),
            MainActivity::class.java,
        )

    /**
     * Delivers the intent the way the framework does for a running target, through
     * `Activity.onNewIntent`. Instrumentation is the public route: the override is
     * protected in the Activity API, so a test cannot call it directly.
     */
    private fun deliverShare(text: String) {
        val intent = shareIntent(text)
        scenario.onActivity { activity ->
            if (launchIntent == null) launchIntent = activity.intent
            InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(activity, intent)
        }
    }

    /**
     * A field that exists only on the create form. The title cannot be used: "New mask"
     * is also the list FAB's label.
     */
    private fun createScreenShown(): Boolean =
        composeRule.onAllNodesWithContentDescription(string(R.string.create_email_domain_label))
            .fetchSemanticsNodes().isNotEmpty()

    private fun listScreenShown(): Boolean =
        composeRule.onAllNodesWithText(string(R.string.email_list_title))
            .fetchSemanticsNodes().isNotEmpty()

    private fun awaitCreateScreen() {
        composeRule.waitUntil(timeoutMillis = 10_000) { createScreenShown() }
    }

    private fun awaitListScreen() {
        composeRule.waitUntil(timeoutMillis = 10_000) { listScreenShown() }
    }

    /**
     * What the domain field holds. The field's name (contentDescription) and its edit
     * state sit on the same node, the way CreateMaskedEmailImeActionTest reads its
     * ImeAction off it.
     */
    private fun domainText(): String {
        val config = composeRule.onNodeWithContentDescription(string(R.string.create_email_domain_label))
            .fetchSemanticsNode()
            .config
        return config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
    }

    private fun awaitDomainContaining(domain: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            createScreenShown() && domainText().contains(domain)
        }
    }

    /**
     * Back out of the create form. A prefilled form is dirty, so back asks first;
     * confirming is part of leaving.
     */
    private fun leaveCreateScreen() {
        val discard = string(R.string.discard_changes_confirm)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !createScreenShown() ||
                composeRule.onAllNodesWithText(discard).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText(discard).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(discard).performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { !createScreenShown() && listScreenShown() }
        composeRule.waitForIdle()
    }

    private companion object {
        const val SHARED_LINK = "https://www.example.com/articles/42?utm_source=share"
        const val SHARED_DOMAIN = "www.example.com"
    }
}
