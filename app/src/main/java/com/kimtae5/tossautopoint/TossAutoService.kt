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
    private var isTossSwiping = false
    private var isTiktokSwiping = false
    private var tossRunnable: Runnable? = null
    private var tiktokRunnable: Runnable? = null
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
            visibility = View.GONE // 처음엔 숨김 상태로 시작
        }
        windowManager?.addView(floatingView, params)
    }

    private fun updateFloatingButtons(packageName: String) {
        val layout = floatingView ?: return
        
        // [핵심 로직] 타겟 앱인지 확인
        val isTargetApp = packageName.contains("toss") || 
                          packageName.contains("tiktok") || 
                          packageName.contains("musically.go") || 
                          packageName.contains("cashwalk")

        // 앱이 타겟 앱이면 보이고, 아니면 숨김
        layout.visibility = if (isTargetApp) View.VISIBLE else View.GONE
        
        if (!isTargetApp) return // 타겟 앱이 아니면 버튼을 그리지 않음

        layout.removeAllViews()

        if (packageName.contains("toss")) {
            addButton(layout, if (isTossSwiping) "■ 토스 정지" else "▶ 토스 시작") {
                if (isTossSwiping) stopTossSwipe() else startTossSwipeLoop()
                updateFloatingButtons(packageName)
            }
        } else if (packageName.contains("tiktok") || packageName.contains("musically.go")) {
            addButton(layout, if (isTiktokSwiping) "■ 틱톡 정지" else "▶ 틱톡 시작") {
                if (isTiktokSwiping) stopTiktokSwipe() else startTiktokSwipeLoop()
                updateFloatingButtons(packageName)
            }
        } else if (packageName.contains("cashwalk")) {
            addButton(layout, "캐시워크 활성화") { showToast("팝업 감지 대기 중입니다.") }
        }
    }

    private fun addButton(layout: LinearLayout, text: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            this.text = text
            this.setOnClickListener { onClick() }
        }
        layout.addView(btn)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.isNotEmpty() && packageName != currentPackageName) {
            currentPackageName = packageName
            updateFloatingButtons(packageName)
        }

        if (packageName.contains("cashwalk")) {
            val rootNode = rootInActiveWindow ?: return
            if (rootNode.findAccessibilityNodeInfosByText("적립 완료").isNotEmpty()) {
                clickSpecificRatio(0.8f, 0.37f)
            }
        }
    }

    // --- 스와이프 로직 유지 ---
    private fun startTossSwipeLoop() {
        isTossSwiping = true
        tossRunnable = object : Runnable {
            override fun run() {
                if (isTossSwiping) {
                    swipeBelowToUp()
                    handler.postDelayed(this, 1800)
                }
            }
        }
        handler.post(tossRunnable!!)
    }

    private fun stopTossSwipe() {
        isTossSwiping = false
        tossRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun startTiktokSwipeLoop() {
        isTiktokSwiping = true
        tiktokRunnable = object : Runnable {
            override fun run() {
                if (isTiktokSwiping) {
                    swipeBelowToUp()
                    handler.postDelayed(this, 30000)
                }
            }
        }
        handler.post(tiktokRunnable!!)
    }

    private fun stopTiktokSwipe() {
        isTiktokSwiping = false
        tiktokRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun swipeBelowToUp() {
        val displayMetrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.8f)
            lineTo(displayMetrics.widthPixels / 2f, displayMetrics.heightPixels * 0.2f)
        }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(), null, null)
    }

    private fun clickSpecificRatio(ratioX: Float, ratioY: Float) {
        val displayMetrics = resources.displayMetrics
        val path = Path().apply { moveTo(displayMetrics.widthPixels * ratioX, displayMetrics.heightPixels * ratioY) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build(), null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
    }
    override fun onInterrupt() {}
}
