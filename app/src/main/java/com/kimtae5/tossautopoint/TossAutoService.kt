package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.random.Random

class TossAutoService : AccessibilityService() {

    // 버전 2.0: 접근성 검사기로 추출한 고유 ID 지정 타격 모드 적용 (100% 안전)
    private val APP_VERSION = "v2.0"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private lateinit var windowLayoutParams: WindowManager.LayoutParams 
    
    private var isRunning = false
    private var autoRunnable: Runnable? = null
    private var currentPackageName = ""

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

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
                stopAuto() 
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
                if (isRunning) stopAuto() else startAuto(pkg)
                updateButtons(pkg) 
            }
        }
        layout.addView(btn)
    }

    private fun startAuto(pkg: String) {
        isRunning = true
        
        autoRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    
                    // 1. [캐시워크 모드] ID 기반 정밀 타격 로직
                    if (pkg.contains("cashwalk")) {
                        val rootNode = rootInActiveWindow
                        var nextDelay = 500L // 기본 화면 탐색 주기 (1초)
                        
                        if (rootNode != null) {
                            // 사용자가 찾아낸 ID로 화면 상의 버튼 노드들을 검색
                            val rewardButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvReceiverRewardButton")
                            val closeButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/ivCloseButton")
                            
                            if (rewardButtons.isNotEmpty()) {
                                // 1순위: '적립하기' 버튼이 보이면 정중앙 클릭
                                clickNodeCenter(rewardButtons[0])
                                nextDelay = 500L // 적립 후 팝업이 뜨므로 0.6초 뒤에 바로 다음 루프 실행 (X버튼 사냥용)
                            } else if (closeButtons.isNotEmpty()) {
                                // 2순위: 적립 완료 팝업의 'X' 버튼이 보이면 정중앙 클릭
                                clickNodeCenter(closeButtons[0])
                                nextDelay = 500L // 닫았으니 다시 여유롭게 1초 주기로 변경
                            }
                            
                            // 메모리 누수 방지를 위한 노드 해제 (필수)
                            rewardButtons.forEach { it.recycle() }
                            closeButtons.forEach { it.recycle() }
                            rootNode.recycle()
                        }
                        
                        // 다음 탐색 예약 (+ 약간의 사람 같은 랜덤 딜레이 추가)
                        handler.postDelayed(this, nextDelay + Random.nextLong(50, 151))
                    } 
                    
                    // 2. [토스 / 틱톡 모드] 기존 방식대로 스와이프 실행
                    else {
                        performSwipe()
                        val baseDelay = if (pkg.contains("toss")) 800L else 10000L
                        val randomDelay = Random.nextLong(100, 201) 
                        
                        handler.postDelayed(this, baseDelay + randomDelay) 
                    }
                }
            }
        }
        handler.post(autoRunnable!!)
    }

    private fun stopAuto() {
        isRunning = false
        autoRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun performSwipe() {
        val dm = resources.displayMetrics
        val randomOffsetX = Random.nextInt(-20, 21)
        val startX = (dm.widthPixels / 2f) + randomOffsetX
        val startY = dm.heightPixels * 0.7f 
        val endX = (dm.widthPixels / 2f) + randomOffsetX
        val endY = dm.heightPixels * 0.3f 

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val duration = 300L + Random.nextLong(100, 201)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    p// 💡 [최종 수정본] 버튼 주변을 완벽하게 랜덤 타격하는 함수
	private fun clickNodeCenter(node: AccessibilityNodeInfo) {
	    val rect = Rect()
	    node.getBoundsInScreen(rect) // 디바이스 화면 상의 버튼 사각형 좌표를 가져옴
	    
	    // 원래 정중앙 좌표에 사람이 누르듯 상하좌우 ±10픽셀 범위의 랜덤 편차 부여!
	    val centerX = rect.centerX().toFloat() + Random.nextInt(-10, 11)
	    val centerY = rect.centerY().toFloat() + Random.nextInt(-10, 11)
	    
	    val path = Path().apply { moveTo(centerX, centerY) }
	    val clickDuration = 50L + Random.nextLong(10, 51) // 화면을 누르고 있는 시간도 랜덤 (0.05초~0.1초)
	    
	    val gesture = GestureDescription.Builder()
	        .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
	        .build()
	        
	    dispatchGesture(gesture, null, null)
	}


    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    override fun onDestroy() {
        super.onDestroy()
        stopAuto()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onInterrupt() {}
}
