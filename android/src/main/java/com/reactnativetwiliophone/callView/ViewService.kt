package com.reactnativetwiliophone.callView

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.content.*
import android.content.pm.ShortcutManager
import android.graphics.*
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.facebook.react.HeadlessJsTaskService
import com.reactnativetwiliophone.Actions
import com.reactnativetwiliophone.Const
import com.reactnativetwiliophone.R
import com.reactnativetwiliophone.boradcastReceivers.NotificationsHeadlessReceiver
import com.reactnativetwiliophone.log
import java.util.*


class ViewService : ViewServiceConfig(), Logger by LoggerImpl() {

  var mNotificationManager: NotificationManager? = null
  var mBinder: IBinder = LocalBinder()
  private var mIntent: Intent? = null
  private var extras: Bundle? = null
  var mBound: Boolean = false
  var isStarted: Boolean? = false
  private var isServiceStarted = false
  private var mStartId = 0
  var binder: Binder? = null
  var shortCutId: String? = null
  var callerId: String? = null
  var callerName: String? = null
  var msgCall: String? = null
  private var ringtone: Ringtone? = null

  companion object {
    @JvmStatic
    lateinit var instance: ViewService
  }

  init {
    instance = this
  }

  fun doUnbindService() {
    log("ViewService ====================== doUnbindService  $mBound")
    if (mBound) {
      unbindService(connection)
      mBound = false
    }
  }

  val connection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(
      className: ComponentName,
      service: IBinder
    ) {
      binder = service as LocalBinder
      mBound = true
      log("ViewService ====================== onServiceConnected  $mBound")

    }

