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
        setContentView(R.layout.activity_main)

        // 1. 안드로이드 13(API 33) 이상일 경우 상단바 알림 권한을 요청합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // 권한이 없다면 팝업창을 띄워 유저에게 권한을 요청합니다.
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        // 2. 유저를 위한 안내 메시지 출력
        Toast.makeText(this, "🤖 AI 자동화 로봇 준비 완료! 설정에서 [접근성]과 [알림] 권한을 허용해 주세요.", Toast.LENGTH_LONG).show()
    }
}
