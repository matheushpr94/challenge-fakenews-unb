package com.example.lumeocrtest

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import com.example.lumeocrtest.research.*
import com.example.lumeocrtest.ui.theme.LumeOCRTestTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lumeocrtest.mascot.MascotService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LumeFrontendTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Before fun stopOverlay() {
        ui.activity.stopService(Intent(ui.activity,MascotService::class.java))
    }

    @Test fun homeHasHonestActionsAndNoTechnicalSettings() {
        ui.onNodeWithText("Seu companheiro de leitura.").assertIsDisplayed()
        ui.onNodeWithContentDescription("Lume, seu companheiro de leitura").assertIsDisplayed()
        ui.onNodeWithText("Ativar mascote").assertIsDisplayed()
        ui.onNodeWithText("Simular captura").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Configurar conexão").assertDoesNotExist()
        ui.onNodeWithText("Perfil").performClick()
        ui.onNodeWithText("Preferências neste aparelho").assertIsDisplayed()
        ui.onNodeWithText("Animações suaves").assertIsDisplayed()
    }

    @Test fun editsSurviveProfileAndRotation() {
        ui.onNodeWithText("Colar texto ou link").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performScrollTo().performTextInput("Texto editado para conferir")
        ui.onNodeWithText("Perfil").performClick()
        ui.onNodeWithText("Analisar",useUnmergedTree=true).performClick()
        ui.onNodeWithText("Colar texto ou link").performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("Texto editado para conferir")
        ui.activityRule.scenario.recreate()
        ui.onNode(hasSetTextAction()).assertTextContains("Texto editado para conferir")
    }
    @Test fun multipleDetailsHaveNoGlobalVerdictAndRelatedSourcesStartCollapsed() {
        val card=ResultCard("Publicação relacionada", "https://example.org/noticia", null, "Veículo", null,
            null, "", "noticia", "contexto", "Menciona o tema", "titulo", "Apenas título disponível",
            null, emptyList(), emptyList(), emptyList())
        fun part(text: String)=ResearchResult("teste",0,interpret(text,0),"ok","",
            sintese=Synthesis("sem_comparacao","",text,"regras"),
            resultados=mapOf("direto" to listOf(card)),trechoDaEntrada=text)
        val result=Evaluation("teste","partes","ok","",listOf(
            part("A prefeitura enviou o projeto ao conselho."),part("O conselho vai analisar a proposta.")))
        ui.runOnUiThread {
            ui.activity.setContent { LumeOCRTestTheme { Column { ResearchResults(result) {} } } }
        }
        ui.onNodeWithText("O que encontramos").assertIsDisplayed()
        ui.onNodeWithText("Indicação provisória").assertDoesNotExist()
        ui.onNodeWithText("Publicação relacionada").assertDoesNotExist()
        ui.onNodeWithText("Ver outras publicações relacionadas (1)").performClick()
        ui.onNodeWithText("Publicação relacionada").assertExists()
    }

}
