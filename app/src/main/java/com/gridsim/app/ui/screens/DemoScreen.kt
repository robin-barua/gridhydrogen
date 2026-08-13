package com.gridsim.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gridsim.app.model.CaseToggle
import com.gridsim.app.model.StorageTech
import com.gridsim.app.ui.AppViewModel

private data class CaseOption(val label: String, val desc: String, val toggle: CaseToggle)

private val caseOptions = listOf(
    CaseOption("Case 1: Conventional only", "Baseline — thermal generation serves all load.", CaseToggle.CASE1_CONVENTIONAL),
    CaseOption("Case 2: + Renewables", "Solar + wind added, no hydrogen or storage. Expect curtailment behind the 4-5 tie line.", CaseToggle.CASE2_RENEWABLES),
    CaseOption("Case 3: + Green hydrogen", "Electrolyzers absorb the congestion-stranded surplus.", CaseToggle.CASE3_HYDROGEN),
    CaseOption("Case 4: + Storage", "Adds a storage unit on top of Case 3.", CaseToggle.CASE4_STORAGE)
)

@Composable
fun DemoScreen(nav: NavHostController, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.resetToDefaults() }
    var selected by remember { mutableStateOf(caseOptions[2]) }
    var storageTech by remember { mutableStateOf(StorageTech.BATTERY) }
    val isRunning by vm.isRunning.collectAsState()
    val result by vm.result.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("PJM 5-bus demo", style = MaterialTheme.typography.headlineSmall)
        Text("720 MW wind @ Bus 5, 300 MW solar @ Bus 3, 240 MVA 4-5 tie line (the binding corridor).")
        Spacer(Modifier.padding(top = 12.dp))

        caseOptions.forEach { opt ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                RadioButton(selected = selected == opt, onClick = { selected = opt })
                Column {
                    Text(opt.label, style = MaterialTheme.typography.titleSmall)
                    Text(opt.desc, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (selected.toggle.includeStorage) {
            Spacer(Modifier.padding(top = 8.dp))
            Text("Storage technology", style = MaterialTheme.typography.titleSmall)
            StorageTech.values().forEach { tech ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = storageTech == tech, onClick = { storageTech = tech })
                    Text("${tech.label}  (${tech.durationHours.toInt()}h duration)")
                }
            }
        }

        Spacer(Modifier.padding(top = 20.dp))
        Button(
            onClick = {
                vm.setCase(selected.toggle)
                if (selected.toggle.includeStorage) vm.setStorageChoice(storageTech, 150.0)
                vm.runSimulation()
            },
            enabled = !isRunning
        ) {
            Text(if (isRunning) "Solving..." else "Run simulation")
        }
        if (isRunning) {
            Spacer(Modifier.padding(top = 12.dp))
            CircularProgressIndicator()
            Text("Solving a 24-hour LP on-device (this can take a few seconds)...")
        }
    }

    LaunchedEffect(result, isRunning) {
        if (result != null && !isRunning) {
            nav.navigate("results")
        }
    }
}
