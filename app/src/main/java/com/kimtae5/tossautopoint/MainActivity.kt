package com.kimtae5.tossautopoint

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// 💡 시스템에 등록되기 위한 최소한의 껍데기 대문 화면입니다.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 실행되자마자 알아서 조용히 종료되어 백그라운드로 전환됩니다.
        finish()
    }
}
