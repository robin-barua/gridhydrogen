package com.gridsim.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.gridsim.app.model.PjmDefaults
import com.gridsim.app.model.StorageTech
import com.gridsim.app.ui.AppViewModel

@Composable
fun CustomScreen(nav: NavHostController, vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.resetToDefaults() }

    val genCosts = remember { PjmDefaults.generators().associate { it.name to mutableStateOf(it.costPerMwh.toString()) } }
    val genCaps = remember { PjmDefaults.generators().associate { it.name to mutableStateOf(it.capacityMw.toString()) } }
    var solarCapacity by remember { mutableStateOf("300") }
    var windCapacity by remember { mutableStateOf("720") }
    var tieLineRating by remember { mutableStateOf("240") }
    var peakDemand by remember { mutableStateOf("1100") }
    var carbonPrice by remember { mutableStateOf("0") }
    var storageTech by remember { mutableStateOf(StorageTech.BATTERY) }
    var storagePower by remember { mutableStateOf("150") }
    var caseChoice by remember { mutableStateOf(CaseToggle.CASE4_STORAGE) }

    val isRunning by vm.isRunning.collectAsState()
    val result by vm.result.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Custom system", style = MaterialTheme.typography.headlineSmall)
        Text("Starts from the PJM 5-bus defaults — edit anything below, then run.", style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.padding(top = 12.dp))
        Text("Generators", style = MaterialTheme.typography.titleSmall)
        genCosts.keys.forEach { name ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(name, modifier = Modifier.padding(end = 8.dp, top = 14.dp))
                OutlinedTextField(
                    value = genCosts[name]!!.value, onValueChange = { genCosts[name]!!.value = it },
                    label = { Text("\$/MWh") }, modifier = Modifier.weight(1f).padding(end = 4.dp)
                )
                OutlinedTextField(
                    value = genCaps[name]!!.value, onValueChange = { genCaps[name]!!.value = it },
                    label = { Text("Capacity MW") }, modifier = Modifier.weight(1f)
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Renewables & network", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(value = solarCapacity, onValueChange = { solarCapacity = it }, label = { Text("Solar capacity (MW)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = windCapacity, onValueChange = { windCapacity = it }, label = { Text("Wind capacity (MW)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = tieLineRating, onValueChange = { tieLineRating = it }, label = { Text("4-5 tie line rating (MVA)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = peakDemand, onValueChange = { peakDemand = it }, label = { Text("Evening peak demand (MW)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(value = carbonPrice, onValueChange = { carbonPrice = it }, label = { Text("Carbon price (\$/ton)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Storage", style = MaterialTheme.typography.titleSmall)
        StorageTech.values().forEach { tech ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = storageTech == tech, onClick = { storageTech = tech })
                Text(tech.label)
            }
        }
        OutlinedTextField(value = storagePower, onValueChange = { storagePower = it }, label = { Text("Storage power rating (MW)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Case", style = MaterialTheme.typography.titleSmall)
        listOf(
            "Conventional only" to CaseToggle.CASE1_CONVENTIONAL,
            "+ Renewables" to CaseToggle.CASE2_RENEWABLES,
            "+ Hydrogen" to CaseToggle.CASE3_HYDROGEN,
            "+ Storage" to CaseToggle.CASE4_STORAGE
        ).forEach { (label, toggle) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = caseChoice == toggle, onClick = { caseChoice = toggle })
                Text(label)
            }
        }

        Spacer(Modifier.padding(top = 20.dp))
        Button(
            enabled = !isRunning,
            onClick = {
                val base = PjmDefaults.defaultSystem()
                val newGenerators = base.generators.map { g ->
                    g.copy(
                        costPerMwh = genCosts[g.name]!!.value.toDoubleOrNull() ?: g.costPerMwh,
                        capacityMw = genCaps[g.name]!!.value.toDoubleOrNull() ?: g.capacityMw
                    )
                }
                val newRenewables = base.renewables.map { r ->
                    when (r.kind) {
                        com.gridsim.app.model.Renewable.Kind.SOLAR -> r.copy(capacityMw = solarCapacity.toDoubleOrNull() ?: r.capacityMw)
                        com.gridsim.app.model.Renewable.Kind.WIND -> r.copy(capacityMw = windCapacity.toDoubleOrNull() ?: r.capacityMw)
                    }
                }
                val newLines = base.lines.map { l ->
                    if (l.a == 4 && l.b == 5) l.copy(ratingMw = tieLineRating.toDoubleOrNull() ?: l.ratingMw) else l
                }
                val newDemands = PjmDefaults.demands(peakDemand.toDoubleOrNull() ?: 1100.0)
                val newStorages = PjmDefaults.defaultStorages(storageTech, storagePower.toDoubleOrNull() ?: 150.0)

                vm.updateSystem {
                    base.copy(
                        generators = newGenerators,
                        renewables = newRenewables,
                        lines = newLines,
                        demands = newDemands,
                        storages = newStorages,
                        carbonPricePerTon = carbonPrice.toDoubleOrNull() ?: 0.0
                    )
                }
                vm.setCase(caseChoice)
                vm.runSimulation()
            }
        ) {
            Text(if (isRunning) "Solving..." else "Run simulation")
        }
        if (isRunning) {
            Spacer(Modifier.padding(top = 12.dp))
            CircularProgressIndicator()
        }
    }

    LaunchedEffect(result, isRunning) {
        if (result != null && !isRunning) {
            nav.navigate("results")
        }
    }
}
