package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TossAutoService : AccessibilityService() {

    private var lastClickedButtonId: String = ""
    private val handler = Handler(Looper.getMainLooper())
    
    // 🛑 [안전장치] 로봇의 가동 상태를 제어하는 핵심 스위치 (false가 되면 올스톱)
    private var isRobotRunning: Boolean = true

    // 비상정지 신호를 수신할 무전기(브로드캐스트 리시버)
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_STOP_ROBOT") {
                isRobotRunning = false
                showToast("🛑 [AI 안전장치] 유저가 강제 정지함! 로봇이 즉시 멈춥니다.")
                stopForeground(true) // 알림창 내리기
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRobotRunning = true
        
        // 폰 상단바에 "강제 멈춤" 버튼이 담긴 상시 알림창을 띄웁니다.
        createNotificationChannel()
        startRobotNotification()

        // 강제 정지 신호 무전기 주소 등록
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, IntentFilter("ACTION_STOP_ROBOT"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, IntentFilter("ACTION_STOP_ROBOT"))
        }

        showToast("🤖 [AI 로봇] 가동 시작! 토스/캐시워크 감시를 시작합니다.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 🛑 안전장치가 꺼졌다면 어떤 신호가 와도 절대 일하지 않고 무시합니다.
        if (!isRobotRunning) return

        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: ""

        // 💡 [먹통 해결] 특정 화면 전환뿐만 아니라, 화면 안의 내용물이 조금이라도 꿈틀거리면 무조건 작동합니다.
        if (packageName.contains("toss") || packageName.contains("cashwalk")) {
            // 현재 화면의 전체 레이아웃 상태를 고유 키값으로 만듭니다.
            val currentLayoutKey = "${packageName}_${rootNode.childCount}"
            val savedBestButtonId = getSavedButton(currentLayoutKey)

            if (savedBestButtonId.isNotEmpty()) {
                clickById(rootNode, savedBestButtonId, true)
            } else {
                exploreAndFindReward(rootNode, currentLayoutKey)
            }
        }
    }

    private fun exploreAndFindReward(node: AccessibilityNodeInfo, layoutKey: String) {
        if (!isRobotRunning) return

        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        // 적립 감지 보상 엔진
        if (text.contains("적립") || text.contains("받기") || text.contains("상자") || text.contains("성공") || text.contains("원")) {
            if (lastClickedButtonId.isNotEmpty()) {
                saveSuccessButton(layoutKey, lastClickedButtonId)
                showToast("🧠 [AI 학습] 적립 성공 기억! 정답 코드: [$lastClickedButtonId]")
                lastClickedButtonId = ""
                return
            }
        }

        if (viewId.contains("tab") || viewId.contains("main") || viewId.contains("home") || viewId.contains("nav")) {
            return
        }

        if (node.isClickable && viewId.isNotEmpty()) {
            lastClickedButtonId = viewId
            showToast("🔍 [AI 탐색] 0.5초 뒤 터치 시도: $viewId")
            
            handler.postDelayed({
                if (isRobotRunning) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }, 500)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                exploreAndFindReward(child, layoutKey)
            }
        }
    }

    private fun clickById(node: AccessibilityNodeInfo, targetId: String, isFirstCall: Boolean) {
        if (!isRobotRunning) return
        
        if (node.viewIdResourceName == targetId && node.isClickable) {
            if (isFirstCall) {
                showToast("🎯 [AI 기억] 정답 저격 사격! (0.5초 뒤 터치)")
            }
            handler.postDelayed({
                if (isRobotRunning) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }, 500)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                clickById(child, targetId, false)
            }
        }
    }

    // 🛑 [상단바 비상 브레이크 시스템] 상단바에 강제정지 버튼을 만드는 함수들
    private fun startRobotNotification() {
        val stopIntent = Intent("ACTION_STOP_ROBOT")
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "ROBOT_CHANNEL")
                .setContentTitle("🤖 토스캐시자동화 로봇 작동 중")
                .setContentText("로봇이 스스로 적립 경로를 학습하고 있습니다.")
                .setSmallIcon(android.R.drawable.ic_media_play)
                // 🛑 비상정지 액션 버튼 추가
                .addAction(android.R.drawable.ic_delete, "🚨 강제 멈춤 (종료)", stopPendingIntent)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("🤖 토스캐시자동화 로봇 작동 중")
                .setContentText("로봇이 스스로 적립 경로를 학습하고 있습니다.")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(android.R.drawable.ic_delete, "🚨 강제 멈춤 (종료)", stopPendingIntent)
                .build()
        }

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ROBOT_CHANNEL", "로봇 제어 센터", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
    }

    private fun saveSuccessButton(key: String, buttonId: String) {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        with(sharedPref.edit()) { putString(key, buttonId); apply() }
    }

    private fun getSavedButton(key: String): String {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        return sharedPref.getString(key, "") ?: ""
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stopReceiver) } catch (e: Exception) {}
    }

    override fun onInterrupt() {}
}
