package com.example.lumeocrtest.ocr

import android.graphics.Bitmap
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    
    suspend fun analyze(bitmap: Bitmap, rawText: String, blocks: List<OcrBlock>, cacheDir: File): VlmResponse? = withContext(Dispatchers.IO) {
        val tempFile = File(cacheDir, "temp_image.jpg")
        val out = FileOutputStream(tempFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.flush()
        out.close()
        
        val blocksJson = gson.toJson(blocks)
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", 
                "image.jpg", 
                tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            .addFormDataPart("ml_kit_text", rawText)
            .addFormDataPart("ml_kit_blocks", blocksJson)
            .build()
            
        val request = Request.Builder()
            .url("http://10.0.2.2:8000/analyze")
            .post(requestBody)
            .build()
            
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(responseBody, VlmResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            tempFile.delete()
        }
    }
}
