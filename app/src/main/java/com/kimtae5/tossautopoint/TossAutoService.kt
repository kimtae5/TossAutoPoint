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
import kotlin.random.Random // 랜덤 기능 사용을 위해 추가

class TossAutoService : AccessibilityService() {

    // 버전 1.8 업데이트: 앱별 속도 차등 및 완전 랜덤 터치 적용
    private val APP_VERSION = "v1.8"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
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
            // [수정 1] 앱이 전환될 때 꼬이는 것을 방지하기 위해, 패키지가 바뀌면 일단 동작을 정지시킵니다.
            stopAuto() 
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
                // 타겟 앱이 아니면 확실하게 정지
                stopAuto() 
            }
        }

        // [유지] 캐시워크 전용 클릭 로직 (스와이프와 무관하게 isRunning이 true면 작동)
        if (pkg != null && pkg.contains("cashwalk") && isRunning) {
            val rootNode = rootInActiveWindow ?: return
            val currentTime = System.currentTimeMillis()

            // 1초 쿨타임
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
                // [수정 1] 함수 이름 변경 (startSwipe -> startAuto)
                if (isRunning) stopAuto() else startAuto(pkg)
                updateButtons(pkg) 
            }
        }
        layout.addView(btn)
    }

    // [수정 1] 스와이프 전용이 아닌 '통합 자동화 시작' 함수로 변경
    private fun startAuto(pkg: String) {
        isRunning = true
        
        // 캐시워크라면 스와이프를 반복할 필요가 없으므로 여기서 함수 종료 (클릭은 onAccessibilityEvent에서 알아서 함)
        if (pkg.contains("cashwalk")) {
            showToast("캐시워크 대기 모드 시작")
            return 
        }

        // 토스나 틱톡일 경우 스와이프 무한 루프 시작
        swipeRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    performSwipe()
                    
                    // [수정 3 & 4] 토스면 800ms(0.8초), 아니면(틱톡) 3000ms(3초) 기본 대기 시간 설정
                    val baseDelay = if (currentPackageName.contains("toss")) 800L else 3000L
                    
                    // [수정 2] 기본 대기 시간에 100ms ~ 200ms 사이의 랜덤 시간을 더함
                    val randomDelay = Random.nextLong(100, 201) 
                    
                    handler.postDelayed(this, baseDelay + randomDelay) 
                }
            }
        }
        handler.post(swipeRunnable!!)
    }

    private fun stopAuto() {
        isRunning = false
        swipeRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun performSwipe() {
        val dm = resources.displayMetrics
        
        // [수정 2] 스와이프할 때마다 매번 똑같은 정중앙을 긋지 않도록 ±20픽셀 랜덤 좌표 부여
        val randomOffsetX = Random.nextInt(-20, 21)
        val startX = (dm.widthPixels / 2f) + randomOffsetX
        val startY = dm.heightPixels * 0.7f 
        val endX = (dm.widthPixels / 2f) + randomOffsetX
        val endY = dm.heightPixels * 0.3f 

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        // [수정 2] 스와이프하는 '드래그 속도(지속 시간)' 자체도 300ms + (100~200ms 랜덤) 적용
        val duration = 300L + Random.nextLong(100, 201)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    private fun clickSpecificRatio(ratioX: Float, ratioY: Float) {
        val dm = resources.displayMetrics
        
        // [수정 2] 캐시워크 강제 클릭 시에도 매번 같은 픽셀을 누르지 않도록 ±10픽셀 랜덤 좌표 부여
        val targetX = (dm.widthPixels * ratioX) + Random.nextInt(-10, 11)
        val targetY = (dm.heightPixels * ratioY) + Random.nextInt(-10, 11)
        
        val path = Path().apply { moveTo(targetX, targetY) }
        
        // [수정 2] 클릭 누르고 있는 짧은 순간도 완전히 똑같지 않게 50ms + 랜덤 적용
        val clickDuration = 50L + Random.nextLong(10, 51)
        
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration)).build(), null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        stopAuto()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onInterrupt() {}
}
