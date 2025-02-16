package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.plshelp.android.ui.screens.MyApplicationTheme

@Composable
fun LocationScreen(onTap: (updateText: (String) -> Unit) -> Unit) {
    var displayText by remember { mutableStateOf("Tap anywhere to get location") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                displayText = "📍 Fetching location..."
                onTap { result -> displayText = result }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = displayText, modifier = Modifier.padding(16.dp))
    }
}

@Preview
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        LocationScreen { }
    }
}
