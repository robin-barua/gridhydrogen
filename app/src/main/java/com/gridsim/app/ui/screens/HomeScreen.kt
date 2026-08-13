package com.gridsim.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun HomeScreen(nav: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Grid Hydrogen Sim", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text(
            "Network-constrained co-optimization of green hydrogen and energy storage, " +
                "based on the PJM 5-bus congestion benchmark.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )
        Button(onClick = { nav.navigate("demo") }, modifier = Modifier.padding(bottom = 12.dp)) {
            Text("Demo mode (PJM 5-bus, Cases 1-4)")
        }
        Button(onClick = { nav.navigate("custom") }) {
            Text("Custom mode (edit parameters)")
        }
    }
}
