package com.example.lumeocrtest.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import com.example.lumeocrtest.MainActivity
import com.example.lumeocrtest.mascot.MascotService

/** A captura acontece apenas após um toque e a autorização do sistema para esta sessão. */
class CaptureActivity : Activity() {
    private val requestCode = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (MascotService.active.value) {
                startService(Intent(this, MascotService::class.java).setAction(MascotService.HIDE_FOR_CAPTURE))
            }
            val manager = getSystemService(MediaProjectionManager::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
        }
    }

    @Deprecated("Usado somente para transportar a autorização de captura desta atividade")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != this.requestCode) return
        if (resultCode == RESULT_OK && data != null) {
            val service = Intent(this, CaptureService::class.java)
                .putExtra(CaptureService.RESULT_CODE, resultCode)
                .putExtra(CaptureService.PROJECTION_DATA, data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        } else {
            if (MascotService.active.value) startService(Intent(this, MascotService::class.java)
                .setAction(MascotService.SHOW_AFTER_CAPTURE))
            startActivity(Intent(this, MainActivity::class.java)
                .setAction(MainActivity.OPEN_CAPTURE)
                .putExtra(MainActivity.CAPTURE_ERROR, "Captura cancelada. Você pode importar uma imagem ou colar um texto.")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}
