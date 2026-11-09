package com.fastmask.data.local

import android.content.Context
import androidx.security.crypto.MasterKey

/**
 * Every encrypted file this app owns shares this Keystore key: changing the scheme
 * or alias makes already-written encrypted data unreadable.
 */
internal fun appMasterKey(context: Context): MasterKey =
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
