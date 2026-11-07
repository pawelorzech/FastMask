package com.fastmask.data.local

import android.content.Context
import androidx.security.crypto.MasterKey

/**
 * The Keystore master key behind every encrypted file this app owns (the token
 * preferences and the offline mask cache). It is the default alias with
 * AES256_GCM, and both users must share exactly this configuration: changing the
 * scheme or alias here makes already-written encrypted data unreadable.
 */
internal fun appMasterKey(context: Context): MasterKey =
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
