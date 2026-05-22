package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TossAutoService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event.packageName?.toString() ?: ""

        // 1. 오직 [토스] 앱 화면이 활성화되어 있을 때만 작동
        if (packageName.contains("toss")) {
            checkAndClick(rootNode, "받기")
        } 
        
        // 2. 오직 [캐시워크] 앱 화면이 활성화되어 있을 때만 작동
        else if (packageName.contains("cashwalk")) {
            checkAndClick(rootNode, "상자")
            checkAndClick(rootNode, "동전")
        }
    }

    private fun checkAndClick(node: AccessibilityNodeInfo, targetText: String) {
        if (node.text != null && node.text.toString().contains(targetText)) {
            
            // 일반 메인 메뉴, 하단 탭바, 네비게이션 요소들의 오작동을 차단하는 ID 필터
            val viewId = node.viewIdResourceName ?: ""
            if (viewId.contains("tab") || viewId.contains("main") || viewId.contains("home") || viewId.contains("nav")) {
                return 
            }

            // 격리벽과 ID 필터를 모두 통과한 진짜 팝업창 버튼만 클릭 실행
            var parent: AccessibilityNodeInfo? = node
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                parent = parent.parent
            }
        }
        
        // 하위 화면 레이어 검색
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                checkAndClick(child, targetText)
            }
        }
    }

    override fun onInterrupt() {}
}
