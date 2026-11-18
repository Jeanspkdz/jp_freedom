package com.jp.freedom.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusThemeTest {
    @Test fun amberIsPrimaryAndMediumPurpleIsSecondary() {
        assertEquals(Color(0xFFFFC27A), TallyColors.primary)
        assertEquals(Color(0xFFB39DDB), TallyColors.secondary)
    }
}
