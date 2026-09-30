package com.example.lumeocrtest.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.example.lumeocrtest.MainActivity
import com.example.lumeocrtest.R
import com.example.lumeocrtest.mascot.MascotService
import java.io.File
import java.io.FileOutputStream

/** Uma única imagem, enviada ao app para revisão antes de qualquer pesquisa. */
class CaptureService : Service() {
    companion object {
        const val RESULT_CODE = "capture_result_code"
        const val PROJECTION_DATA = "capture_projection_data"
        private const val STOP = "capture_stop"
        private const val CHANNEL = "lume_capture"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var done = false
    private var width = 0
    private var height = 0

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Leitura solicitada da tela", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 3, Intent(this, CaptureService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val notification = builder
            .setContentTitle("Lume está lendo a tela autorizada")
            .setContentText("Uma imagem será capturada para você revisar.")
            .setSmallIcon(R.drawable.ic_lume_notification)
            .addAction(Notification.Action.Builder(null, "Cancelar", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(22, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(22, notification)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            finishCapture(null, "Captura cancelada.")
            return START_NOT_STICKY
        }
        if (projection != null || done) return START_NOT_STICKY
        val permission = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(PROJECTION_DATA, Intent::class.java)
        else intent?.getParcelableExtra<Intent>(PROJECTION_DATA)
        if (permission == null) {
            finishCapture(null, "Autorização de captura ausente.")
            return START_NOT_STICKY
        }
        try {
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
                    ?: Activity.RESULT_CANCELED, permission)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!done) handler.post { finishCapture(null, "A captura foi interrompida.") }
                }
                override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
                    if (!done && newWidth > 0 && newHeight > 0 && (newWidth != width || newHeight != height)) {
                        makeReader(newWidth, newHeight)
                        display?.resize(newWidth, newHeight, resources.displayMetrics.densityDpi)
                        display?.surface = reader!!.surface
                    }
                }
            }, handler)
            val bounds = if (Build.VERSION.SDK_INT >= 30)
                getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds else null
            width = bounds?.width() ?: resources.displayMetrics.widthPixels
            height = bounds?.height() ?: resources.displayMetrics.heightPixels
            handler.postDelayed({
                if (!done) try {
                    makeReader(width, height)
                    display = projection!!.createVirtualDisplay("Lume one screen", width, height,
                        resources.displayMetrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        reader!!.surface, null, handler)
                } catch (_: Exception) {
                    finishCapture(null, "Não foi possível capturar. Tente compartilhar uma imagem.")
                }
            }, 700)
            handler.postDelayed({ if (!done) finishCapture(null, "A tela não ficou disponível. Tente novamente.") }, 15_000)
        } catch (_: Exception) {
            finishCapture(null, "O Android não autorizou a captura. Tente novamente.")
        }
        return START_NOT_STICKY
    }

    private fun makeReader(newWidth: Int, newHeight: Int) {
        reader?.setOnImageAvailableListener(null, null)
        val previous = reader
        width = newWidth
        height = newHeight
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: Exception) { null }
                    ?: return@setOnImageAvailableListener
                try {
                    if (done) return@setOnImageAvailableListener
                    val plane = image.planes[0]
                    val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    val file = File(cacheDir, "lume-capture-${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    padded.recycle()
                    finishCapture(file, null)
                } catch (_: Exception) {
                    finishCapture(null, "Não foi possível salvar a imagem capturada.")
                } finally {
                    image.close()
                }
            }, handler)
        }
        display?.surface = reader!!.surface
        previous?.close()
    }

    private fun finishCapture(file: File?, error: String?) {
        if (done) return
        done = true
        cleanup()
        if (MascotService.active.value) startService(Intent(this, MascotService::class.java)
            .setAction(MascotService.SHOW_AFTER_CAPTURE))
        startActivity(Intent(this, MainActivity::class.java)
            .setAction(MainActivity.OPEN_CAPTURE)
            .putExtra(MainActivity.CAPTURE_URI, file?.let { android.net.Uri.fromFile(it).toString() })
            .putExtra(MainActivity.CAPTURE_ERROR, error)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        stopSelf()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        display?.release(); display = null
        reader?.setOnImageAvailableListener(null, null); reader?.close(); reader = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() {
        done = true
        cleanup()
        super.onDestroy()
    }
}
