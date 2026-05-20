package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.GestureDescription
import android.util.Log

class TossAutoService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            val rootNode = rootInActiveWindow ?: return
            val currentApp = event.packageName?.toString() ?: ""
            
            when {
                currentApp.contains("viva.republica.toss") -> {
                    val tossTargets = listOf("받기", "포인트", "동전", "미션")
                    for (target in tossTargets) {
                        findAndClick(rootNode, target)
                    }
                }

                currentApp.contains("com.cashwalk.cashwalk") -> {
                    val cashwalkTargets = listOf(
                        "동네 산책", "동네산책",
                        "산책하고 최대 50캐시 받기", "50캐시 받기",
                        "적립 완료", "적립완료", "1캐시",
                        "닫기", "취소", "close"
                    )
                    
                    for (target in cashwalkTargets) {
                        findAndClick(rootNode, target)
                    }

                    findXButton(rootNode)
                }
            }
        }
    }

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

    private fun findXButton(node: AccessibilityNodeInfo?) {
        if (node == null) return
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
