package com.example.lumeocrtest.research

import com.example.lumeocrtest.capture.normalizeAddress
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class PageAddressTests {
    @Test fun `endereco da barra ganha esquema e e aceito`() {
        assertEquals("https://g1.globo.com/mundo/noticia/2026/10/13/x.ghtml",
            normalizeAddress("g1.globo.com/mundo/noticia/2026/10/13/x.ghtml"))
        assertEquals("https://www.uol.com.br/", normalizeAddress("https://www.uol.com.br/"))
    }

    @Test fun `texto digitado na barra nao vira endereco`() {
        assertNull(normalizeAddress("quem ganhou o nobel"))
        assertNull(normalizeAddress("nobel"))
        assertNull(normalizeAddress(""))
    }
}
