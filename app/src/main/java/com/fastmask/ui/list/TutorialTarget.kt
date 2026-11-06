package com.fastmask.ui.list

import androidx.annotation.StringRes
import com.fastmask.R

/**
 * The elements the demo tutorial points at, in the order it visits them.
 * Each one reports its on-screen bounds; the tutorial starts once every entry
 * has been measured.
 */
enum class TutorialTarget(@StringRes val titleRes: Int, @StringRes val bodyRes: Int) {
    /** The mask list as a whole. */
    LIST(R.string.tutorial_step_1_title, R.string.tutorial_step_1_body),
    SEARCH(R.string.tutorial_step_2_title, R.string.tutorial_step_2_body),
    FILTER(R.string.tutorial_step_3_title, R.string.tutorial_step_3_body),
    FIRST_MASK(R.string.tutorial_step_4_title, R.string.tutorial_step_4_body),
    CREATE_FAB(R.string.tutorial_step_5_title, R.string.tutorial_step_5_body),
}
