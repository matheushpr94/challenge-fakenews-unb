package app.lume.mvp

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class CaptureActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val manager = getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(manager.createScreenCaptureIntent(),42)
        }
    }
    @Deprecated("Activity result is intentionally kept dependency-free for this MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == 42) {
            if (resultCode == RESULT_OK && data != null) {
                startForegroundService(Intent(this,CaptureService::class.java).putExtra("resultCode",resultCode).putExtra("data",data))
            } else startService(Intent(this,OverlayService::class.java).setAction("show"))
            finish()
        }
    }
}
