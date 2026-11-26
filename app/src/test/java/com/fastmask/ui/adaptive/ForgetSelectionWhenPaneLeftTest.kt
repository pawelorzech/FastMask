@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package com.fastmask.ui.adaptive

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ForgetSelectionWhenPaneLeftTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `leaving detail pane via back clears selection and does not fire before entering`() {
        var clearedCount = 0
        lateinit var navigator: ThreePaneScaffoldNavigator<String>
        lateinit var scope: CoroutineScope

        composeRule.setContent {
            navigator = rememberListDetailPaneScaffoldNavigator<String>()
            scope = rememberCoroutineScope()
            ForgetSelectionWhenPaneLeft(
                navigator = navigator,
                detailRole = ListDetailPaneScaffoldRole.Detail,
                onLeft = { clearedCount++ },
            )
        }

        composeRule.waitForIdle()
        assertEquals("must not clear before Detail is opened", 0, clearedCount)

        composeRule.runOnIdle {
            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, "detail:m1") }
        }
        composeRule.waitForIdle()
        assertEquals("entering Detail must not clear selection", 0, clearedCount)

        composeRule.runOnIdle {
            scope.launch { navigator.navigateBack() }
        }
        composeRule.waitForIdle()
        assertEquals("leaving Detail back to List must clear selection once", 1, clearedCount)
    }
}
