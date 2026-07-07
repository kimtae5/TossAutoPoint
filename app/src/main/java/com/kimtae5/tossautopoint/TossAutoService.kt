package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class TossAutoService : AccessibilityService() {

    private val APP_VERSION = "v1.6"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    
    private var isRunning = false
    private var swipeRunnable: Runnable? = null
    private var currentPackageName = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
    }

    private fun createFloatingView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xAA000000.toInt()) 
            visibility = View.GONE
        }
        windowManager?.addView(floatingView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // [버그 수정] 이벤트 패키지명이 null일 때 강제 종료하지 않고 안전하게 처리
        val pkg = event.packageName?.toString()
        
        if (pkg != null && pkg != currentPackageName) {
            currentPackageName = pkg
            
            // [버그 수정] 틱톡의 진짜 패키지명(aweme) 추가
            val isTarget = pkg.contains("toss") || 
                           pkg.contains("tiktok") || 
                           pkg.contains("musically") || 
                           pkg.contains("trill") || 
                           pkg.contains("aweme") || 
                           pkg.contains("cashwalk")

            val layout = floatingView ?: return

            // [버그 수정] 타겟 앱이면 켜고, 홈 화면이나 다른 앱이면 '무조건' 숨김
            if (isTarget) {
                layout.visibility = View.VISIBLE
                updateButtons(pkg)
            } else {
                layout.visibility = View.GONE
                stopSwipe() // 버튼이 숨겨지면 동작도 무조건 정지
            }
        }

        if (pkg != null && pkg.contains("cashwalk")) {
            val rootNode = rootInActiveWindow ?: return
            if (rootNode.findAccessibilityNodeInfosByText("적립 완료").isNotEmpty()) {
                clickSpecificRatio(0.8f, 0.37f)
            }
        }
    }

    private fun updateButtons(pkg: String) {
        val layout = floatingView ?: return
        layout.removeAllViews()
        
        val appName = when {
            pkg.contains("toss") -> "토스"
            pkg.contains("cashwalk") -> "캐시워크"
            else -> "틱톡"
        }

        val btn = Button(this).apply {
            text = if (isRunning) "■ $appName 정지 ($APP_VERSION)" else "▶ $appName 시작 ($APP_VERSION)"
            setOnClickListener {
                if (isRunning) stopSwipe() else startSwipe()
                updateButtons(pkg) 
            }
        }
        layout.addView(btn)
    }

    private fun startSwipe() {
        isRunning = true
        swipeRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    performSwipe()
                    handler.postDelayed(this, 3000) 
                }
            }
        }
        handler.post(swipeRunnable!!)
    }

    private fun stopSwipe() {
        isRunning = false
        swipeRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun performSwipe() {
        val dm = resources.displayMetrics
        val startX = dm.widthPixels / 2f
        val startY = dm.heightPixels * 0.7f 
        val endX = dm.widthPixels / 2f
        val endY = dm.heightPixels * 0.3f 

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
            
        // [버그 확인용] XML에 canPerformGestures="true"가 없으면 success가 false로 나옴
        val success = dispatchGesture(gesture, null, null)
        if (!success) {
            showToast("스와이프 실패! (XML 설정을 확인하세요)")
        }
    }

    private fun clickSpecificRatio(ratioX: Float, ratioY: Float) {
        val dm = resources.displayMetrics
        val path = Path().apply { moveTo(dm.widthPixels * ratioX, dm.heightPixels * ratioY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(), null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        stopSwipe()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onInterrupt() {}
}
