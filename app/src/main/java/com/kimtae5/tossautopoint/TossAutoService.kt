package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TossAutoService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // 💡 현재 화면을 실행 중인 앱의 고유 패키지명을 가져옵니다.
        val packageName = event.packageName?.toString() ?: ""

        // 💡 오직 토스(toss) 앱이거나 캐시워크(cashwalk) 앱이 켜져 있을 때만 클릭 센서를 가동합니다.
        if (packageName.contains("toss") || packageName.contains("cashwalk")) {
            checkAndClick(rootNode, "받기")
            checkAndClick(rootNode, "상자")
            checkAndClick(rootNode, "동전")
            checkAndClick(rootNode, "확인")
        }
    }

    private fun checkAndClick(node: AccessibilityNodeInfo, targetText: String) {
        if (node.text != null && node.text.toString().contains(targetText)) {
            var parent: AccessibilityNodeInfo? = node
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                parent = parent.parent
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                checkAndClick(child, targetText)
            }
        }
    }

    override fun onInterrupt() {}
}
