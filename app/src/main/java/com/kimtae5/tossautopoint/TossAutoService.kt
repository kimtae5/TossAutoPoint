package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TossAutoService : AccessibilityService() {

    // 마지막으로 찔러본(클릭한) 버튼의 내부 고유 ID를 임시 보관하는 변수
    private var lastClickedButtonId: String = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: ""

        // 오직 토스와 캐시워크 앱 내부에서만 AI 학습 센서 가동
        if (packageName.contains("toss") || packageName.contains("cashwalk")) {
            
            // 현재 유저가 보고 있는 스마트폰의 구체적인 '화면 이름' (예: TossNotificationActivity 등)
            val currentScreen = event.className?.toString() ?: "UnknownScreen"
            
            // 1. 뇌 메모장에서 이 화면의 과거 성공 정답 버튼 ID가 있는지 조회합니다.
            val savedBestButtonId = getSavedButton(currentScreen)

            if (savedBestButtonId.isNotEmpty()) {
                // 🧠 [기억 성공] 과거에 이 화면에서 정답이었던 버튼만 칼같이 클릭하고 조기 종료!
                clickById(rootNode, savedBestButtonId)
            } else {
                // 🔍 [기억 없음 / 탐색 모드] 화면을 분석해서 터치해보고 학습을 시작합니다.
                exploreAndFindReward(rootNode, currentScreen)
            }
        }
    }

    // 🔍 탐색 및 보상 학습 핵심 알고리즘
    private fun exploreAndFindReward(node: AccessibilityNodeInfo, currentScreen: String) {
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        // 🎁 [보상 체계] 아무거나 찌르다가 화면에 적립 관련 단어(성공 신호)가 포착되면 학습 성공!
        if (text.contains("적립") || text.contains("받기") || text.contains("상자") || text.contains("성공") || text.contains("원")) {
            if (lastClickedButtonId.isNotEmpty()) {
                // 방금 전 성공을 이끌어낸 버튼 ID를 이 화면의 '영구 정답'으로 뇌에 저장합니다.
                saveSuccessButton(currentScreen, lastClickedButtonId)
                lastClickedButtonId = "" // 초기화
                return
            }
        }

        // 일반 하단 메뉴바 등 오작동 유발 구역은 찌르기 대상에서 제외 (필터링)
        if (viewId.contains("tab") || viewId.contains("main") || viewId.contains("home") || viewId.contains("nav")) {
            return
        }

        // 클릭 가능한 버튼을 발견하면 실험삼아 찔러봅니다 (시행착오 탐색)
        if (node.isClickable && viewId.isNotEmpty()) {
            lastClickedButtonId = viewId // 내가 지금 뭘 찔렀는지 기록해둠
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }

        // 하위 화면 레이어 검색 트래버스
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                exploreAndFindReward(child, currentScreen)
            }
        }
    }

    // 고유 ID 명칭을 기반으로 정밀 타격 클릭하는 기능
    private fun clickById(node: AccessibilityNodeInfo, targetId: String) {
        if (node.viewIdResourceName == targetId && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                clickById(child, targetId)
            }
        }
    }

    // 💾 [뇌 기능 - 저장] 성공한 화면의 정답 버튼 ID를 폰 메모리에 영구 저장
    private fun saveSuccessButton(screenName: String, buttonId: String) {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString(screenName, buttonId)
            apply()
        }
    }

    // 📖 [뇌 기능 - 불러오기] 현재 화면의 정답 버튼 ID가 메모리에 있는지 조회
    private fun getSavedButton(screenName: String): String {
        val sharedPref = getSharedPreferences("AI_BRAIN_MEM", Context.MODE_PRIVATE)
        return sharedPref.getString(screenName, "") ?: ""
    }

    override fun onInterrupt() {}
}
