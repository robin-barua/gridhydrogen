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
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gridsim.app.solver.SimulationResult
import com.gridsim.app.ui.AppViewModel
import com.gridsim.app.ui.components.LineChartView

@Composable
fun ResultsScreen(nav: NavHostController, vm: AppViewModel) {
    val result by vm.result.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Results", style = MaterialTheme.typography.headlineSmall)
        val r = result
        if (r == null) {
            Text("No simulation run yet.")
            Spacer(Modifier.padding(top = 12.dp))
            Button(onClick = { nav.popBackStack() }) { Text("Back") }
            return@Column
        }
        if (!r.feasible) {
            Text("The LP was infeasible with these parameters — try relaxing a constraint (e.g. raise a line rating or generator capacity).",
                color = androidx.compose.ui.graphics.Color.Red)
        }

        MetricsTable(r)

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        LineChartView(
            title = "Hourly dispatch (MW)",
            series = (r.genDispatch.map { it.name to it.values } +
                r.renewableUsed.map { it.name to it.values } +
                listOf("Total load" to r.totalLoadSeries)),
            yLabel = "MW"
        )

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        LineChartView(
            title = "Locational marginal prices (\$/MWh)",
            series = r.lmpByBus.map { it.name to it.values },
            yLabel = "\$/MWh"
        )

        if (r.electrolyzerDraw.isNotEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            LineChartView(
                title = "Electrolyzer draw (MW)",
                series = r.electrolyzerDraw.map { it.name to it.values },
                yLabel = "MW"
            )
        }

        if (r.storageSoc.isNotEmpty()) {
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            LineChartView(
                title = "Storage state of charge (MWh)",
                series = r.storageSoc.map { it.name to it.values },
                yLabel = "MWh"
            )
        }

        Divider(modifier = Modifier.padding(vertical = 12.dp))
        LineChartView(
            title = "Line flows (MW)",
            series = r.lineFlow.map { it.name to it.values },
            yLabel = "MW"
        )

        Spacer(Modifier.padding(top = 16.dp))
        Button(onClick = { nav.popBackStack() }) { Text("Run another case") }
    }
}

@Composable
private fun MetricsTable(r: SimulationResult) {
    val m = r.metrics
    val rows = listOf(
        "Renewable penetration" to "%.1f %%".format(m.renewablePenetrationPct),
        "Renewable used" to "%.0f MWh/day".format(m.renewableUsedMwh),
        "Curtailed" to "%.0f MWh/day".format(m.curtailedMwh),
        "Emissions" to "%.0f tCO2/day".format(m.emissionsTonPerDay),
        "Emissions reduction" to (m.emissionsReductionPct?.let { "%.1f %%".format(it) } ?: "—"),
        "Operational cost" to "%.1f k\$/day".format(m.operationalCostK),
        "Storage cost" to "%.1f k\$/day".format(m.storageCostK),
        "Net cost" to "%.1f k\$/day".format(m.netCostK),
        "Green H2" to "%.2f tons/day".format(m.hydrogenTonsPerDay),
        "Average LMP" to "%.2f \$/MWh".format(m.averageLmp)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(label, modifier = Modifier.weight(1f))
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
