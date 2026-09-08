package com.folzi.astrachat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.folzi.astrachat.data.AppSettings
import com.folzi.astrachat.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ComposeSmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun actionsWorkAndSecretDeletionRequiresConfirmation() {
        var confirmed = 0
        compose.setContent { AstraTheme(AppSettings(theme = "amoled")) { Confirm("Удалить ключ?", "Подтвердите удаление", {}, { confirmed++ }) } }
        compose.onNodeWithText("Удалить ключ?").assertIsDisplayed()
        compose.onNodeWithText("Подтвердить").assertHasClickAction().performClick()
        assertEquals(1, confirmed)
    }

    @Test fun hairlineSurfacesRenderTranscriptLabels() {
        var picked = 0
        compose.setContent {
            AstraTheme(AppSettings(theme = "dark")) {
                Column(Modifier.padding(8.dp)) {
                    SectionLabel("ASTRA CHAT")
                    Panel { RoleLabel("Astra", accented = true) }
                    Hairline()
                    PrimaryAction("Выбрать модель") { picked++ }
                }
            }
        }
        compose.onNodeWithText("ASTRA CHAT").assertIsDisplayed()
        compose.onNodeWithText("ASTRA").assertIsDisplayed()
        compose.onNodeWithText("Выбрать модель").assertHasClickAction().performClick()
        assertEquals(1, picked)
    }
}
