package com.reactnativetwiliophone.callView

import android.os.Handler
import android.os.Looper
import com.reactnativetwiliophone.log


internal inline fun onMainThread(crossinline doWork: () -> Unit) {
  log("onMainThread √√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√")

  Handler(Looper.getMainLooper()).post {
    log("Looper.getMainLooper √√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√√")

    doWork()
  }
}
