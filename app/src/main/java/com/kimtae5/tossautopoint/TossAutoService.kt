package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class TossAutoService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            val currentApp = event.packageName?.toString() ?: ""

            when {
                // ----- [토스 앱 동작] -----
                currentApp.contains("viva.republica.toss") -> {
                    val tossTargets = listOf("받기", "포인트", "동전", "미션")
                    for (target in tossTargets) {
                        findAndClick(rootNode, target)
                    }
                }

                // ----- [캐시워크 앱 동작 (화면 1~4 완벽 대응)] -----
                currentApp.contains("com.cashwalk.cashwalk") -> {
                    val cashwalkTargets = listOf(
                        "동네 산책", "동네산책",               // 화면 1 대응
                        "산책하고 최대 50캐시 받기", "50캐시 받기", // 화면 2 대응
                        "적립 완료", "적립완료", "1캐시",       // 화면 4 팝업 감지용
                        "닫기", "취소", "close"                // 화면 4 X버튼 대체용 (숨은 텍스트)
                    )

                    // 일반 글자 기반 클릭 실행
                    for (target in cashwalkTargets) {
                        findAndClick(rootNode, target)
                    }

                    // 화면 4의 물리적인 'X' 자 모양 이미지 버튼 강제 탐색 및 클릭
                    findXButton(rootNode)
                }
            }
        }
    }

    // 텍스트 및 contentDescription 기반 클릭 함수
    private fun findAndClick(node: AccessibilityNodeInfo?, text: String) {
        if (node == null) return
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""

        if (nodeText.contains(text) || nodeDesc.contains(text)) {
            var runner: AccessibilityNodeInfo? = node
            while (runner != null) {
                if (runner.isClickable) {
                    runner.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return 
                }
                runner = runner.parent
            }
        }
        for (i in 0 until node.childCount) {
            findAndClick(node.getChild(i), text)
        }
    }

    // 화면 4의 글자 없는 X 아이콘 버튼을 클래스명(ImageView)으로 추적해 누르는 초필살기
    private fun findXButton(node: AccessibilityNodeInfo?) {
        if (node == null) return
        // 버튼이 클릭 가능하고 글자는 없는데 X자 역할을 하는 노드 서칭
        if (node.isClickable && node.className?.toString()?.contains("ImageView") == true) {
            val desc = node.contentDescription?.toString() ?: ""
            if (desc.isEmpty() || desc.contains("닫기") || desc.contains("close")) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        for (i in 0 until node.childCount) {
            findXButton(node.getChild(i))
        }
    }

    override fun onInterrupt() {}
}
