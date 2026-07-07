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

    // 💡 버전 표시 상수 추가
    private val APP_VERSION = "v1.5"

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
            setBackgroundColor(0xAA000000.toInt()) // 반투명 배경
            visibility = View.GONE
        }
        windowManager?.addView(floatingView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        
        // 💡 [수정] 틱톡 라이트, 한국 틱톡, 캐시워크 등 모든 패키지명 확실하게 포함
        val isTarget = pkg.contains("toss") || 
                       pkg.contains("tiktok") || 
                       pkg.contains("musically") || 
                       pkg.contains("trill") || 
                       pkg.contains("cashwalk")
        
        // 앱 화면이 전환되었을 때 UI 업데이트
        if (pkg != currentPackageName) {
            currentPackageName = pkg
            
            val layout = floatingView ?: return
            if (isTarget) {
                layout.visibility = View.VISIBLE
                updateButtons(pkg)
            } else {
                // 타겟 앱이 아니면(홈화면 등) 버튼 숨기고 동작 강제 정지
                layout.visibility = View.GONE
                stopSwipe() 
            }
        }

        // 💡 [복구] 예전에 작동했던 캐시워크 클릭 로직
        if (pkg.contains("cashwalk")) {
            val rootNode = rootInActiveWindow ?: return
            if (rootNode.findAccessibilityNodeInfosByText("적립 완료").isNotEmpty()) {
                clickSpecificRatio(0.8f, 0.37f)
            }
        }
    }

    // 💡 앱에 따라 동적으로 버튼 텍스트 변경 (버전 표시 포함)
    private fun updateButtons(pkg: String) {
        val layout = floatingView ?: return
        layout.removeAllViews()
        
        val appName = when {
            pkg.contains("toss") -> "토스"
            pkg.contains("cashwalk") -> "캐시워크"
            else -> "틱톡"
        }

        val btn = Button(this).apply {
            // 버튼에 버전 텍스트 표시
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
                    handler.postDelayed(this, 3000) // 3초 간격 스와이프
                }
            }
        }
        handler.post(swipeRunnable!!)
        showToast("자동화 시작!")
    }

    private fun stopSwipe() {
        isRunning = false
        swipeRunnable?.let { handler.removeCallbacks(it) }
    }

    // 💡 [수정] 스와이프 튜닝: 사람의 손가락 움직임과 가장 유사한 좌표와 속도
    private fun performSwipe() {
        val dm = resources.displayMetrics
        val startX = dm.widthPixels / 2f
        val startY = dm.heightPixels * 0.7f // 아래에서
        val endX = dm.widthPixels / 2f
        val endY = dm.heightPixels * 0.3f // 위로

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        // 400ms: 시스템이 가짜 입력으로 인식하지 않고 물리적 드래그로 인식하기 가장 좋은 시간
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    // 예전의 클릭 로직
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
