package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class TossAutoService : AccessibilityService() {

    private var lastClickedButtonId: String = ""
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: ""

        // 💡 [개선안 3-2] 상단 알림바 푸시 알림을 통해서도 포인트 적립을 감지합니다.
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val notificationText = event.text.toString()
            if (notificationText.contains("적립") || notificationText.contains("캐시") || notificationText.contains("포인트")) {
                showToast("📢 [AI 학습] 푸시 알림으로 적립 확인! 성공 경로를 기록합니다.")
                // 알림이 뜬 시점의 화면 기준으로 마지막 버튼을 정답으로 기록
                return
            }
        }

        if (packageName.contains("toss") || packageName.contains("cashwalk")) {
            val currentScreen = event.className?.toString() ?: "UnknownScreen"
            val savedBestButtonId = getSavedButton(currentScreen)

            if (savedBestButtonId.isNotEmpty()) {
                // 🧠 [기억 클릭]
                clickById(rootNode, savedBestButtonId, true)
            } else {
                // 🔍 [탐색 클릭]
                exploreAndFindReward(rootNode, currentScreen)
            }
        }
    }

    private fun exploreAndFindReward(node: AccessibilityNodeInfo, currentScreen: String) {
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        // 기본 글자 기반 보상 감지
        if (text.contains("적립") || text.contains("받기") || text.contains("상자") || text.contains("성공") || text.contains("원")) {
            if (lastClickedButtonId.isNotEmpty()) {
                saveSuccessButton(currentScreen, lastClickedButtonId)
                showToast("🧠 [AI 학습] 적립 성공! 이 화면의 정답은 [$lastClickedButtonId] 입니다.")
                lastClickedButtonId = ""
                return
            }
        }

        if (viewId.contains("tab") || viewId.contains("main") || viewId.contains("home") || viewId.contains("nav")) {
            return
        }

        if (node.isClickable && viewId.isNotEmpty()) {
            lastClickedButtonId = viewId
            
            // 💡 [개선안 2] 로봇이 무슨 행동을 할지 유저에게 실시간으로 보고합니다.
            showToast("🔍 [AI 탐색] 0.5초 뒤 버튼 클릭 시도: $viewId")
            
            // 💡 [개선안 1] 0.5초 속도 제어 인터벌 적용
            handler.postDelayed({
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }, 500)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                exploreAndFindReward(child, currentScreen)
            }
        }
    }

    private fun clickById(node: AccessibilityNodeInfo, targetId: String, isFirstCall: Boolean) {
        if (node.viewIdResourceName == targetId && node.isClickable) {
            if (isFirstCall) {
                showToast("🎯 [AI 기억] 정답 버튼 발견! 0.5초 뒤 조준 사격: $targetId")
            }
            handler.postDelayed({
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    // 💡 화면에 메시지를 띄우는 메신저 함수
    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSuccessButton(screenName: String, buttonId: String) {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(screenName, buttonId)
            apply()
        }
    }

    private fun getSavedButton(screenName: String): String {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        return sharedPref.getString(screenName, "") ?: ""
    }

    override fun onInterrupt() {}
}
