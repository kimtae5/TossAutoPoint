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

    companion object {
        const val PKG_TOSS = "viva.republica.toss"
        const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_KR = "com.ss.android.ugc.trill"
        const val PKG_CASHWALK = "cashwalk.android"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
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
            visibility = View.GONE // 처음엔 숨김
        }
        windowManager?.addView(floatingView, params)
    }

    // [강화된 버튼 관리]
    private fun updateFloatingButtons(packageName: String?) {
        val layout = floatingView ?: return
        
        // 타겟 앱 판별
        val isTarget = packageName != null && (
            packageName == PKG_TOSS || 
            packageName == PKG_TIKTOK_GLOBAL || 
            packageName == PKG_TIKTOK_KR || 
            packageName == PKG_CASHWALK
        )

        // [핵심 해결] 타겟이 아니면 무조건 GONE (버튼 강제 삭제)
        if (!isTarget) {
            layout.visibility = View.GONE
            return
        }

        layout.visibility = View.VISIBLE
        layout.removeAllViews()
        
        addButton(layout, "⚡ 강제 스와이프") { performForceSwipe() }
    }

    private fun addButton(layout: LinearLayout, text: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
        layout.addView(btn)
    }

    private fun performForceSwipe() {
        // 화면 해상도에 따른 좌표 계산
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels / 2f, dm.heightPixels * 0.8f)
            lineTo(dm.widthPixels / 2f, dm.heightPixels * 0.2f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        // [디버깅 추가] 결과에 따른 메시지
        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                showToast("스와이프 완료")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                showToast("스와이프 실패: 권한 또는 앱 차단")
            }
        }, null)

        if (!success) showToast("스와이프 요청 실패")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // [강화] 이벤트 소스에서 패키지명 추출 (null 체크)
        val packageName = event.packageName?.toString()
        
        // 패키지가 바뀌었거나, 타겟 앱이 아닐 때 즉시 갱신
        updateFloatingButtons(packageName)
        currentPackageName = packageName ?: ""
    }

    private fun showToast(msg: String) = Handler(Looper.getMainLooper()).post { 
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() 
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager?.removeView(it) }
    }
    override fun onInterrupt() {}
}
