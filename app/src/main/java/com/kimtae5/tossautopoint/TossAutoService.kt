package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class TossAutoService : AccessibilityService() {

    // 빌드 에러 수정 버전
    private val APP_VERSION = "v1.7.1"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    
    // 💡 [수정됨] 버튼의 layoutParams와 이름이 겹치지 않도록 명확하게 windowLayoutParams로 변경
    private lateinit var windowLayoutParams: WindowManager.LayoutParams 
    
    private var isRunning = false
    private var swipeRunnable: Runnable? = null
    private var currentPackageName = ""

    // 드래그 이동을 위한 변수
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // 캐시워크 무한 클릭 방지용 쿨타임 변수
    private var lastCashwalkClickTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
    }

    private fun createFloatingView() {
        // 💡 [수정됨]
        windowLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowLayoutParams.gravity = Gravity.TOP or Gravity.START
        windowLayoutParams.x = 100
        windowLayoutParams.y = 300

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#AA000000")) 
            visibility = View.GONE
        }
        windowManager?.addView(floatingView, windowLayoutParams)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        
        if (pkg != null && pkg != currentPackageName) {
            currentPackageName = pkg
            
            val isTarget = pkg.contains("toss") || 
                           pkg.contains("tiktok") || 
                           pkg.contains("musically") || 
                           pkg.contains("trill") || 
                           pkg.contains("aweme") || 
                           pkg.contains("cashwalk")

            val layout = floatingView ?: return

            if (isTarget) {
                layout.visibility = View.VISIBLE
                updateButtons(pkg)
            } else {
                layout.visibility = View.GONE
                stopSwipe() 
            }
        }

        // 캐시워크 전용 로직
        if (pkg != null && pkg.contains("cashwalk") && isRunning) {
            val rootNode = rootInActiveWindow ?: return
            val currentTime = System.currentTimeMillis()

            if (currentTime - lastCashwalkClickTime > 1000) {
                if (rootNode.findAccessibilityNodeInfosByText("적립 완료").isNotEmpty()) {
                    clickSpecificRatio(0.8f, 0.38f) 
                    lastCashwalkClickTime = currentTime
                }
                else if (rootNode.findAccessibilityNodeInfosByText("장소에 도착했어요!").isNotEmpty() ||
                         rootNode.findAccessibilityNodeInfosByText("적립하기").isNotEmpty()) {
                    clickSpecificRatio(0.85f, 0.82f) 
                    lastCashwalkClickTime = currentTime
                }
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
            
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 💡 [수정됨] windowLayoutParams 사용
                        initialX = windowLayoutParams.x
                        initialY = windowLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false 
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            // 💡 [수정됨] windowLayoutParams 사용
                            windowLayoutParams.x = initialX + dx
                            windowLayoutParams.y = initialY + dy
                            windowManager?.updateViewLayout(floatingView, windowLayoutParams)
                            true 
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
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
            
        dispatchGesture(gesture, null, null)
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
