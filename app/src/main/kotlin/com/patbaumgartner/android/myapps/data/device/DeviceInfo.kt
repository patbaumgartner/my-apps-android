package com.patbaumgartner.android.myapps.data.device

import android.os.Build

/** Device facts needed for ABI selection and compatibility checks. */
object DeviceInfo {
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    val sdkInt: Int = Build.VERSION.SDK_INT
}
