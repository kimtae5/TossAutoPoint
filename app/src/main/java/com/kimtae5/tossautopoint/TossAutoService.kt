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

    // 각 앱별 독립 제어를 위한 변수 및 루틴 정의
    private var isTossSwiping = false
    private var isTiktokSwiping = false
    private var tossRunnable: Runnable? = null
    private var tiktokRunnable: Runnable? = null

    // 상단바 멀티 액션 버튼 신호를 가로챌 무전기(수신기)
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isRobotRunning && intent?.action != "ACTION_STOP_ROBOT") return

            when (intent?.action) {
                "ACTION_STOP_ROBOT" -> { // 마스터 브레이크
                    isRobotRunning = false
                    stopTossSwipe()
                    stopTiktokSwipe()
                    showToast("🛑 [AI 안전장치] 전체 기능을 강제 종료합니다.")
                    stopForeground(true)
                }
                "ACTION_START_TOSS" -> { // 토스 재생
                    isTossSwiping = true
                    startTossSwipeLoop()
                    updateNotification("토스 스와이프 중", "toss")
                    showToast("▶ [토스] 아래->위 무한 스와이프를 시작합니다.")
                }
                "ACTION_STOP_TOSS" -> { // 토스 정지
                    stopTossSwipe()
                    updateNotification("토스 대기 중", "toss")
                    showToast("■ [토스] 스와이프를 일시 정지합니다.")
                }
                "ACTION_START_TIKTOK" -> { // 틱톡 라이트 재생
                    isTiktokSwiping = true
                    startTiktokSwipeLoop()
                    updateNotification("틱톡라이트 스와이프 중", "tiktok")
                    showToast("▶ [틱톡라이트] 30초 타이머를 가동합니다.")
                }
                "ACTION_STOP_TIKTOK" -> { // 틱톡 라이트 정지
                    stopTiktokSwipe()
                    updateNotification("틱톡라이트 대기 중", "tiktok")
                    showToast("■ [틱톡라이트] 타이머를 일시 정지합니다.")
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

        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: ""

        if (packageName.isNotEmpty() && packageName != currentPackageName) {
            currentPackageName = packageName
            onAppChanged(packageName)
        }

        // ==========================================
        // ⭕ [캐시워크 모드] 팝업 감지 후 0초 즉시 정밀 타격
        // ==========================================
        if (currentPackageName.contains("cashwalk")) {
            val nodeCount = countNodes(rootNode)
            // 화면 세포(노드) 수가 50개 이하로 떨어지는 팝업 등장 순간 포착
            if (nodeCount in 1..50) {
                showToast("⚡ [캐시워크] 팝업 감지 즉시 타격! X자(0.8, 0.37) 클릭")
                // Delay 없이 즉시 지연 시간 0초 만에 인젝션 실행
                clickSpecificRatio(0.8f, 0.37f) 
            }
        }
    }

    // 스마트폰 켜진 앱이 바뀔 때 상단바 버튼 구성을 변경함
    private fun onAppChanged(packageName: String) {
        if (packageName.contains("toss")) {
            updateNotification("토스 감지됨", "toss")
        } else if (packageName.contains("tiktok") || packageName.contains("musically.go")) { 
            // 일반 틱톡 및 틱톡 라이트(musically.go) 모두 대응
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

    // ==========================================
    // ⭕ [토스 루틴] 제어 스위치에 따른 아래->위 무한 스와이프
    // ==========================================
    private fun startTossSwipeLoop() {
        tossRunnable = object : Runnable {
            override fun run() {
                if (isRobotRunning && isTossSwiping && currentPackageName.contains("toss")) {
                    swipeBelowToUp() // 아래에서 위로 스와이프 실행
                    handler.postDelayed(this, 1800) // 1.8초 간격으로 연속 스크롤 부드럽게 유지
                }
            }
        }
        handler.post(tossRunnable!!)
    }

    private fun stopTossSwipe() {
        isTossSwiping = false
        tossRunnable?.let { handler.removeCallbacks(it) }
    }

    // ==========================================
    // ⭕ [틱톡 라이트 루틴] 제어 스위치에 따른 30초 스와이프
    // ==========================================
    private fun startTiktokSwipeLoop() {
        tiktokRunnable = object : Runnable {
            override fun run() {
                if (isRobotRunning && isTiktokSwiping && (currentPackageName.contains("tiktok") || currentPackageName.contains("musically.go"))) {
                    swipeBelowToUp() // 아래에서 위로 스와이프 실행
                    handler.postDelayed(this, 30000) // 정밀한 30초 타이머 주기 적용
                }
            }
        }
        handler.postDelayed(tiktokRunnable!!, 500) // 첫 시작은 0.5초 뒤 가동
    }

    private fun stopTiktokSwipe() {
        isTiktokSwiping = false
        tiktokRunnable?.let { handler.removeCallbacks(it) }
    }

    // 📱 [X자 타격용] 전달받은 해상도 비례 좌표를 정밀 타격하는 제스처 엔진
    private fun clickSpecificRatio(ratioX: Float, ratioY: Float) {
        val displayMetrics = resources.displayMetrics
        val targetX = displayMetrics.widthPixels * ratioX
        val targetY = displayMetrics.heightPixels * ratioY

        val path = Path().apply { moveTo(targetX, targetY) }
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 40)
        val gestureDescription = GestureDescription.Builder().addStroke(strokeDescription).build()
        dispatchGesture(gestureDescription, null, null)
    }

    // 👆 [통합 스와이프] 손가락을 아래(80% 지점)에서 위(20% 지점)로 정밀하게 쓸어 올림
    private fun swipeBelowToUp() {
        val displayMetrics = resources.displayMetrics
        val startX = displayMetrics.widthPixels / 2f
        val endX = displayMetrics.widthPixels / 2f
        
        val startY = displayMetrics.heightPixels * 0.8f  // 화면 아래쪽
        val endY = displayMetrics.heightPixels * 0.2f    // 화면 위쪽

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 280) // 0.28초 동안 밀기
        val gestureDescription = GestureDescription.Builder().addStroke(strokeDescription).build()
        dispatchGesture(gestureDescription, null, null)
    }

    private fun countNodes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 1
        for (i in 0 until node.childCount) {
            count += countNodes(node.getChild(i))
        }
        return count
    }

    // 🚨 [상단바 멀티 컨트롤러] 현재 앱 상태에 맞춰 시작/정지 버튼을 실시간으로 조립하는 제어판
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

        // 💡 [에러 해결] 각 앱 상태별 ic_media_start 오타를 정식 명칭인 ic_media_play로 전면 수정
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
