package com.folzi.astrachat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
}
