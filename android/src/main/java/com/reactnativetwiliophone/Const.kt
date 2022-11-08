package com.reactnativetwiliophone

import android.util.Log

fun log(msg: String) {
  Log.d("call_log", msg)
}

class StaticConst {
  companion object {
    var IS_RUNNING = false
  }
}

object Const {

  const val MODULE_NAME = "TwilioPhone"
  const val INCOMING_CALL_CHANNEL_NAME = "incoming_call_channel_name"
  const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel_id"

  const val MISSED_CALL_CHANNEL_NAME = "missed_call_channel_name"
  const val MISSED_CALL_CHANNEL_ID = "missed_call_channel_id"

  const val INCOMING_CALL_NOTIFICATION_ID = 58764854
  const val INCOMIN_CALL_REQUEST_CODE = 954
  const val MISSED_CALL_NOTIFICATION_ID = 58764854
  const val MISSED_CALL_REQUEST_CODE = 395

  const val EXTRA_NOTIFIER = "com.reactnativetwiliophone.notifier"
  const val CALLER_IMAGE = "callerImage"
  const val MESSAGE_CALL = "callerMsg"

  const val MAIN_ACTIVITY = ".MainActivity"
  const val SHARED_TARGET =  ".category.TEXT_SHARE_TARGET"
  const val SHORT_CUT_DATA_URL =   "https://com.iriscrm/call/"
  const val SHOTRT_CUT_ICON = "icon-missed-call.png"
  const val LIST_PARCABLE = "list_data_parcable"

  //prefs---------------------------------------

  const val PREFS_NAME = "call_prefs"
  const val ACTIVITY_LAUNCHER_NAME = "activity_launcher_name"

  //extras---------------------------------------
  const val ACTION = "action"
  const val REJECT = "reject"
  const val ANSWER = "answer"
  const val CALLER_NAME = "callerName"
  const val CALL_SID = "callSid"

  //events--------------------------------------
  const val REGISTER_SUCCESS = "RegistrationSuccess"
  const val REGISTER_FAILURE = "RegistrationFailure"
  const val CALL_INVITE = "CallInvite"
  const val UNREGISTER_SUCCESS = "UnregistrationSuccess"
  const val UNREGISTER_FAILURE = "UnregistrationFailure"
  const val CANCELLED_CALL_INVITE = "CancelledCallInvite"
  const val CALL_RINGING = "CallRinging"
  const val CALL_CONNECT_FAILURE = "CallConnectFailure"
  const val CALL_CONNECTED = "CallConnected"
  const val CALL_RE_CONNECTED = "CallReconnected"
  const val CALL_RE_CONNECTING = "CallReconnecting"
  const val CALL_CONNECTED_ERROR = "CallDisconnectedError"
  const val CALL_DISCONNECTED = "CallDisconnected"
  const val FROM = "from"
  const val CLIENT = "client:"
  const val ERROR_CODE = "errorCode"
  const val ERROR_MESSAGE = "errorMessage"
  var IS_LOGGER_ENABLED = true

}
