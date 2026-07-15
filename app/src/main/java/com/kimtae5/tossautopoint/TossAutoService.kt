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

    // 버전 2.2: 광고 오클릭 방지 -> 앵커(고유 ID)를 활용한 이중 인증 조건 추가
    private val APP_VERSION = "v2.2"

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
                    
                    if (pkg.contains("cashwalk")) {
                        val rootNode = rootInActiveWindow
                        var nextDelay = 500L 
                        
                        if (rootNode != null) {
                            // 1. 타겟 버튼 탐색
                            val rewardButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvReceiveRewardButton")
                            val closeButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/ivCloseButton")
                            
                            // 💡 2. [조건 강화] 이중 인증용 앵커(Anchor) 탐색
                            // TODO: 접근성 검사기로 찾은 앵커 ID를 아래에 교체해서 넣으세요!
                            val bannerAnchor = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvTodayRewardCount")
                            val popupAnchor = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvPlaceName")
                            
                            // 3. 진짜 화면인지 이중 체크 후 클릭
                            if (rewardButtons.isNotEmpty() && bannerAnchor.isNotEmpty()) {
                                // 적립 버튼 AND 배너 앵커가 모두 있을 때만 클릭 (광고 팝업에 있는 가짜 버튼 무시)
                                clickNodeCenter(rewardButtons[0])
                                nextDelay = 500L 
                            } else if (closeButtons.isNotEmpty() && popupAnchor.isNotEmpty()) {
                                // X 버튼 AND 팝업 앵커가 모두 있을 때만 클릭 (일반 광고의 X 버튼 무시)
                                clickNodeCenter(closeButtons[0])
                                nextDelay = 500L 
                            }
                            
                            // 메모리 누수 방지용 노드 반환
                            rewardButtons.forEach { it.recycle() }
                            closeButtons.forEach { it.recycle() }
                            bannerAnchor.forEach { it.recycle() }
                            popupAnchor.forEach { it.recycle() }
                            rootNode.recycle()
                        }
                        
                        handler.postDelayed(this, nextDelay + Random.nextLong(50, 151))
                    } 
                    else {
                        performSwipe()
                        val baseDelay = if (pkg.contains("toss")) 800L else 3000L
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

    private fun clickNodeCenter(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect) 
        
        val centerX = rect.centerX().toFloat() + Random.nextInt(-5, 6)
        val centerY = rect.centerY().toFloat() + Random.nextInt(-5, 6)
        
        val path = Path().apply { moveTo(centerX, centerY) }
        val clickDuration = 50L + Random.nextLong(10, 51) 
        
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
