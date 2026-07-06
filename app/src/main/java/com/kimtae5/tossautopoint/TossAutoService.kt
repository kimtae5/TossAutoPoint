package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TossAutoService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRobotRunning = true
    private var currentPackageName = ""

    private var isTossSwiping = false
    private var isTiktokSwiping = false
    private var tossRunnable: Runnable? = null
    private var tiktokRunnable: Runnable? = null

    // 상단바 멀티 액션 버튼 신호를 가로챌 무전기(수신기)
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isRobotRunning && intent?.action != "ACTION_STOP_ROBOT") return

            when (intent?.action) {
                "ACTION_STOP_ROBOT" -> { 
                    isRobotRunning = false
                    stopTossSwipe()
                    stopTiktokSwipe()
                    showToast("🛑 [AI 안전장치] 전체 기능을 강제 종료합니다.")
                    stopForeground(true)
                }
                "ACTION_START_TOSS" -> { 
                    isTossSwiping = true
                    startTossSwipeLoop()
                    updateNotification("토스 스와이프 중", "toss")
                    showToast("▶ [토스] 아래->위 무한 스와이프 시작")
                }
                "ACTION_STOP_TOSS" -> { 
                    stopTossSwipe()
                    updateNotification("토스 대기 중", "toss")
                    showToast("■ [토스] 스와이프 일시 정지")
                }
                "ACTION_START_TIKTOK" -> { 
                    isTiktokSwiping = true
                    startTiktokSwipeLoop()
                    updateNotification("틱톡라이트 스와이프 중", "tiktok")
                    showToast("▶ [틱톡라이트] 30초 타이머 가동")
                }
                "ACTION_STOP_TIKTOK" -> { 
                    stopTiktokSwipe()
                    updateNotification("틱톡라이트 대기 중", "tiktok")
                    showToast("■ [틱톡라이트] 타이머 일시 정지")
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRobotRunning = true
        createNotificationChannel()
        updateNotification("대기 중", "none")

        val filter = IntentFilter().apply {
            addAction("ACTION_STOP_ROBOT")
            addAction("ACTION_START_TOSS")
            addAction("ACTION_STOP_TOSS")
            addAction("ACTION_START_TIKTOK")
            addAction("ACTION_STOP_TIKTOK")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        showToast("🤖 [자동화 로봇] 가동 준비 완료!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRobotRunning) return

        // [수정 핵심 1] 화면 정보(rootNode) 유무와 관계없이, 앱 변경 패키지명을 0순위로 추출합니다.
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.isNotEmpty() && packageName != currentPackageName) {
            currentPackageName = packageName
            onAppChanged(packageName)
        }

        // 이후 로직은 화면 정보가 온전히 존재할 때만 실행합니다.
        val rootNode = rootInActiveWindow ?: return

        // [수정 핵심 2] 캐시워크 팝업 감지 방식 변경 (세포 수 측정 폐기 -> 텍스트 직관 검색)
        if (currentPackageName.contains("cashwalk")) {
            // 화면 노드 전체를 뒤져서 캡처 사진에 있던 "적립 완료"라는 글자를 찾습니다.
            val targetNodes = rootNode.findAccessibilityNodeInfosByText("적립 완료")
            
            // 해당 글자가 하나라도 발견되면 팝업이 뜬 것으로 확정합니다.
            if (targetNodes.isNotEmpty()) {
                showToast("⚡ [캐시워크] 텍스트 스캔 완료! 즉시 타격")
                clickSpecificRatio(0.8f, 0.37f) 
            }
        }
    }

    private fun onAppChanged(packageName: String) {
        if (packageName.contains("toss")) {
            updateNotification("토스 감지됨", "toss")
        } else if (packageName.contains("tiktok") || packageName.contains("musically.go")) { 
            updateNotification("틱톡라이트 감지됨", "tiktok")
        } else if (packageName.contains("cashwalk")) {
            updateNotification("캐시워크 감지됨", "none")
            stopTossSwipe()
            stopTiktokSwipe()
        } else {
            updateNotification("일반 대기 중", "none")
            stopTossSwipe()
            stopTiktokSwipe()
        }
    }

    private fun startTossSwipeLoop() {
        tossRunnable = object : Runnable {
            override fun run() {
                if (isRobotRunning && isTossSwiping && currentPackageName.contains("toss")) {
                    swipeBelowToUp()
                    handler.postDelayed(this, 1800)
                }
            }
        }
        handler.post(tossRunnable!!)
    }

    private fun stopTossSwipe() {
        isTossSwiping = false
        tossRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startTiktokSwipeLoop() {
        tiktokRunnable = object : Runnable {
            override fun run() {
                if (isRobotRunning && isTiktokSwiping && (currentPackageName.contains("tiktok") || currentPackageName.contains("musically.go"))) {
                    swipeBelowToUp()
                    handler.postDelayed(this, 30000)
                }
            }
        }
        handler.postDelayed(tiktokRunnable!!, 500)
    }

    private fun stopTiktokSwipe() {
        isTiktokSwiping = false
        tiktokRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun clickSpecificRatio(ratioX: Float, ratioY: Float) {
        val displayMetrics = resources.displayMetrics
        val targetX = displayMetrics.widthPixels * ratioX
        val targetY = displayMetrics.heightPixels * ratioY

        val path = Path().apply { moveTo(targetX, targetY) }
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 40)
        val gestureDescription = GestureDescription.Builder().addStroke(strokeDescription).build()
        dispatchGesture(gestureDescription, null, null)
    }

    private fun swipeBelowToUp() {
        val displayMetrics = resources.displayMetrics
        val startX = displayMetrics.widthPixels / 2f
        val endX = displayMetrics.widthPixels / 2f
        val startY = displayMetrics.heightPixels * 0.8f
        val endY = displayMetrics.heightPixels * 0.2f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 280)
        val gestureDescription = GestureDescription.Builder().addStroke(strokeDescription).build()
        dispatchGesture(gestureDescription, null, null)
    }

    private fun updateNotification(statusText: String, appMode: String) {
        val stopIntent = Intent("ACTION_STOP_ROBOT")
        val stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "ROBOT_CHANNEL")
        } else {
            Notification.Builder(this)
        }

        builder.setContentTitle("🤖 AI 자동화 제어 센터 ($statusText)")
            .setContentText("오작동 발생 시 🚨강제 멈춤 버튼을 누르세요.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_delete, "🚨 강제 멈춤", stopPendingIntent)

        when (appMode) {
            "toss" -> {
                if (!isTossSwiping) {
                    val startToss = Intent("ACTION_START_TOSS")
                    val pStart = PendingIntent.getBroadcast(this, 1, startToss, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    builder.addAction(android.R.drawable.ic_media_play, "▶ 토스 시작", pStart)
                } else {
                    val stopToss = Intent("ACTION_STOP_TOSS")
                    val pStop = PendingIntent.getBroadcast(this, 2, stopToss, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    builder.addAction(android.R.drawable.ic_media_pause, "■ 토스 정지", pStop)
                }
            }
            "tiktok" -> {
                if (!isTiktokSwiping) {
                    val startTiktok = Intent("ACTION_START_TIKTOK")
                    val pStart = PendingIntent.getBroadcast(this, 3, startTiktok, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    builder.addAction(android.R.drawable.ic_media_play, "▶ 틱톡라이트 시작", pStart)
                } else {
                    val stopTiktok = Intent("ACTION_STOP_TIKTOK")
                    val pStop = PendingIntent.getBroadcast(this, 4, stopTiktok, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    builder.addAction(android.R.drawable.ic_media_pause, "■ 틱톡라이트 정지", pStop)
                }
            }
        }

        startForeground(1, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("ROBOT_CHANNEL", "로봇 제어 센터", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTossSwipe()
        stopTiktokSwipe()
        try { unregisterReceiver(commandReceiver) } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}
