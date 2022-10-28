package com.reactnativetwiliophone.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import com.facebook.react.bridge.ReadableMap
import com.reactnativetwiliophone.Actions
import com.reactnativetwiliophone.Const
import com.reactnativetwiliophone.callView.ViewService
import com.reactnativetwiliophone.callView.tryOnly
import com.reactnativetwiliophone.log


object ViewUtils {
  var serviceIntent: Intent? = null
  var currentState = "OFF"

  @SuppressLint("SuspiciousIndentation")
  fun showCallView(context: Context, data: ReadableMap) {
    if(currentState == "OFF") {
      currentState = "ON"
      val callerName = data.getString(Const.CALLER_NAME)
      val callSid = data.getString(Const.CALL_SID)
      log("---------------------- showCallView start ------------------------")

      if (checkFloatingWindowPermission(context)) {
        if (callerName != null) {
          serviceIntent = Intent(context, ViewService::class.java)
          serviceIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          // serviceIntent!!.addFlags(FLAG_ACTIVITY_NO_HISTORY)
          serviceIntent!!.putExtra(Const.CALLER_NAME, callerName)
          serviceIntent!!.putExtra(Const.CALL_SID, callSid)
          serviceIntent!!.putExtra(Const.CALLER_IMAGE, "ic_notify.png")
          serviceIntent!!.putExtra(Const.MESSAGE_CALL, "Incoming Call ....")
          serviceIntent!!.action = Actions.START.name
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //context.startForegroundService(serviceIntent)
            ContextCompat.startForegroundService(
              context,
              serviceIntent!!
            );
          } else {
            context.startService(serviceIntent)
          }
          context.bindService(serviceIntent, ViewService().connection, 0);
        }
      }
    }
  }

  fun stopService(context: Context) {
    if(currentState == "ON") {
      currentState = "OFF"
      tryOnly {
        context.stopService(serviceIntent)
      }
    }
  }

  private fun checkFloatingWindowPermission(context: Context): Boolean {
    //val foregroud: Boolean = ForegroundCheckTask()!.execute(context).get()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (Settings.canDrawOverlays(context)) {
        true
      } else {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        } else {
          TODO("VERSION.SDK_INT < M")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent)
        // showPermissionDialog(context)
        false
      }
    } else {
      true
    }
  }

   @RequiresApi(Build.VERSION_CODES.M)
   fun checkDisplayBubblesPermission(activity: Activity, context: Context) {
     if(!canDisplayBubbles(context)){
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
         showPermissionDialog(activity, context,
           Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS),
           "NOTIFICATION BUBBLE Permission Required",
           "To see incoming calls and notifications  please Enable notifications bubble permission now?"
         )
       }
     }
     if(ContextCompat.checkSelfPermission(context,
         android.Manifest.permission.ACCESS_NOTIFICATION_POLICY)
       != PackageManager.PERMISSION_GRANTED){
       showPermissionDialog(activity, context,
         Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
         "ACCESS NOTIFICATION POLICY Permission Required",
         "To see incoming calls and sound  please Enable notifications policy permission now?"
       )
     }
  }

  private fun canDisplayBubbles(context: Context): Boolean {

    log(" ======================== canDisplayBubbles POST_NOTIFICATIONS =====================${
    ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.POST_NOTIFICATIONS)
      != PackageManager.PERMISSION_GRANTED}")

    log(" ======================== canDisplayBubbles ACCESS_NOTIFICATION_POLICY =====================${
      ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.ACCESS_NOTIFICATION_POLICY)
        != PackageManager.PERMISSION_GRANTED}")

    log(" ======================== canDisplayBubbles BIND_NOTIFICATION_LISTENER_SERVICE =====================${
      ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
        != PackageManager.PERMISSION_GRANTED}")

    log(" ======================== canDisplayBubbles BIND_ACCESSIBILITY_SERVICE =====================${
      ContextCompat.checkSelfPermission(context,
        android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE)
        != PackageManager.PERMISSION_GRANTED}")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val bubblesEnabledGlobally: Boolean
      bubblesEnabledGlobally = try {
        Settings.Global.getInt(context.contentResolver, "notification_bubbles") == 1
      } catch (e: Settings.SettingNotFoundException) {
        // If we're not able to read the system setting, just assume the best case.
        log(" ======================== canDisplayBubbles SettingNotFoundException =====================${e.message}")

        true
      }
      val notificationManager: NotificationManager = context.getSystemService(
        NotificationManager::class.java
      )
      val bubblesEnabledNotificationManager: Boolean= if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        notificationManager.areBubblesEnabled()
      } else {
        notificationManager.areBubblesAllowed()
      }


      log(" ======================== canDisplayBubbles bubblesEnabledNotificationManager =====================${bubblesEnabledNotificationManager}")
      log(" ======================== canDisplayBubbles bubblesEnabledGlobally =====================${bubblesEnabledGlobally}")

      return bubblesEnabledGlobally && bubblesEnabledNotificationManager;
    } else {
      return true
    }
  }


  public fun checkWindowsDrawWithDialogPermission(activity: Activity, context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (Settings.canDrawOverlays(context)) {
        true
      } else {
        showPermissionDialog(activity, context,
          Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
          "SYSTEM ALERT WINDOW Permission Required",
          "To see incoming calls when the app close, please Enable action to manage overly permission now?"
        )
        false
      }
    } else {
      true
    }
  }


  private fun showPermissionDialog(
    activity: Activity,
    context: Context,
    intent: Intent,
    title: String,
    message: String
  ) {
    val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(activity)
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setNegativeButton("No", object : DialogInterface.OnClickListener {
      override fun onClick(dialogInterface: DialogInterface, i: Int) {
        dialogInterface.dismiss()
      }
    })
    builder.setPositiveButton("Yes", object : DialogInterface.OnClickListener {
      override fun onClick(dialogInterface: DialogInterface?, i: Int) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { intent } else {
          TODO("VERSION.SDK_INT < M")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent)
      }
    })
    val alertDialog: android.app.AlertDialog? = builder.create()
    alertDialog!!.setCancelable(false)
    alertDialog.show()
  }

  fun flagUpdateCurrent(mutable: Boolean): Int {
    return if (mutable) {
      if (Build.VERSION.SDK_INT >= 31) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
  }
}
