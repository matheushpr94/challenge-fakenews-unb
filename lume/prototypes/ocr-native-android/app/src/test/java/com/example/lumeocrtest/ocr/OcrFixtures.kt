package com.example.lumeocrtest.ocr

import com.google.gson.JsonParser

/** Saída real do ML Kit gravada por `OcrFixtureDumpTest` (androidTest) em `src/test/resources/ocr`. */
data class OcrFixture(val name: String, val width: Int, val height: Int, val blocks: List<OcrBlock>)

object OcrFixtures {
    fun load(name: String): OcrFixture {
        val stream = OcrFixtures::class.java.classLoader!!.getResourceAsStream("ocr/$name.json")
            ?: error("fixture ocr/$name.json não encontrada")
        val root = JsonParser.parseString(stream.bufferedReader(Charsets.UTF_8).readText()).asJsonObject
        fun rect(e: com.google.gson.JsonElement?) = e?.takeIf { it.isJsonArray }?.asJsonArray?.let {
            OcrRect(it[0].asInt, it[1].asInt, it[2].asInt, it[3].asInt)
        }
        val blocks = root.getAsJsonArray("blocks").map { b ->
            val o = b.asJsonObject
            val lines = o.getAsJsonArray("lines").map { it.asJsonObject }
            OcrBlock(
                text = o.get("text").asString,
                boundingBox = rect(o.get("box")),
                lines = lines.map { it.get("text").asString },
                lineDetails = lines.map { l ->
                    OcrLine(l.get("text").asString, rect(l.get("box")), l.get("confidence")?.asFloat)
                },
            )
        }
        return OcrFixture(name, root.get("width").asInt, root.get("height").asInt, blocks)
    }
}
