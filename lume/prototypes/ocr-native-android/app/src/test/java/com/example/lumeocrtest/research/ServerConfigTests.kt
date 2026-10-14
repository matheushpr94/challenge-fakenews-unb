package com.example.lumeocrtest.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Endereço do servidor dos modelos (celular sem cabo): normalização e limites de segurança. */
class ServerConfigTests {
    @Test fun `endereco sem esquema vira https e perde a barra final`() {
        assertEquals("https://abc-def.trycloudflare.com", normalizeBaseUrl("  abc-def.trycloudflare.com/ "))
        assertEquals("https://abc-def.trycloudflare.com", normalizeBaseUrl("HTTPS://ABC-DEF.trycloudflare.com"))
    }

    @Test fun `http so e aceito para o computador do emulador`() {
        assertEquals("http://10.0.2.2:9000", normalizeBaseUrl("http://10.0.2.2:9000"))
        assertEquals("http://127.0.0.1:9000", normalizeBaseUrl("http://127.0.0.1:9000/"))
        assertNull(normalizeBaseUrl("http://192.168.0.10:9000"))
        assertNull(normalizeBaseUrl("http://exemplo.com"))
    }

    @Test fun `texto que nao e endereco e recusado`() {
        assertNull(normalizeBaseUrl(""))
        assertNull(normalizeBaseUrl("meu servidor"))
        assertNull(normalizeBaseUrl("localhost"))
        assertNull(normalizeBaseUrl("https://"))
    }

    @Test fun `porta e mantida`() {
        assertEquals("https://meu.dominio.com:8443", normalizeBaseUrl("meu.dominio.com:8443"))
    }
}