    override fun onServiceDisconnected(arg0: ComponentName) {
      mBound = false
      log("ViewService ====================== onServiceDisconnected  $mBound")

    }
  }

  inner class LocalBinder : Binder() {
    fun getService(): ViewService = this@ViewService
  }

  override fun onBind(intent: Intent?): IBinder? {
    stopForeground(true)
    log("ViewService ====================== onBind  service")
    return mBinder
  }

  override fun onRebind(intent: Intent?) {
    log("ViewService ====================== onRebind  service")
    stopForeground(true)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    log("ViewService ====================== onUnbind  service ")
    stopForeground(true)
    return true
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun startViewForeground() {

      var ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

      if (ringtoneUri == null) {
        ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (ringtoneUri == null) {
          ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
      }
      ringtone = RingtoneManager.getRingtone(this, ringtoneUri)
      ringtone?.play()


      val channelId = if (isHigherThanAndroid8()) {
        createNotificationChannel(
          Const.INCOMING_CALL_CHANNEL_ID,
          Const.INCOMING_CALL_CHANNEL_NAME)
      } else {
        // In earlier version, channel ID is not used
        // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
        ""
      }
      val notification = setupNotificationBuilder(channelId)
    /*  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try{
       // notification.headsUpContentView =RemoteViews(packageName, R.layout.call_view)
        }catch (e:Exception){
          log("======================== startViewForeground headsUpContentView ERRROR ${e.toString()}")
        }
      }*/
      log("startViewForeground")
      startForeground(Const.INCOMING_CALL_NOTIFICATION_ID, notification)
      this.isStarted = true

  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

    val prefs: SharedPreferences = this.getSharedPreferences(Const.PREFS_NAME, MODE_PRIVATE)

    if (intent != null) {
      log("onStartCommand action=" + intent.action)
      val action = intent.action
      mIntent = intent
      mStartId = startId
      extras = intent.extras
      log("using an intent with action $action")
      // if (action === Actions.STOP.name) {
      //  stopSelf()
      //  stopSelfResult(startId)
      // } else {
      if (isDrawOverlaysPermissionGranted()) {
        setupViewAppearance()
        if (isHigherThanAndroid8()) {
          if (this.isStarted == false) {
            if(extras!=null) {
              callerId = extras!!.getString(Const.CALL_SID)
              callerName= extras!!.getString(Const.CALLER_NAME)
              msgCall= extras!!.getString(Const.MESSAGE_CALL)
              val editor: SharedPreferences.Editor=prefs.edit()
              editor.putString(Const.CALLER_NAME, callerName)
              shortCutId = "contact_"+callerId
              log("onCreate callerName $callerName")
              log("onCreate callerId $callerId")
              log("onCreate shortCutId $shortCutId")
              log("onCreate msgCall $msgCall")

              startViewForeground()
            }
          }
        }

      } else throw PermissionDeniedException()
      //  }
    }
    return START_STICKY;
  }

  override fun onCreate() {
    super.onCreate()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      if(extras!=null) {
        callerId = extras!!.getString(Const.CALL_SID)
        callerName= extras!!.getString(Const.CALLER_NAME)
        msgCall= extras!!.getString(Const.MESSAGE_CALL)

        shortCutId = "contact_"+callerId
        log("onCreate callerName $callerName")
        log("onCreate callerId $callerId")
        log("onCreate shortCutId $shortCutId")
        log("onCreate msgCall $msgCall")

        startViewForeground()
      }
    }
  }

  override fun setupCallView(action: CallView.Action): CallView.Builder? {
    val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    val layout = inflater.inflate(R.layout.call_view, null)

    val textView: TextView = layout.findViewById(R.id.callerNameV)
    if (extras != null) {
      textView.text = extras!!.getString(Const.CALLER_NAME)
    }
    val imgDeclineBtn: ImageButton = layout.findViewById(R.id.imgDecline)
    val imgAnswerBtn: ImageButton = layout.findViewById(R.id.imgAnswer)
    imgAnswerBtn.setOnClickListener { v: View? ->
      extras?.let {
        handleIntent(
          it,
          Const.ANSWER
        )
      }
    }

    imgDeclineBtn.setOnClickListener { v: View? ->
      extras?.let {
        handleIntent(
          it,
          Const.REJECT
        )
      }
    }

//    if(isDeviceLocked(this)){
//      log("************************ NOTIFY ===================== isPhoneLocked : "+isDeviceLocked(this).toString())
//      mNotificationManager?.notify(0, setupNotificationBuilder(Const.INCOMING_CALL_CHANNEL_ID))
//      //return null
//    }
    log("return view ************************ NOTIFY ===================== isPhoneLocked : "+isDeviceLocked(this).toString())

    return CallView.Builder()
        .with(this)
        .setCallView(layout)
        .setDimAmount(0.8f)
        .addCallViewListener(object : CallView.Action {
          override fun popCallView() {
            popCallView()
          }

          override fun onOpenCallView() {
            isServiceStarted = true
          }
        })

  }

  fun isDeviceLocked(context: Context): Boolean {
    var isLocked = false

    // First we check the locked state
    val keyguardManager = context.getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    val inKeyguardRestrictedInputMode = keyguardManager.inKeyguardRestrictedInputMode()
    isLocked = if (inKeyguardRestrictedInputMode) {
      true
    } else {
      // If password is not set in the settings, the inKeyguardRestrictedInputMode() returns false,
      // so we need to check if screen on for this case
      val powerManager: PowerManager = context.getSystemService(POWER_SERVICE) as PowerManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
        !powerManager.isInteractive()
      } else {
        !powerManager.isScreenOn()
      }
    }
    log(String.format("Now device is %s.", if (isLocked) "locked" else "unlocked"))
    return isLocked
  }

  //=======================================================================================

  fun setupNotificationBuilder(channelId: String): Notification {

    val packageName: String = this.getApplicationContext().getPackageName()
    val targetIntent: Intent =
      this.getPackageManager().getLaunchIntentForPackage(packageName)!!.cloneFilter()


    val pendingIntent: PendingIntent =
      PendingIntent.getActivity(this,  Const.INCOMIN_CALL_REQUEST_CODE, targetIntent,flagUpdateCurrent(mutable = true))
    val category = "$packageName.category.IMG_SHARE_TARGET"

    val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val bubbleData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      BubbleMetadata.Builder(shortCutId.toString())
        .setDesiredHeight(600)
        .setAutoExpandBubble(true)
        .setSuppressNotification(true)
        .build()
    } else {
      null
    }

    val fullScreenPendingIntent = PendingIntent.getActivity(this, Const.INCOMIN_CALL_REQUEST_CODE,
      targetIntent, flagUpdateCurrent(mutable = true))

    createAppShortcut(targetIntent)
    val notificationBuilder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Builder(this, channelId)
          .setContentIntent(pendingIntent)
          .setFullScreenIntent(fullScreenPendingIntent,true)
          .setOngoing(true)
          .setSmallIcon(R.drawable.ic_baseline_wifi_calling_3_24)
          .setContentTitle(callerName)
          .setContentText(msgCall)
          .setChannelId(Const.INCOMING_CALL_CHANNEL_ID)
          .setTicker("Call_STATUS")
          .setShortcutId(shortCutId)
          .setLocusId(LocusIdCompat(shortCutId.toString()))
          .setPriority(PRIORITY_HIGH)
          .setCategory(Notification.CATEGORY_CALL)
          .setOnlyAlertOnce(true)
          .setShowWhen(true)
      } else {
        Builder(this, channelId)
          .setContentIntent(pendingIntent)
          .setFullScreenIntent(fullScreenPendingIntent,true)
          .setOngoing(true)
          .setSmallIcon(R.drawable.ic_baseline_wifi_calling_3_24)
          .setContentTitle(callerName)
          .setContentText(msgCall)
          .setChannelId(Const.INCOMING_CALL_CHANNEL_ID)
          .setTicker("Call_STATUS")
          .setShortcutId(shortCutId)
          .setLocusId(LocusIdCompat(shortCutId.toString()))
          .setPriority(PRIORITY_HIGH)
          .setOnlyAlertOnce(true)
          .setShowWhen(true)
      }

