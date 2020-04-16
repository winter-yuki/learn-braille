package com.github.braillesystems.learnbraille.util

import android.app.Application
import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

fun <T, R> T?.side(block: (T) -> R) {
    if (this == null) return
    block(this)
}

val Application.usbManager get() = getSystemService(USB_SERVICE) as UsbManager

operator fun MatchGroupCollection.component1() = get(0)
operator fun MatchGroupCollection.component2() = get(1)
operator fun MatchGroupCollection.component3() = get(2)
operator fun MatchGroupCollection.component4() = get(3)
operator fun MatchGroupCollection.component5() = get(4)

val Any?.devnull: Unit get() {}

fun scope(job: Job = Job()) = CoroutineScope(Dispatchers.Main + job)
