package com.kimtae5.tossautopoint

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect // 버튼의 네모 박스 좌표를 구하기 위해 필요합니다.
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

    // 버전 2.8: 서랍 애니메이션 대응 및 고유 버튼(적립/보물상자) 위치 필터 해제
    private val APP_VERSION = "v2.8"

    // 메인 스레드에서 작업을 예약하고 실행하기 위한 핸들러입니다.
    private val handler = Handler(Looper.getMainLooper())
    
    // 화면 위에 다른 뷰를 띄우기 위한 윈도우 매니저입니다.
    private var windowManager: WindowManager? = null
    
    // 시작/정지 버튼이 들어갈 투명한 네모 상자(레이아웃)입니다.
    private var floatingView: LinearLayout? = null
    
    // 플로팅 버튼의 크기와 위치 정보를 담는 변수입니다.
    private lateinit var windowLayoutParams: WindowManager.LayoutParams 
    
    // 현재 자동 클릭/스와이프가 실행 중인지 체크하는 스위치 역할의 변수입니다.
    private var isRunning = false
    // 자동화 작업을 반복해서 실행하게 해주는 Runnable 객체입니다.
    private var autoRunnable: Runnable? = null
    
    // 현재 스마트폰 화면에 떠 있는 앱의 패키지 이름을 저장합니다.
    private var currentPackageName = ""

    // 1회용 스위치: 파란 네모를 눌렀는지 기억합니다.
    private var isMapPinClicked = false 
    // 캡처 중복 방지: 스크린샷 처리가 진행 중일 때 또 캡처하는 것을 막습니다.
    private var isCapturing = false 

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    // -------------------------------------------------------------
    // 1. 서비스 시작 시 최초 1회 실행되는 설정 함수
    // -------------------------------------------------------------
    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // 화면에 띄울 플로팅 버튼 레이아웃을 생성합니다.
        createFloatingView()
    }

    // -------------------------------------------------------------
    // 2. 화면 위에 둥둥 떠 있는(Floating) 레이아웃 생성
    // -------------------------------------------------------------
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

    // -------------------------------------------------------------
    // 3. 스마트폰 화면이 변할 때마다(이벤트 발생 시) 호출되는 핵심 함수
    // -------------------------------------------------------------
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString()
        
        // 새로운 앱이 화면에 나타났다면?
        if (pkg != null && pkg != currentPackageName) {
            stopAuto() // 안전을 위해 무조건 돌고 있던 매크로를 정지시킵니다.
            currentPackageName = pkg
            
            val isTarget = pkg.contains("toss") || 
                           pkg.contains("tiktok") || 
                           pkg.contains("musically") || 
                           pkg.contains("trill") || 
                           pkg.contains("aweme") || 
                           pkg.contains("cashwalk")

            val layout = floatingView ?: return

            // 목표 앱이면 시작 버튼을 화면에 보여주고 텍스트를 업데이트합니다.
            if (isTarget) {
                layout.visibility = View.VISIBLE 
                updateButtons(pkg) 
            } else {
                // 다른 앱(예: 카카오톡)이면 버튼을 숨깁니다.
                layout.visibility = View.GONE 
                stopAuto() 
            }
        }
    }

    // -------------------------------------------------------------
    // 4. 플로팅 버튼의 디자인과 터치/드래그 동작을 제어하는 함수
    // -------------------------------------------------------------
    private fun updateButtons(pkg: String) {
        val layout = floatingView ?: return
        layout.removeAllViews() // 기존에 그려진 버튼을 지웁니다.
        
        // 현재 앱에 맞춰서 버튼에 띄울 이름을 정합니다.
        val appName = when {
            pkg.contains("toss") -> "토스"
            pkg.contains("cashwalk") -> "캐시워크"
            else -> "틱톡"
        }

        // 새로운 버튼을 생성합니다.
        val btn = Button(this).apply {
            // isRunning 상태에 따라 글자를 ■ 정지 또는 ▶ 시작으로 바꿉니다.
            text = if (isRunning) "■ $appName 정지 ($APP_VERSION)" else "▶ $appName 시작 ($APP_VERSION)"
            
            // 버튼을 꾹 누르고 드래그(이동)할 때의 처리를 담당합니다.
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { // 손가락이 처음 화면에 닿았을 때
                        initialX = windowLayoutParams.x
                        initialY = windowLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false 
                    }
                    MotionEvent.ACTION_MOVE -> { // 손가락을 떼지 않고 움직일 때
                        val dx = (event.rawX - initialTouchX).toInt() // X축으로 이동한 거리
                        val dy = (event.rawY - initialTouchY).toInt() // Y축으로 이동한 거리
                        
                        // 손떨림을 방지하기 위해 10픽셀 이상 움직였을 때만 창을 이동시킵니다.
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            windowLayoutParams.x = initialX + dx
                            windowLayoutParams.y = initialY + dy
                            windowManager?.updateViewLayout(floatingView, windowLayoutParams) // 화면 재배치
                            true 
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            // 버튼을 짧게 '클릭'했을 때의 처리입니다.
            setOnClickListener {
                if (isRunning) stopAuto() else startAuto(pkg) // 켜져있으면 끄고, 꺼져있으면 켭니다.
                updateButtons(pkg) // 버튼 글씨를 갱신합니다.
            }
        }
        layout.addView(btn) // 완성된 버튼을 레이아웃에 넣습니다.
    }

    // -------------------------------------------------------------
    // 5. [핵심] 자동 클릭 및 스와이프를 무한 반복하는 스케줄러 함수
    // -------------------------------------------------------------
    private fun startAuto(pkg: String) {
        isRunning = true
        
        autoRunnable = object : Runnable {
            override fun run() {
                // 시작 버튼이 켜져 있을 때만 작동합니다.
                if (isRunning) {
                    
                    // [캐시워크] 모드일 경우의 로직입니다.
                    if (pkg.contains("cashwalk")) {
                        val rootNode = rootInActiveWindow // 현재 화면의 모든 뼈대 구조를 가져옵니다.
                        var nextDelay = 500L // 기본 화면 감시 주기 (1초)
                        
                        if (rootNode != null) {
                            // 1. 타겟 ID를 가진 요소를 모조리 찾습니다. (이때 하단 광고의 닫기 버튼도 포함될 수 있음)
                            val rewardButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/tvReceiveRewardButton")
                            val locationCloseButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/ivCloseButton")
                            val treasureBoxes = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/coinbox")
                            val adCloseButtons = rootNode.findAccessibilityNodeInfosByViewId("com.cashwalk.cashwalk:id/ivClose")
                            
                            // 한 번의 루프(0.5초)당 한 번만 클릭하도록 제어하는 스위치입니다.
                            var isClickedInThisLoop = false

                            // ---------------------------------------------------------
                            // [1순위: 광고 창 닫기] (X버튼은 하단 광고와 겹칠 수 있으니 85% 안전구역 필터 유지!)
                            // ---------------------------------------------------------
                            if (!isClickedInThisLoop) {
                                for (button in adCloseButtons) {
                                    if (isSafeLocation(button)) {
                                        clickNodeCenter(button)
                                        isClickedInThisLoop = true
                                        break
                                    }
                                }
                            }
                            if (!isClickedInThisLoop) {
                                for (button in locationCloseButtons) {
                                    if (isSafeLocation(button)) {
                                        clickNodeCenter(button)
                                        isClickedInThisLoop = true
                                        break
                                    }
                                }
                            }

                            // ---------------------------------------------------------
                            // [2순위: '적립하기' 누르기] (확실한 고유 버튼이므로 위치 필터 해제!)
                            // ---------------------------------------------------------
                            if (!isClickedInThisLoop) {
                                for (button in rewardButtons) {
                                    // 하단에 있든 어디에 있든 투명한 가짜 버튼만 아니면 무조건 클릭!
                                    // 0.5초마다 추적하므로, 올라오는 중이라도 다음 0.5초 뒤에 멈춘 위치를 정확히 찍습니다.
                                    if (button.isVisibleToUser) { 
                                        clickNodeCenter(button)
                                        isClickedInThisLoop = true
                                        break
                                    }
                                }
                            }
                            
                            // ---------------------------------------------------------
                            // [3순위: '보물상자' 누르기] (확실한 고유 버튼이므로 위치 필터 해제!)
                            // ---------------------------------------------------------
                            if (!isClickedInThisLoop) {
                                for (button in treasureBoxes) {
                                    if (button.isVisibleToUser) { // 위치 필터 검사 X, 보이는지만 검사 O
                                        clickNodeCenter(button)
                                        isClickedInThisLoop = true
                                        break
                                    }
                                }
                            }
                            
                            // 안드로이드 메모리가 꽉 차는 것을 막기 위해 다 쓴 뼈대 정보는 쓰레기통에 버립니다(recycle).
                            rewardButtons.forEach { it.recycle() }
                            locationCloseButtons.forEach { it.recycle() }
                            treasureBoxes.forEach { it.recycle() }
                            adCloseButtons.forEach { it.recycle() }
                            rootNode.recycle()
                        }
                        
                        // 설정된 nextDelay 값에 50ms~150ms 사이의 랜덤 시간을 더해서, 기계가 아닌 사람처럼 보이게 예약합니다.
                        handler.postDelayed(this, nextDelay + Random.nextLong(50, 151))
                    } 
                    
                    // [토스 / 틱톡] 모드일 경우의 스와이프 로직입니다.
                    else {
                        performSwipe() // 화면을 위로 쓸어 올리는 함수를 실행합니다.
                        
                        // 토스면 800ms(0.8초), 아니면(틱톡) 10000ms(10초) 기본 대기 시간 세팅
                        val baseDelay = if (pkg.contains("toss")) 800L else 10000L
                        // 기계적인 탐색을 속이기 위해 매번 100ms~200ms 사이의 시간을 추가로 더함
                        val randomDelay = Random.nextLong(100, 201) 
                        
                        // 다음 스와이프를 예약합니다.
                        handler.postDelayed(this, baseDelay + randomDelay) 
                    }
                }
            }
        }
        // 예약된 루프를 처음 1회 작동시킵니다.
        handler.post(autoRunnable!!)
    }

    // -------------------------------------------------------------
    // 6. 무한 루프 스케줄러를 정지시키는 함수
    // -------------------------------------------------------------
    private fun stopAuto() {
        isRunning = false
        autoRunnable?.let { handler.removeCallbacks(it) } // 예약된 작업을 취소합니다.
    }

    // -------------------------------------------------------------
    // 7. 화면을 위아래로 스와이프(쓸어올리기) 하는 제스처 함수
    // -------------------------------------------------------------
    private fun performSwipe() {
        val dm = resources.displayMetrics
        
        // 매번 똑같은 직선을 긋지 않도록 가로 좌표에 -20 ~ +20 픽셀의 오차를 줍니다.
        val randomOffsetX = Random.nextInt(-20, 21)
        
        // 시작점(화면 중앙 아래쪽)과 끝점(화면 중앙 위쪽)의 좌표를 계산합니다.
        val startX = (dm.widthPixels / 2f) + randomOffsetX
        val startY = dm.heightPixels * 0.7f 
        val endX = (dm.widthPixels / 2f) + randomOffsetX
        val endY = dm.heightPixels * 0.3f 

        // 손가락이 지나갈 경로를 그립니다.
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        // 스와이프 하는 속도(시간)를 0.3초 + (0.1~0.2초 랜덤)으로 설정합니다.
        val duration = 300L + Random.nextLong(100, 201)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
            
        // 안드로이드 시스템에 제스처를 쏴줍니다!
        dispatchGesture(gesture, null, null)
    }

    // [광고용 X버튼 전용] 화면 상위 85% 안전 구역에 있는지, 그리고 눈에 보이는지 확인합니다.
    private fun isSafeLocation(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        
        val rect = Rect()
        // 대상 버튼의 화면상 좌상단, 우하단 픽셀 좌표를 구해서 rect에 넣습니다.
        node.getBoundsInScreen(rect)
        
        // 내 스마트폰의 전체 화면 높이(픽셀 단위)를 가져옵니다.
        val screenHeight = resources.displayMetrics.heightPixels
        
        // 화면 최상단(0)부터 전체 높이의 85% 지점까지를 '안전 구역(Safe Zone)'으로 설정합니다.
        // 예를 들어 화면 높이가 1000픽셀이라면, 850 지점까지만 안전하다고 봅니다.
        val safeZoneBottom = screenHeight * 0.85
        
        // 버튼의 한가운데 Y(세로) 좌표가 안전 구역 안에 포함되어 있는지 검사합니다.
        // 안전 구역보다 숫자가 크면 화면 맨 밑바닥(15% 영역)에 있다는 뜻이므로 false(광고)를 반환합니다.
        return rect.centerY() < safeZoneBottom
    }

    // -------------------------------------------------------------
    // 9. 특정 노드(버튼)의 중심 주변을 랜덤하게 클릭하는 정밀 타격 함수
    // -------------------------------------------------------------
    private fun clickNodeCenter(node: AccessibilityNodeInfo) {
        val rect = Rect()
        node.getBoundsInScreen(rect) 
        
        val centerX = rect.centerX().toFloat() + Random.nextInt(-10, 11)
        val centerY = rect.centerY().toFloat() + Random.nextInt(-10, 11)
        
        val path = Path().apply { moveTo(centerX, centerY) }
        val clickDuration = 50L + Random.nextLong(10, 51) 
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()
            
        dispatchGesture(gesture, null, null)
    }

    private fun showToast(msg: String) = handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // -------------------------------------------------------------
    // 10. 앱이나 서비스가 종료될 때 찌꺼기를 치우는 함수
    // -------------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()
        stopAuto() // 돌고 있던 매크로를 멈춥니다.
        floatingView?.let { windowManager?.removeView(it) } // 화면에서 버튼 상자를 제거합니다.
    }

    override fun onInterrupt() {}
}