//    if(isDeviceLocked(this)){
//      notificationBuilder.setSound(ringtoneUri)
//    }
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
      notificationBuilder.setBubbleMetadata(bubbleData)
      log("SETT bubble call notify ************************ success")

    }
    log("return notificationBuilder ************************ NOTIFY ===================== isPhoneLocked : "+isDeviceLocked(this).toString())

    return notificationBuilder
      .build()

  }

  fun createAppShortcut(intent: Intent?) {
    val shortcut = ShortcutInfoCompat.Builder(this, shortCutId.toString())
      .setShortLabel(callerName.toString())
      .setLongLabel(msgCall.toString())
      .setLongLived(true)
      .setIcon(IconCompat.createWithResource(this, R.drawable.ic_baseline_call_missed_24))
      .setIntent(intent!!.setAction(Intent.ACTION_MAIN))
      .build()

    ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    log("======================== Shortcut ===================== created ")

  }

  public fun deleteShortCut() {
    val shortcutIntent = Intent(Intent.ACTION_MAIN)
    shortcutIntent.setClassName(this.getApplicationContext().getPackageName(), Const.MAIN_ACTIVITY)
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val removeIntent = Intent()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      removeIntent.putExtra(Intent.EXTRA_SHORTCUT_ID, shortCutId)
    }
    removeIntent.putExtra("duplicate", false)
    removeIntent.action = "com.android.launcher.action.UNINSTALL_SHORTCUT"
    sendBroadcast(removeIntent)
    removeShortcut()
    removeAllShortcuts()
  }




  fun removeShortcut() {
    log("======================== removeShortcut===================== id $shortCutId")

    if (Build.VERSION.SDK_INT < 25) return
    val shortcutManager: ShortcutManager =getSystemService(ShortcutManager::class.java)
    shortcutManager.removeDynamicShortcuts(Arrays.asList(shortCutId))
    if (Build.VERSION.SDK_INT < 30) return
    shortcutManager.removeLongLivedShortcuts(Arrays.asList(shortCutId))

  }

  fun removeAllShortcuts() {
    log("======================== removeAllShortcuts=====================")

    if (Build.VERSION.SDK_INT < 25) return
    val shortcutManager: ShortcutManager = getSystemService(ShortcutManager::class.java)
    shortcutManager.removeAllDynamicShortcuts()
  }



  public fun flagUpdateCurrent(mutable: Boolean): Int {
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


  override fun onDestroy() {
    log("====================== onDestroy  ViewService")
    stopService(this)
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopService(this)
    log("======================== onTaskRemoved =====================")
    super.onTaskRemoved(rootIntent)
  }


  fun stopService(context: Context) {
    try {

      ringtone?.stop()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ringtone?.isLooping=false
      }
     /* onMainThread {
        deleteShortCut()
        removeAllShortcuts()
        removeShortcut()
      }*/
      doUnbindService()
      log("stop service 1")

      // Thread.currentThread().interrupt();
      // mNotificationManager!!.cancel(Const.NOTIFICATION_ID)
      //stopForeground(STOP_FOREGROUND_REMOVE)
      // stopForeground(true)
      log("stop service 2")
      //stopSelfResult(mStartId);
      tryStopService();
      log("stop service 3")
      val closeIntent = Intent(context, ViewService::class.java)
      log("stop service 5")
      stopService(closeIntent)
      log("success stop service")
      deleteShortCut()
    } catch (e: Exception) {
      log("Error stop service A${e.toString()}")

      tryStopService()
    }
    isServiceStarted = false
    //setServiceState(this, ServiceState.STOPPED)

  }

  fun handleIntent(extras: Bundle, type: String?) {
    try {
      val pendingFlags: Int
      pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }

      val packageName: String = this.getApplicationContext().getPackageName()
      val appIntent: Intent =
        this.getPackageManager().getLaunchIntentForPackage(packageName)!!.cloneFilter()

      appIntent.action = Actions.STOP.name
      val contentIntent = PendingIntent.getActivity(
        this,
        0,
        appIntent,
        pendingFlags
      )
      contentIntent.send()
      val headlessIntent = Intent(
        this,
        NotificationsHeadlessReceiver::class.java
      )
      extras.putString(Const.ACTION, type)
      headlessIntent.putExtra(Const.EXTRA_NOTIFIER, extras)
      val name = startService(headlessIntent)
      if (name != null) {
        HeadlessJsTaskService.acquireWakeLockNow(this)
      }
      log("finish service A")
      stopService(this)
    } catch (e: java.lang.Exception) {
      log("Exception =$e")
      stopService(Intent(this, ViewService::class.java))
    }
  }

  @Deprecated("this function may not work properly", ReplaceWith("true"))
  open fun setLoggerEnabled(): Boolean = true

  // helper --------------------------------------------------------------------------------------

  @SuppressLint("WrongConstant")
  @TargetApi(Build.VERSION_CODES.O)
  @RequiresApi(Build.VERSION_CODES.O)
  private fun createNotificationChannel(
    channelId: String,
    channelName: String
  ): String {
    mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (mNotificationManager?.getNotificationChannel(channelId)   == null) {
      val channel = NotificationChannel(
        channelId,
        channelName, NotificationManager.IMPORTANCE_HIGH
      )
      channel.lightColor = Color.BLUE
      channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      mNotificationManager!!.createNotificationChannel(channel)
    }
    return channelId
  }


  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
  private fun isHigherThanAndroid8() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

  fun cancelPushNotification(context: Context) {
    val notificationManager = NotificationManagerCompat.from(context)
    notificationManager.cancel(Const.INCOMING_CALL_NOTIFICATION_ID)
  }
}
