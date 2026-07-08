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

    // 버전 1.7 업데이트
    private val APP_VERSION = "v1.7"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private lateinit var layoutParams: WindowManager.LayoutParams // 위치 이동을 위한 전역 변수
    
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
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 100
        layoutParams.y = 300

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#AA000000")) // 반투명 배경 (코드 안전성 위해 변경)
            visibility = View.GONE
        }
        windowManager?.addView(floatingView, layoutParams)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // [v1.6 뼈대 완벽 유지] 이벤트 패키지명 필터링 및 뷰 숨김 로직
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

        // [v1.7 신규 추가] 캐시워크 전용 로직 (시작 버튼이 눌려있을 때만 동작)
        if (pkg != null && pkg.contains("cashwalk") && isRunning) {
            val rootNode = rootInActiveWindow ?: return
            val currentTime = System.currentTimeMillis()

            // 1초(1000ms) 쿨타임: 중복 클릭으로 인한 화면 멈춤 방지
            if (currentTime - lastCashwalkClickTime > 1000) {
                
                // 1. [이미지 1] '적립 완료!' 팝업창 닫기 (우측 상단 X 버튼 강제 클릭)
                if (rootNode.findAccessibilityNodeInfosByText("적립 완료").isNotEmpty()) {
                    clickSpecificRatio(0.8f, 0.38f) // 팝업창 우측 상단 X 비율
                    lastCashwalkClickTime = currentTime
                }
                // 2. [이미지 2] '장소에 도착했어요!' 배너 (노란색 적립하기 우측 강제 클릭)
                else if (rootNode.findAccessibilityNodeInfosByText("장소에 도착했어요!").isNotEmpty() ||
                         rootNode.findAccessibilityNodeInfosByText("적립하기").isNotEmpty()) {
                    clickSpecificRatio(0.85f, 0.82f) // 하단 노란버튼 우측 비율
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
            
            // [v1.7 신규 추가] 터치 및 드래그(이동) 이벤트 처리
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 처음 터치한 위치 기억
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false // 클릭 이벤트가 실행될 수 있도록 false 반환
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 움직인 거리 계산
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        // 일정 거리(10픽셀) 이상 움직이면 '드래그'로 인식하여 버튼 이동
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            windowManager?.updateViewLayout(floatingView, layoutParams)
                            true // 클릭이 실행되지 않도록 이벤트 소비
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            // 기존 클릭 동작 유지
            setOnClickListener {
                if (isRunning) stopSwipe() else startSwipe()
                updateButtons(pkg) 
            }
        }
        layout.addView(btn)
    }

    // [v1.6 뼈대 완벽 유지] 스와이프 로직
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

    // 비율 기반 강제 클릭 함수
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
