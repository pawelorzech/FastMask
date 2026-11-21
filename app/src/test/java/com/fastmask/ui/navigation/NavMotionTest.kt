package com.fastmask.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavMotionTest {

    private data class EdgeCase(
        val fromRoute: String?,
        val toRoute: String?,
        val isPop: Boolean,
        val fromIsModalLogin: Boolean = false,
        val toIsModalLogin: Boolean = false,
        val expectedStyle: NavTransitionStyle,
        val expectedEnterRole: TransitionLayerRole,
        val expectedExitRole: TransitionLayerRole,
    )

    @Test
    fun `every navigation edge resolves to its logical M3 Expressive style and layer roles`() {
        val cases = listOf(
            // Horizontal hierarchy (X axis)
            EdgeCase(
                fromRoute = NavRoutes.WELCOME,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                expectedStyle = NavTransitionStyle.HIERARCHY_FORWARD,
                expectedEnterRole = TransitionLayerRole.TOP_HORIZONTAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.WELCOME,
                isPop = true,
                expectedStyle = NavTransitionStyle.HIERARCHY_BACKWARD,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_HORIZONTAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_LIST,
                toRoute = NavRoutes.EMAIL_DETAIL,
                isPop = false,
                expectedStyle = NavTransitionStyle.HIERARCHY_FORWARD,
                expectedEnterRole = TransitionLayerRole.TOP_HORIZONTAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_DETAIL,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = true,
                expectedStyle = NavTransitionStyle.HIERARCHY_BACKWARD,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_HORIZONTAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_LIST,
                toRoute = NavRoutes.SETTINGS,
                isPop = false,
                expectedStyle = NavTransitionStyle.HIERARCHY_FORWARD,
                expectedEnterRole = TransitionLayerRole.TOP_HORIZONTAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.SETTINGS,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = true,
                expectedStyle = NavTransitionStyle.HIERARCHY_BACKWARD,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_HORIZONTAL,
            ),

            // Vertical modal sheets (Y axis)
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_LIST,
                toRoute = NavRoutes.CREATE_EMAIL,
                isPop = false,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.SETTINGS,
                toRoute = NavRoutes.PRO,
                isPop = false,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.PRO,
                toRoute = NavRoutes.SETTINGS,
                isPop = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),

            // Share Create opened on top of Detail, Settings, or Pro
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_DETAIL,
                toRoute = NavRoutes.CREATE_EMAIL,
                isPop = false,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.EMAIL_DETAIL,
                isPop = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.SETTINGS,
                toRoute = NavRoutes.CREATE_EMAIL,
                isPop = false,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.SETTINGS,
                isPop = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.PRO,
                toRoute = NavRoutes.CREATE_EMAIL,
                isPop = false,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.PRO,
                isPop = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),

            // Modal re-authentication over workspace screens
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_LIST,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = true,
                fromIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_DETAIL,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.EMAIL_DETAIL,
                isPop = true,
                fromIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_OPEN,
                expectedEnterRole = TransitionLayerRole.TOP_MODAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.CREATE_EMAIL,
                isPop = true,
                fromIsModalLogin = true,
                expectedStyle = NavTransitionStyle.MODAL_CLOSE,
                expectedEnterRole = TransitionLayerRole.UNDERNEATH,
                expectedExitRole = TransitionLayerRole.TOP_MODAL,
            ),

            // Session entry (Z forward via resetTo)
            EdgeCase(
                fromRoute = NavRoutes.WELCOME,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = false,
                expectedStyle = NavTransitionStyle.SESSION_ENTER,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = false,
                expectedStyle = NavTransitionStyle.SESSION_ENTER,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.EMAIL_LIST,
                isPop = false,
                fromIsModalLogin = true,
                expectedStyle = NavTransitionStyle.SESSION_ENTER,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),

            // Session exit (Z backward via resetTo: logout & demo exit)
            EdgeCase(
                fromRoute = NavRoutes.SETTINGS,
                toRoute = NavRoutes.WELCOME,
                isPop = false,
                expectedStyle = NavTransitionStyle.SESSION_EXIT,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_LIST,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = false,
                expectedStyle = NavTransitionStyle.SESSION_EXIT,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.EMAIL_DETAIL,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = false,
                expectedStyle = NavTransitionStyle.SESSION_EXIT,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.CREATE_EMAIL,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = false,
                expectedStyle = NavTransitionStyle.SESSION_EXIT,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),
            EdgeCase(
                fromRoute = NavRoutes.SETTINGS,
                toRoute = NavRoutes.LOGIN,
                isPop = false,
                toIsModalLogin = false,
                expectedStyle = NavTransitionStyle.SESSION_EXIT,
                expectedEnterRole = TransitionLayerRole.NONE,
                expectedExitRole = TransitionLayerRole.NONE,
            ),

            // Demo-exit step-back arrow: Login -> Welcome via resetTo
            EdgeCase(
                fromRoute = NavRoutes.LOGIN,
                toRoute = NavRoutes.WELCOME,
                isPop = false,
                expectedStyle = NavTransitionStyle.STEP_BACK_RESET,
                expectedEnterRole = TransitionLayerRole.TOP_HORIZONTAL,
                expectedExitRole = TransitionLayerRole.UNDERNEATH,
            ),
        )

        cases.forEach { case ->
            val style = resolveTransitionStyle(
                fromRoute = case.fromRoute,
                toRoute = case.toRoute,
                isPop = case.isPop,
                fromIsModalLogin = case.fromIsModalLogin,
                toIsModalLogin = case.toIsModalLogin,
            )
            assertEquals("style for $case", case.expectedStyle, style)
            assertEquals("enterRole for $case", case.expectedEnterRole, style.enterLayerRole())
            assertEquals("exitRole for $case", case.expectedExitRole, style.exitLayerRole())
        }
    }
}
