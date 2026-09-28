package com.example.lumeocrtest

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import com.example.lumeocrtest.ocr.ArticleMetadata
import com.example.lumeocrtest.research.*
import kotlinx.coroutines.*

/** Memory-only session; survives rotation without saving captures to disk. */
class AnalysisState : ViewModel() {
    val service=ResearchService()
    val gate=RequestGate()
    val linkFetcher=OkHttpFetcher()
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    var ocrJob: Job?=null
    var startedUri: String?=null
    var consumedImport=0
    val selectedUri = mutableStateOf<String?>(null)
    val preview = mutableStateOf<ImageBitmap?>(null)
    val recognizedText = mutableStateOf("")
    val cleanedText = mutableStateOf("")
    val metadata = mutableStateOf<ArticleMetadata?>(null)
    val mainClaim = mutableStateOf<String?>(null)
    val claims = mutableStateOf<List<String>>(emptyList())
    val showRawText = mutableStateOf(false)
    val ocrError = mutableStateOf<String?>(null)
    val ocrState = mutableStateOf("")
    val job = mutableStateOf<Job?>(null)
    val queryText = mutableStateOf("")
    val busyText = mutableStateOf("")
    val evaluation = mutableStateOf<Evaluation?>(null)
    val clarification = mutableStateOf<Clarification?>(null)
    val lastOptions = mutableStateOf<Clarification?>(null)
    val searchError = mutableStateOf<String?>(null)
    val notice = mutableStateOf<String?>(null)
    val searchedText = mutableStateOf<String?>(null)
    val correcting = mutableStateOf(false)
    val correctionText = mutableStateOf("")
    val flowOriginal = mutableStateOf("")
    val flowDetails = mutableStateOf("")
    val flowRejected = mutableStateOf<List<String>>(emptyList())
    val flowRound = mutableIntStateOf(0)
    val enteringDetails = mutableStateOf(false)
    val detailsText = mutableStateOf("")
    val lastAction = mutableStateOf<(() -> Unit)?>(null)
    override fun onCleared() {
        gate.invalidate()
        scope.cancel()
        lastAction.value=null
        super.onCleared()
    }
}
