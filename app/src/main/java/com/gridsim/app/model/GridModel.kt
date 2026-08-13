package com.gridsim.app.model

/** A transmission bus. id is 1-based; bus 1 is the angle reference (theta = 0). */
data class Bus(val id: Int, val name: String)

/** A transmission line between buses a and b, reactance x (p.u.), thermal rating F (MW). */
data class Line(val id: Int, val a: Int, val b: Int, val reactanceX: Double, val ratingMw: Double)

/** Conventional (thermal) generator: capacity, linear $/MWh cost, ramp limit, emission rate. */
data class Generator(
    val name: String,
    val bus: Int,
    val capacityMw: Double,
    val costPerMwh: Double,
    val rampMwPerHour: Double,
    val emissionsTonPerMwh: Double
)

/** Renewable plant (solar or wind). hourlyAvailMw[t] is the max available power that hour. */
data class Renewable(
    val name: String,
    val bus: Int,
    val kind: Kind,
    val capacityMw: Double,
    val hourlyProfile: DoubleArray // fraction of capacity available, length 24, 0..1
) {
    enum class Kind { SOLAR, WIND }
}

enum class StorageTech(val label: String, val durationHours: Double, val chargeEff: Double, val dischargeEff: Double, val lcosPerMwhDay: Double) {
    BATTERY("Battery", 2.0, 0.95, 0.95, 230.0),
    PUMPED_HYDRO("Pumped hydro", 8.0, 0.87, 0.85, 130.0),
    CAES("Compressed air (CAES)", 12.0, 0.85, 0.70, 111.0)
}

/** A storage unit sited at a bus, sized as nominal power rating (MW). Energy cap = duration*power. */
data class Storage(val name: String, val bus: Int, val tech: StorageTech, val powerRatingMw: Double)

/** Water electrolyzer co-located with a renewable bus. */
data class Electrolyzer(val name: String, val bus: Int, val capacityMw: Double, val efficiencyTonPerMwh: Double)

/** Demand at a bus for each hour of the day (MW), length 24. */
data class Demand(val bus: Int, val hourlyMw: DoubleArray)

/** Which optional components are active — mirrors the paper's Case 1..4 progression. */
data class CaseToggle(
    val includeRenewables: Boolean,
    val includeHydrogen: Boolean,
    val includeStorage: Boolean
) {
    companion object {
        val CASE1_CONVENTIONAL = CaseToggle(includeRenewables = false, includeHydrogen = false, includeStorage = false)
        val CASE2_RENEWABLES = CaseToggle(includeRenewables = true, includeHydrogen = false, includeStorage = false)
        val CASE3_HYDROGEN = CaseToggle(includeRenewables = true, includeHydrogen = true, includeStorage = false)
        val CASE4_STORAGE = CaseToggle(includeRenewables = true, includeHydrogen = true, includeStorage = true)
    }
}

/** Full system definition — everything the LP builder needs. Editable in the Custom screen. */
data class GridSystem(
    val hours: Int = 24,
    val buses: List<Bus>,
    val lines: List<Line>,
    val generators: List<Generator>,
    val renewables: List<Renewable>,
    val electrolyzers: List<Electrolyzer>,
    val storages: List<Storage>,
    val demands: List<Demand>,
    val carbonPricePerTon: Double = 0.0,
    val renewableIncentivePerMwh: Double = 0.05, // small negative weight w_re in the objective (paper Eq. 1)
    val h2LhvMwhPerTon: Double = 33.6
)
