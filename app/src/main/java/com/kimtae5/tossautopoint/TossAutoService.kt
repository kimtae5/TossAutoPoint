package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TossAutoService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return

        // 💡 현재 폰 화면에 뜬 창의 종류(이름)를 가로챕니다.
        val className = event.className?.toString() ?: ""

        // 💡 오직 'Dialog(대화상자)'나 'Popup(팝업)'이라는 단어가 포함된 팝업창 화면에서만 클릭을 작동시킵니다.
        if (className.contains("Dialog") || className.contains("Popup") || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 캐시워크 및 토스의 적립 관련 타격 단어 설정
            checkAndClick(rootNode, "받기")
            checkAndClick(rootNode, "상자")
            checkAndClick(rootNode, "동전")
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
