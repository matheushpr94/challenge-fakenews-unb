package app.lume.mvp

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager

class CaptureService : Service() {
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capture","Leitura da tela",NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this,3,Intent(this,CaptureService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,"capture").setContentTitle("Capturando a tela solicitada")
            .setContentText("Uma imagem será capturada para você revisar.").setSmallIcon(R.drawable.lume_icon)
            .addAction(Notification.Action.Builder(null,"Cancelar",stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(22,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(22,notification)
    }
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finishCapture("Captura cancelada."); return START_NOT_STICKY }
        if (projection != null || done) return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data",Intent::class.java) else intent?.getParcelableExtra<Intent>("data")
        if (data == null) { finishCapture("Autorização de captura ausente."); return START_NOT_STICKY }
        try {
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(intent!!.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
            projection!!.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { if (!done) finishCapture("A captura foi interrompida. Você pode tentar novamente.") }
                override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
                    if (!done && newWidth > 0 && newHeight > 0 && (newWidth != width || newHeight != height)) {
                        makeReader(newWidth,newHeight)
                        display?.resize(newWidth,newHeight,resources.displayMetrics.densityDpi)
                        display?.surface = reader!!.surface
                    }
                }
            },handler)
            val manager = getSystemService(WindowManager::class.java)
            val bounds = if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds else null
            width = bounds?.width() ?: resources.displayMetrics.widthPixels
            height = bounds?.height() ?: resources.displayMetrics.heightPixels
            // Let the permission activity disappear before creating the projection.
            handler.postDelayed({
                if (!done) try {
                    makeReader(width,height)
                    display = projection!!.createVirtualDisplay("Lume single capture",width,height,resources.displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
                } catch (_: Exception) { finishCapture("Não foi possível capturar. Tente compartilhar um print ou link.") }
            },650)
            handler.postDelayed({ if (!done) finishCapture("A tela não ficou disponível. Abra o conteúdo e tente novamente.") },15000)
        } catch (_: Exception) { finishCapture("O Android não autorizou esta captura. Tente novamente.") }
        return START_NOT_STICKY
    }
    private fun makeReader(newWidth: Int, newHeight: Int) {
        reader?.setOnImageAvailableListener(null,null)
        val previous = reader
        width = newWidth; height = newHeight
        reader = ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2).apply {
            setOnImageAvailableListener({ source ->
                val image = try { source.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
                try {
                    if (done) return@setOnImageAvailableListener
                    val plane = image.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth,image.height,Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(padded,0,0,image.width,image.height)
                    val scaled = Reading.fit(cropped)
                    if (cropped !== padded && padded !== scaled) padded.recycle()
                    if (cropped !== scaled) cropped.recycle()
                    Reading.clear(); Reading.image = scaled
                    finishCapture(null)
                } catch (_: Exception) { finishCapture("Não consegui ler a imagem. Compartilhe um print ou o texto.") }
                finally { image.close() }
            },handler)
        }
        // Release the old surface only after the virtual display is switched to the new one.
        if (display != null) display!!.surface = reader!!.surface
        previous?.close()
    }
    private fun finishCapture(error: String?) {
        if (done) return
        done = true; cleanup()
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this,OverlayService::class.java).setAction(if(error == null) "review" else "error").putExtra("message",error))
        }
        stopSelf()
    }
    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        display?.release(); display = null
        reader?.setOnImageAvailableListener(null,null); reader?.close(); reader = null
        projection?.stop(); projection = null
    }
    override fun onDestroy() { done = true; cleanup(); super.onDestroy() }
}
