package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect // 버튼의 사각형 좌표를 계산하기 위해 추가
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
import kotlin.random.Random // 사람 같은 랜덤 값 적용을 위해 추가

class TossAutoService : AccessibilityService() {

    // 버전 2.1: 중괄호 컴파일 에러 해결 및 완전한 랜덤 타격 반영 완료 버전
    private val APP_VERSION = "v2.1"

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null
    private lateinit var windowLayoutParams: WindowManager.LayoutParams 
    
    private var isRunning = false
    private var autoRunnable: Runnable? = null
    private var currentPackageName = ""

    // 플로팅 버튼 드래그 이동용 좌표 변수
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // 서비스가 시작(연결)되면 최초 1회 실행되는 함수
    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView() // 화면에 띄울 멀티 버튼 레이아웃 생성
    }

    // 화면에 보여질 플로팅 레이아웃 설정 함수
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
            setBackgroundColor(Color.parseColor("#AA000000")) // 반투명 검은색 배경
            visibility = View.GONE // 처음엔 숨김 처리 (타겟 앱 켜지면 등장)
        }
        windowManager?.addView(floatingView, windowLayoutParams)
    }

    // 스마트폰의 화면이 바뀌거나 움직일 때마다 실시간으로 감지하는 핵심 함수
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        
        // 현재 열린 앱이 이전 앱과 다르다면 (앱 전환 발생 시)
        if (pkg != null && pkg != currentPackageName) {
            stopAuto() // 안전을 위해 기존에 돌던 스와이프/클릭 자동화 루프를 무조건 정지
            currentPackageName = pkg
            
            // 토스, 틱톡, 캐시워크 패키지 중 하나인지 확인
            val isTarget = pkg.contains("toss") || 
                           pkg.contains("tiktok") || 
                           pkg.contains("musically") || 
                           pkg.contains("trill") || 
                           pkg.contains("aweme") || 
                           pkg.contains("cashwalk")

            val layout = floatingView ?: return

            if (isTarget) {
                layout.visibility = View.VISIBLE // 대상 앱이면 플로팅 버튼 표시
                updateButtons(pkg) // 열린 앱에 맞춰 버튼 텍스트 변경
            } else {
                layout.visibility = View.GONE // 대상 앱이 아니면 버튼 숨김
                stopAuto() 
            }
        }
    }

    // 화면 플로팅 버튼 디자인 및 드래그, 클릭 이벤트 처리 함수
    private fun updateButtons(pkg: String) {
        val layout = floatingView ?: return
        layout.removeAllViews()
        
        val appName = when {
            pkg.contains("toss") -> "토스"
            pkg.contains("cashwalk") -> "캐시워크"
            else -> "틱톡"
        }

        val btn = Button(this).apply {
            // 실행 상태에 따라 버튼 문구 동적 변경
            text = if (isRunning) "■ $appName 정지 ($APP_VERSION)" else "▶ $appName 시작 ($APP_VERSION)"
            
            // [터치 리스너] 버튼을 꾹 눌러서 화면 원하는 곳으로 드래그 이동시키는 로직
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
                        
                        // 미세한 떨림 방지 (10픽셀 이상 움직였을 때만 이동 처리)
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
            
            // [클릭 리스너] 시작 버튼을 누르면 정지/시작 토글 처리
            setOnClickListener {
                if (isRunning) stopAuto() else startAuto(pkg)
                updateButtons(pkg) 
            }
        }
        layout.addView(btn)
    }

    // 자동화 동작을 총괄하는 핵심 무한 루프 스케줄러 함수
    private fun startAuto(pkg: String) {
        isRunning = true
        
        autoRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    
                    // 1. [캐시워크 모드] 안전한 고유 ID 사냥 모드
                    if (pkg.contains("cashwalk")) {
                        val rootNode = rootInActiveWindow
                        var nextDelay = 500L // 기본 화면 감시 주기 (1초)
                        
                        if (rootNode != null) {
                            // 사용자가 접근성 검사기로 알아낸 정밀 ID 부품 확보
                            val rewardButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvReceiveRewardButton")
                            val closeButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/ivCloseButton")
                            
                            if (rewardButtons.isNotEmpty()) {
                                // 적립하기 버튼이 포착되면 정중앙(주변랜덤) 클릭!
                                clickNodeCenter(rewardButtons[0])
                                nextDelay = 500L // 팝업창이 바로 뜨므로 다음 사냥은 0.6초 뒤에 광속 실행
                            } else if (closeButtons.isNotEmpty()) {
                                // 적립 완료 후 팝업의 X 버튼이 포착되면 바로 클릭!
                                clickNodeCenter(closeButtons[0])
                                nextDelay = 500L // 창을 닫았으니 다시 여유롭게 1초 주기로 탐색 변경
                            }
                            
                            // [중요] 안드로이드 메모리 누수 방지를 위한 노드 반환 처리
                            rewardButtons.forEach { it.recycle() }
                            closeButtons.forEach { it.recycle() }
                            rootNode.recycle()
                        }
                        
                        // 다음 탐색 예약 (여기에도 50ms~150ms 사이의 사람 같은 시간 편차 적용)
                        handler.postDelayed(this, nextDelay + Random.nextLong(50, 151))
                    } 
                    
                    // 2. [토스 / 틱톡 모드] 기존 방식대로 스와이프 무한 루프 작동
                    else {
                        performSwipe()
                        
                        // 토스면 800ms(0.8초), 아니면(틱톡) 10000ms(10초) 기본 대기 시간 세팅
                        val baseDelay = if (pkg.contains("toss")) 800L else 10000L
                        // 기계적인 탐색을 속이기 위해 매번 100ms~200ms 사이의 시간을 추가로 더함
                        val randomDelay = Random.nextLong(100, 201) 
                        
                        handler.postDelayed(this, baseDelay + randomDelay) 
                    }
                }
            }
        }
        handler.post(autoRunnable!!)
    }

    // 자동화 동작을 안전하게 멈추는 함수
    private fun stopAuto() {
        isRunning = false
        autoRunnable?.let { handler.removeCallbacks(it) }
    }

    // 화면을 위아래로 쓸어 올리는 스와이프 제스처 함수 (토스 / 틱톡 전용)
    private fun performSwipe() {
        val dm = resources.displayMetrics
        
        // 매번 똑같은 일직선을 긋지 않도록 X축(가로) 좌표를 좌우 ±20픽셀 랜덤 흔들기 적용
        val randomOffsetX = Random.nextInt(-20, 21)
        val startX = (dm.widthPixels / 2f) + randomOffsetX
        val startY = dm.heightPixels * 0.7f 
        val endX = (dm.widthPixels / 2f) + randomOffsetX
        val endY = dm.heightPixels * 0.3f 

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        // 화면을 누르고 끄는 '지속 시간(속도)'도 0.3초 + (100~200ms 랜덤)을 더해 사람 손가락처럼 모사
        val duration = 300L + Random.nextLong(100, 201)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    // 찾은 고유 ID 부품의 정확한 좌표를 계산하여 그 '주변 영역을 랜덤 타격'하는 정밀 타격 함수 (캐시워크 전용)
    private fun clickNodeCenter(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect) // 디바이스 화면 전체 크기 기준, 해당 버튼의 네모 박스 좌표 추출
        
        // 기계처럼 정중앙 픽셀만 누르는 행위를 방지하기 위해 상하좌우 ±5픽셀 편차를 주어 주변을 무작위 타격!
        val centerX = rect.centerX().toFloat() + Random.nextInt(-5, 6)
        val centerY = rect.centerY().toFloat() + Random.nextInt(-5, 6)
        
        val path = Path().apply { moveTo(centerX, centerY) }
        
        // 화면을 톡! 누르고 떼는 손가락의 정밀한 누름 지연 시간조차 0.05초~0.1초 사이로 미세하게 무작위 조절
        val clickDuration = 50L + Random.nextLong(10, 51)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // 앱이 완전히 종료되거나 서비스가 꺼질 때 메모리 정리용 함수
    override fun onDestroy() {
        super.onDestroy()
        stopAuto()
        floatingView?.let { windowManager?.removeView(it) }
    }

    override fun onInterrupt() {}
}
