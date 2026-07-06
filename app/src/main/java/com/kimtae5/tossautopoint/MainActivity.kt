package com.kimtae5.tossautopoint

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // [수정 완료] R.layout.activity_main 의존성을 제거하여 빌드 에러를 방지합니다.
        
        // 1. 안드로이드 13(API 33) 이상일 경우 상단바 알림 권한을 요청합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // 2. 안내 메시지 출력 후 앱을 종료합니다(투명 테마이므로 화면이 보이지 않음).
        Toast.makeText(this, "🤖 권한 확인 중입니다. 필요한 경우 허용 버튼을 눌러주세요.", Toast.LENGTH_SHORT).show()
        finish() 
    }
}
