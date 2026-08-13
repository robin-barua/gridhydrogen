package com.gridsim.app.solver

data class SeriesByName(val name: String, val values: DoubleArray)

data class SummaryMetrics(
    val renewablePenetrationPct: Double,
    val renewableUsedMwh: Double,
    val curtailedMwh: Double,
    val emissionsTonPerDay: Double,
    val emissionsReductionPct: Double?,
    val operationalCostK: Double,
    val storageCostK: Double,
    val netCostK: Double,
    val hydrogenTonsPerDay: Double,
    val averageLmp: Double
)

data class SimulationResult(
    val feasible: Boolean,
    val hours: Int,
    val genDispatch: List<SeriesByName>,
    val renewableUsed: List<SeriesByName>,
    val renewableAvailable: List<SeriesByName>,
    val electrolyzerDraw: List<SeriesByName>,
    val hydrogenCumulative: List<SeriesByName>,
    val storageCharge: List<SeriesByName>,
    val storageDischarge: List<SeriesByName>,
    val storageSoc: List<SeriesByName>,
    val lineFlow: List<SeriesByName>,
    val lmpByBus: List<SeriesByName>, // one series per bus, length = hours
    val totalLoadSeries: DoubleArray,
    val metrics: SummaryMetrics
)
