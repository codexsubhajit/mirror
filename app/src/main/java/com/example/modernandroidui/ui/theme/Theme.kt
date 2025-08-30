package com.example.modernandroidui.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance

private val appTypography = androidx.compose.material3.Typography()

@Composable
fun ModernAndroidUITheme(
    content: @Composable () -> Unit
) {
    // Set system bar colors globally using Accompanist SystemUiController
    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography,
        shapes = shapes
    ) {
        val systemUiController = com.google.accompanist.systemuicontroller.rememberSystemUiController()
        val useDarkIcons = false // Always use white nav buttons
        systemUiController.setStatusBarColor(
            color = MaterialTheme.colorScheme.primary,
            darkIcons = true // status bar icons can remain dark if theme is light
        )
        systemUiController.setNavigationBarColor(
            color = Color(0x80000000), // 50% transparent black
            darkIcons = useDarkIcons // false = white nav buttons
        )
        content()
    }
}

private val colorScheme = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    background = Color(0xFFF0F0F0),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
)

private val shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp)
)