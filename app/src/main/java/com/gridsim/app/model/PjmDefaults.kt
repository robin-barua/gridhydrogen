package com.gridsim.app.model

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Default PJM 5-bus test system, parameterized from the paper:
 *  - Table 1: conventional generators (G1..G4)
 *  - Table 2: storage technology parameters (Battery / Pumped hydro / CAES)
 *  - Sec. 3.2: 720 MW wind at bus 5, 300 MW solar at bus 3, 150 MW electrolyzers
 *    co-located at buses 3 and 5, load up to 1100 MW at buses 2/3/4, 4-5 tie
 *    line rated 240 MVA (the binding corridor), all other lines 400 MVA.
 *
 * The paper does not publish the exact hourly wind/solar/load time series or
 * line reactances used to produce its figures, so the 24-hour shapes below
 * are representative profiles built to match the qualitative pattern shown
 * in Fig. 3 (midday solar bell curve, evening-peaking wind and load). Every
 * value here is editable in the app's Custom screen.
 */
object PjmDefaults {

    fun hourlyDemandShape(): DoubleArray = DoubleArray(24) { h ->
        // dips ~0.5 overnight/midday, peaks ~1.0 in the evening (hour 20-21)
        val base = 0.6 + 0.15 * sin(PI * (h - 4) / 12.0).coerceIn(-1.0, 1.0)
        val eveningBump = max(0.0, 0.32 * sin(PI * (h - 15) / 10.0))
        (base + eveningBump).coerceIn(0.42, 1.0)
    }

    fun hourlySolarShape(): DoubleArray = DoubleArray(24) { h ->
        if (h in 6..18) max(0.0, sin(PI * (h - 6) / 12.0)) else 0.0
    }

    fun hourlyWindShape(): DoubleArray = DoubleArray(24) { h ->
        // moderate overnight, dips midday, strong evening peak (matches Fig. 3)
        val base = 0.55 + 0.15 * sin(PI * (h - 2) / 12.0)
        val eveningPeak = max(0.0, 0.35 * sin(PI * (h - 14) / 10.0))
        val middaySag = max(0.0, 0.25 * sin(PI * (h - 9) / 6.0))
        (base + eveningPeak - middaySag).coerceIn(0.2, 1.0)
    }

    fun buses(): List<Bus> = listOf(
        Bus(1, "Bus 1"), Bus(2, "Bus 2"), Bus(3, "Bus 3"), Bus(4, "Bus 4"), Bus(5, "Bus 5")
    )

    fun lines(): List<Line> = listOf(
        Line(1, 1, 2, reactanceX = 0.0281, ratingMw = 400.0),
        Line(2, 1, 4, reactanceX = 0.0304, ratingMw = 400.0),
        Line(3, 1, 5, reactanceX = 0.0064, ratingMw = 400.0),
        Line(4, 2, 3, reactanceX = 0.0108, ratingMw = 400.0),
        Line(5, 3, 4, reactanceX = 0.0297, ratingMw = 400.0),
        Line(6, 4, 5, reactanceX = 0.0297, ratingMw = 240.0) // the binding 4-5 tie line
    )

    fun generators(): List<Generator> = listOf(
        Generator("G1", bus = 1, capacityMw = 40.0, costPerMwh = 14.0, rampMwPerHour = 40.0, emissionsTonPerMwh = 0.30),
        Generator("G2", bus = 1, capacityMw = 170.0, costPerMwh = 15.0, rampMwPerHour = 100.0, emissionsTonPerMwh = 0.30),
        Generator("G3", bus = 3, capacityMw = 700.0, costPerMwh = 30.0, rampMwPerHour = 350.0, emissionsTonPerMwh = 0.25),
        Generator("G4", bus = 4, capacityMw = 200.0, costPerMwh = 40.0, rampMwPerHour = 200.0, emissionsTonPerMwh = 0.45)
    )

    fun renewables(): List<Renewable> = listOf(
        Renewable("Solar PV", bus = 3, kind = Renewable.Kind.SOLAR, capacityMw = 300.0, hourlyProfile = hourlySolarShape()),
        Renewable("Wind farm", bus = 5, kind = Renewable.Kind.WIND, capacityMw = 720.0, hourlyProfile = hourlyWindShape())
    )

    fun electrolyzers(): List<Electrolyzer> = listOf(
        Electrolyzer("Electrolyzer @ Bus3", bus = 3, capacityMw = 150.0, efficiencyTonPerMwh = 1.0 / 33.6),
        Electrolyzer("Electrolyzer @ Bus5", bus = 5, capacityMw = 150.0, efficiencyTonPerMwh = 1.0 / 33.6)
    )

    /** Default single storage unit for Case 4 (battery, sized as a fraction of renewable capacity). */
    fun defaultStorages(tech: StorageTech = StorageTech.BATTERY, powerRatingMw: Double = 150.0): List<Storage> = listOf(
        Storage("Storage @ Bus5", bus = 5, tech = tech, powerRatingMw = powerRatingMw)
    )

    fun demands(peakTotalMw: Double = 1100.0): List<Demand> {
        val shape = hourlyDemandShape()
        // Split peak across buses 2, 3, 4 roughly 25% / 40% / 35%
        fun scaled(fraction: Double) = DoubleArray(24) { h -> shape[h] * peakTotalMw * fraction }
        return listOf(
            Demand(bus = 2, hourlyMw = scaled(0.25)),
            Demand(bus = 3, hourlyMw = scaled(0.40)),
            Demand(bus = 4, hourlyMw = scaled(0.35))
        )
    }

    fun defaultSystem(): GridSystem = GridSystem(
        hours = 24,
        buses = buses(),
        lines = lines(),
        generators = generators(),
        renewables = renewables(),
        electrolyzers = electrolyzers(),
        storages = defaultStorages(),
        demands = demands(),
        carbonPricePerTon = 0.0,
        renewableIncentivePerMwh = 0.05
    )
}
