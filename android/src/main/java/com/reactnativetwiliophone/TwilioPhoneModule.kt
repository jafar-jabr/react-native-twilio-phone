package com.reactnativetwiliophone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.TrafficStats
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.reactnativetwiliophone.utils.ViewUtils
import com.twilio.voice.*


class TwilioPhoneModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  private var activeCallInvites = mutableMapOf<String, CallInvite>()
  private var activeCalls = mutableMapOf<String, Call>()
  private var callListener = callListener()

  private var audioManager: AudioManager =
    reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  override fun getName(): String {
    return Const.MODULE_NAME
  }

  @ReactMethod
  fun register(accessToken: String, deviceToken: String) {
    log("Registering")
    StaticConst.IS_RUNNING = true
    Voice.register(
      accessToken,
      Voice.RegistrationChannel.FCM,
      deviceToken,
      object : RegistrationListener {
        override fun onRegistered(accessToken: String, fcmToken: String) {
          log("Successfully registered FCM token")

          sendEvent(reactApplicationContext, Const.REGISTER_SUCCESS, null)
        }

        override fun onError(error: RegistrationException, accessToken: String, fcmToken: String) {
          log("Registration error: ${error.errorCode}  ${error.message}")

          val params = Arguments.createMap()
          params.putInt(Const.ERROR_CODE, error.errorCode)
          params.putString(Const.ERROR_MESSAGE, error.message)

          sendEvent(reactApplicationContext, Const.REGISTER_FAILURE, params)
        }
      })

    val acitivity = currentActivity
    if (acitivity != null) {
      log("on register twillio ======================== save Bakage name =====================${acitivity.packageName}")
      val editor: SharedPreferences.Editor = acitivity.getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE).edit()
      editor.putString(Const.PACKAGE_ID, acitivity.packageName)
      editor.putString(Const.ACTIVITY_LAUNCHER_NAME, acitivity.packageName+Const.MAIN_ACTIVITY)

      editor.apply()
    }

  }

  @ReactMethod
  fun requestWindowsDrawPermission() {
    val acitivity = currentActivity
    if (acitivity != null) {
      ViewUtils.checkWindowsDrawWithDialogPermission(acitivity, reactApplicationContext);
      //ViewUtils.checkDisplayBubblesPermission(acitivity, reactApplicationContext);
    }
  }

  fun showCallNotification(payload: ReadableMap) {
      log("show incomming call ------------------------------")
      TrafficStats.clearThreadStatsTag()
      ViewUtils.showCallView(reactApplicationContext.applicationContext, payload)
//      Handler().postDelayed({ ViewUtils.stopService(reactApplicationContext) }, 3000)
  }

  @ReactMethod
  fun hideCallNotification() {
    log("hide incomming call ------------------------------")
    TrafficStats.clearThreadStatsTag()
    ViewUtils.stopService(reactApplicationContext.applicationContext)
  }

  @ReactMethod
  fun handleMessage(payload: ReadableMap) {
    log("Handling message")

    val data = Arguments.toBundle(payload)

    if (data == null) {
      log("The message was not a valid Twilio Voice SDK payload")
      return
    }
    val valid = Voice.handleMessage(reactApplicationContext, data, object : MessageListener {
      override fun onCallInvite(callInvite: CallInvite) {
        log("Call invite received")
        activeCallInvites[callInvite.callSid] = callInvite

        val from = callInvite.from ?: ""
        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, callInvite.callSid)
        params.putString(Const.FROM,  from.replace(Const.CLIENT, ""))
        val pushData = Arguments.createMap()
        pushData.putString(Const.CALLER_NAME,  from.replace(Const.CLIENT, ""))
        pushData.putString(Const.CALL_SID, callInvite.callSid)
        showCallNotification(pushData)
        sendEvent(reactApplicationContext, Const.CALL_INVITE, params)
      }

      override fun onCancelledCallInvite(
        cancelledCallInvite: CancelledCallInvite,
        callException: CallException?
      ) {
        log("Cancelled call invite received")
        activeCallInvites.remove(cancelledCallInvite.callSid)
        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, cancelledCallInvite.callSid)
        sendEvent(reactApplicationContext, Const.CANCELLED_CALL_INVITE, params)
        hideCallNotification()
      }
    })

    if (!valid) {
      log("The message was not a valid Twilio Voice SDK payload")
    }
  }

  @ReactMethod
  fun acceptCallInvite(callSid: String) {
    log("Accepting call invite")

    if (activeCallInvites[callSid] == null) {
      log("No call invite to be accepted")
      return
    }

    val call = activeCallInvites[callSid]!!.accept(reactApplicationContext, callListener)

    activeCalls[callSid] = call
    activeCallInvites.remove(callSid)

    //new not in original
    val params = Arguments.createMap()
    params.putString(Const.CALL_SID, callSid)
    sendEvent(reactApplicationContext, Const.CALL_CONNECTED, params)

  }
  @ReactMethod
  fun rejectCallInvite(callSid: String) {
    log("Rejecting call invite")

    if (activeCallInvites[callSid] == null) {
      log("No call invite to be rejected")
      return
    }

    activeCallInvites[callSid]!!.reject(reactApplicationContext)

    activeCallInvites.remove(callSid)

  }

  @ReactMethod
  fun disconnectCall(callSid: String) {
    log("Disconnecting call")

    if (activeCalls[callSid] == null) {
      log("No call to be disconnected")
      return
    }

    activeCalls[callSid]!!.disconnect()
  }

  @ReactMethod
  fun endCall(callSid: String) {
    log("Ending call")

    if (activeCallInvites[callSid] != null) {
      activeCallInvites[callSid]!!.reject(reactApplicationContext)
      activeCallInvites.remove(callSid)
      return
    }

    if (activeCalls[callSid] != null) {
      activeCalls[callSid]!!.disconnect()
      return
    }

    log("Unknown sid to perform end-call action with")
  }

  @ReactMethod
  fun endAllCalls() {
    log("Ending all calls")
    activeCallInvites.forEach { (callSid, activeCallInvite) ->
      activeCallInvite.reject(reactApplicationContext)
      activeCallInvites.remove(callSid)
    }
    activeCalls.forEach { (callSid, activeCall) ->
      activeCall.disconnect()
      activeCalls.remove(callSid)
    }

    log("Unknown sid to perform end-call action with")
  }

  @ReactMethod
  fun toggleMuteCall(callSid: String, mute: Boolean) {
    log("Toggling mute call")

    val activeCall = activeCalls[callSid] ?: return

    activeCall.mute(mute)
  }

  @ReactMethod
  fun toggleHoldCall(callSid: String, hold: Boolean) {
    log("Toggling hold call")

    val activeCall = activeCalls[callSid] ?: return

    activeCall.hold(hold)
  }

  @ReactMethod
  fun toggleSpeaker(speakerOn: Boolean) {
    log("Toggling speaker")
    audioManager.isSpeakerphoneOn = speakerOn
  }

  @ReactMethod
  fun sendDigits(callSid: String, digits: String) {
    log("Sending digits")

    val activeCall = activeCalls[callSid] ?: return

    activeCall.sendDigits(digits)
  }

  @ReactMethod
  fun startCall(accessToken: String, params: ReadableMap) {
    log("Starting call")

    val connectParams = mutableMapOf<String, String>()

    for (entry in params.entryIterator) {
      connectParams[entry.key] = entry.value as String
    }

    val connectOptions = ConnectOptions.Builder(accessToken)
      .params(connectParams)
      .build()

    Voice.connect(reactApplicationContext, connectOptions, callListener)
  }

  @ReactMethod
  fun unregister(accessToken: String, deviceToken: String) {
    log("Unregistering")

    Voice.unregister(
      accessToken,
      Voice.RegistrationChannel.FCM,
      deviceToken,
      object : UnregistrationListener {
        override fun onUnregistered(accessToken: String, fcmToken: String) {
          log("Successfully unregistered FCM token")

          sendEvent(reactApplicationContext, Const.UNREGISTER_SUCCESS, null)
        }

        override fun onError(error: RegistrationException, accessToken: String, fcmToken: String) {
          log("Unregistration error: ${error.errorCode} ${error.message}")

          val params = Arguments.createMap()
          params.putInt(Const.ERROR_CODE, error.errorCode)
          params.putString(Const.ERROR_MESSAGE, error.message)

          sendEvent(reactApplicationContext, Const.UNREGISTER_FAILURE, params)
        }
      })
  }

  @ReactMethod
  fun checkPermissions(callback: Callback) {
    log("Checking permissions")

    val permissionsToRequest = mutableListOf<String>()

    val recordAudio = checkPermission(android.Manifest.permission.RECORD_AUDIO)
    if (recordAudio != "GRANTED") {
      permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
    }

    val callPhone = checkPermission(android.Manifest.permission.CALL_PHONE)
    if (callPhone != "GRANTED") {
      permissionsToRequest.add(android.Manifest.permission.CALL_PHONE)
    }

    if (permissionsToRequest.isNotEmpty()) {
      currentActivity?.let {
        ActivityCompat.requestPermissions(
          it,
          permissionsToRequest.toTypedArray(),
          1
        )
      }
    }

    val permissions = Arguments.createMap()
    permissions.putString("RECORD_AUDIO", recordAudio)
    permissions.putString("CALL_PHONE", callPhone)

    callback(permissions)
  }

  @ReactMethod
  fun addListener(eventName: String?) {
    log("addListener eventName= $eventName")

    // Set up any upstream listeners or background tasks as necessary
  }

  @ReactMethod
  fun removeListeners(count: Int?) {
    log("removeListeners")

    // Remove upstream listeners, stop unnecessary background tasks
  }

  private fun sendEvent(
    reactContext: ReactContext,
    eventName: String,
    params: WritableMap?
  ) {
    reactContext
      .getJSModule(RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private fun callListener(): Call.Listener {
    return object : Call.Listener {
      /*
       * This callback is emitted once before the Call.Listener.onConnected() callback when
       * the callee is being alerted of a Call. The behavior of this callback is determined by
       * the answerOnBridge flag provided in the Dial verb of your TwiML application
       * associated with this client. If the answerOnBridge flag is false, which is the
       * default, the Call.Listener.onConnected() callback will be emitted immediately after
       * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
       * call to emit the onConnected callback only after the call is answered.
       * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
       * twiML response contains a Say verb, then the call will emit the
       * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
       * raised, irrespective of the value of answerOnBridge being set to true or false
       */
      override fun onRinging(call: Call) {
        log("Call did start ringing")
        /*
         * When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge)
         * is enabled in the <Dial> TwiML verb, the caller will not hear the ringback while
         * the call is ringing and awaiting to be accepted on the callee's side. The application
         * can use the `SoundPoolManager` to play custom audio files between the
         * `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks.
         */

        activeCalls[call.sid!!] = call

        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)

        sendEvent(reactApplicationContext, Const.CALL_RINGING, params)
      }

      override fun onConnectFailure(call: Call, error: CallException) {
        log("Call failed to connect: ${error.errorCode}, ${error.message}")

        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)
        params.putInt(Const.ERROR_CODE, error.errorCode)
        params.putString(Const.ERROR_MESSAGE, error.message)

        sendEvent(reactApplicationContext, Const.CALL_CONNECT_FAILURE, params)
      }

      override fun onConnected(call: Call) {
        log("Call did connect")

        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)

        sendEvent(reactApplicationContext, Const.CALL_CONNECTED, params)
      }

      override fun onReconnecting(call: Call, error: CallException) {
        log("Call is reconnecting with error: ${error.errorCode}, ${error.message}")
        log("Unregistration error: ${error.errorCode} ${error.message}")


        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)
        params.putInt(Const.ERROR_CODE, error.errorCode)
        params.putString(Const.ERROR_MESSAGE, error.message)

        sendEvent(reactApplicationContext, Const.CALL_RE_CONNECTING, params)
      }

      override fun onReconnected(call: Call) {
        log("Call did reconnect")

        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)

        sendEvent(reactApplicationContext, Const.CALL_RE_CONNECTED, params)
      }

      override fun onDisconnected(call: Call, error: CallException?) {
        val params = Arguments.createMap()
        params.putString(Const.CALL_SID, call.sid)

        if (error != null) {
          log("Call disconnected with error: ${error.errorCode}${error.message}")

          params.putInt(Const.ERROR_CODE, error.errorCode)
          params.putString(Const.ERROR_MESSAGE, error.message)

          sendEvent(reactApplicationContext, Const.CALL_CONNECTED_ERROR, params)
        } else {
          log("Call disconnected")

          sendEvent(reactApplicationContext, Const.CALL_DISCONNECTED, params)
        }
      }
    }
  }

  private fun checkPermission(permission: String): String {
    val activity = currentActivity ?: return "UNKNOWN"

    return when (ContextCompat.checkSelfPermission(activity, permission)) {
      PackageManager.PERMISSION_GRANTED -> "GRANTED"
      PackageManager.PERMISSION_DENIED -> "DENIED"
      else -> {
        "UNKNOWN"
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  public  fun showMissedCallNotification(context:Context,shortCutId:String,msgCall:String) {

    val prefs: SharedPreferences = context.getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE)
    val callerName = prefs.getString(Const.CALLER_NAME, "irisCrm").toString() //"No name defined" is the default value.

    val notificationIntent = Intent(context, Class.forName(reactApplicationContext.packageName+Const.MAIN_ACTIVITY)::class.java)
    val pendingIntent: PendingIntent =
      PendingIntent.getActivity(context,  Const.MISSED_CALL_REQUEST_CODE, notificationIntent,flagCanelCurrent(mutable = true))
    val bubbleData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      NotificationCompat.BubbleMetadata.Builder(pendingIntent,
        IconCompat.createWithResource(context, R.drawable.ic_baseline_call_missed_24))
        .setDesiredHeight(600)
        .build()
    } else {
      null
    }
    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (notificationManager.getNotificationChannel(Const.MISSED_CALL_CHANNEL_ID)   == null) {
      val channel = NotificationChannel(
        Const.MISSED_CALL_CHANNEL_ID,
        Const.MISSED_CALL_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
      )
      channel.lightColor = Color.BLUE
      channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      notificationManager.createNotificationChannel(channel)
    }
    val notificationBuilder: NotificationCompat.Builder=
      NotificationCompat.Builder(context, Const.MISSED_CALL_CHANNEL_ID)
        .setContentIntent(pendingIntent)
        .setSmallIcon(R.drawable.ic_baseline_call_missed_24)
        .setContentTitle(msgCall)
        .setContentText(callerName)
        .setChannelId(Const.MISSED_CALL_CHANNEL_ID)
        .setTicker("Call_STATUS")
        .setSound(ringtoneUri)
        .setCategory(Notification.CATEGORY_MISSED_CALL)
        .setShortcutId("missed_$shortCutId")
        .setLocusId(LocusIdCompat("missed_${shortCutId.toString()}"))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setShowWhen(true)
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
      notificationBuilder.setBubbleMetadata(bubbleData)
      log("SETT bubble missed call notify ************************ success")

    }
    notificationBuilder.build()
    notificationManager.notify(0, notificationBuilder.build())

  }

   fun flagCanelCurrent(mutable: Boolean): Int {
    return if (mutable) {
      if (Build.VERSION.SDK_INT >= 31) {
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
      } else {
        PendingIntent.FLAG_CANCEL_CURRENT
      }
    } else {
      PendingIntent.FLAG_CANCEL_CURRENT
    }
  }
}
