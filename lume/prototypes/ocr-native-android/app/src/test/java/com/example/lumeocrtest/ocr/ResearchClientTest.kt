package com.example.lumeocrtest.ocr

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class ResearchClientTest {
    @Test fun missingOptionalSectionsRemainEmpty() {
        val response = decodeResearchResponse("""{"status":"insuficiente","sintese":{"frase":"Sem evidências"}}""")
        assertEquals("Sem evidências", response.objectOrEmpty("sintese").string("frase"))
        assertTrue(response.objects("contexto").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun nonObjectResponseIsRejected() { decodeResearchResponse("[]") }

    @Test fun searchPreservesRequestIdAndStructuredEvidence() = runBlocking {
        ServerSocket(0).use { server ->
            val worker = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val first = input.readLine()
                    assertTrue(first.startsWith("POST /api/search"))
                    var size = 0
                    while (true) {
                        val line = input.readLine()
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", true)) size = line.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(size); var read = 0
                    while (read < size) read += input.read(body, read, size-read)
                    val request = decodeResearchResponse(String(body))
                    val result = """{"id_consulta":"${request.string("id_consulta")}","sintese":{"indicacao":{"texto":"Inconclusiva"}},"resultados":{"responde":[]}}""".toByteArray()
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${result.size}\r\nConnection: close\r\n\r\n".toByteArray() + result)
                }
            }
            val result = ResearchClient("http://127.0.0.1:${server.localPort}").search("Afirmacao de teste", "integration-1")
            assertEquals("integration-1", result.string("id_consulta"))
            assertEquals("Inconclusiva", result.objectOrEmpty("sintese").objectOrEmpty("indicacao").string("texto"))
            worker.join(2000)
        }
    }
}
