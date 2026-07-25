package com.fastmask.quickmask

import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickMaskActivity : ComponentActivity() {

    @Inject
    lateinit var runner: QuickMaskRunner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runner.launchCreate()
        finish()
    }
}
