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

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    
    // 동작 상태 변수
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
        val pkg = event.packageName?.toString() ?: ""
        
        // 1. 타겟 앱 체크 (토스, 틱톡 등)
        val isTarget = pkg.contains("toss") || pkg.contains("tiktok") || pkg.contains("musically")
        
        // 2. 패키지가 바뀌었을 때 UI 갱신
        if (pkg != currentPackageName) {
            currentPackageName = pkg
            
            val layout = floatingView ?: return
            if (isTarget) {
                layout.visibility = View.VISIBLE
                updateButtons(pkg)
            } else {
                layout.visibility = View.GONE
                stopSwipe() // 다른 앱으로 나가면 자동 정지
            }
        }
    }

    private fun updateButtons(pkg: String) {
        val layout = floatingView ?: return
        layout.removeAllViews()
        
        val btn = Button(this).apply {
            text = if (isRunning) "■ 정지" else "▶ 시작"
            setOnClickListener {
                if (isRunning) stopSwipe() else startSwipe()
                updateButtons(pkg) // 버튼 텍스트 바로 갱신
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
                    handler.postDelayed(this, 2000) // 2초 간격
                }
            }
        }
        handler.post(swipeRunnable!!)
        showToast("자동 스와이프 시작")
    }

    private fun stopSwipe() {
        isRunning = false
        swipeRunnable?.let { handler.removeCallbacks(it) }
        showToast("자동 스와이프 정지")
    }

    private fun performSwipe() {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.8f)
            lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.2f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        stopSwipe()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onInterrupt() {}
}
