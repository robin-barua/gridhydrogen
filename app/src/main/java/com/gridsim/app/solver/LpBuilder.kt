package com.gridsim.app.solver

import com.gridsim.app.model.CaseToggle
import com.gridsim.app.model.GridSystem
import kotlin.math.max

/**
 * Builds and solves the multi-period LP described in Section 2 of the paper:
 *  - Objective (1): min sum thermal cost + small renewable-dispatch incentive
 *  - Gen/ramp (2)-(3), renewable availability (4)
 *  - DC flow (5)-(6) and nodal balance (7) -- the dual of (7) is the LMP
 *  - Hydrogen (8)-(10), storage (11)-(13)
 *
 * Free variables (bus angles theta, line flows f) are represented as the
 * difference of two non-negative variables (plus/minus split) since the
 * underlying Simplex solver requires x >= 0, matching standard LP practice.
 */
object LpBuilder {

    private class Indexer {
        private var next = 0
        val map = HashMap<String, Int>()
        fun idx(key: String): Int = map.getOrPut(key) { next++ }
        val size: Int get() = next
    }

    private class Row(val coeffs: HashMap<Int, Double> = HashMap(), var rhs: Double = 0.0, var type: Int = 0, var tag: String? = null) {
        fun add(v: Int, c: Double) { coeffs[v] = (coeffs[v] ?: 0.0) + c }
    }

    fun solve(system: GridSystem, case: CaseToggle, baselineEmissionsTon: Double? = null): SimulationResult {
        val T = system.hours
        val refBus = system.buses.minOf { it.id }
        val activeRenewables = if (case.includeRenewables) system.renewables else emptyList()
        val activeElectrolyzers = if (case.includeHydrogen) system.electrolyzers else emptyList()
        val activeStorages = if (case.includeStorage) system.storages else emptyList()

        val ix = Indexer()
        val rows = ArrayList<Row>()

        fun demandAt(bus: Int, t: Int): Double =
            system.demands.filter { it.bus == bus }.sumOf { it.hourlyMw[t] }

        // ---- Generator capacity + ramp ----
        for (g in system.generators) {
            for (t in 0 until T) {
                val v = ix.idx("gen:${g.name}:$t")
                rows.add(Row().apply { add(v, 1.0); rhs = g.capacityMw; type = -1 })
                if (t > 0) {
                    val vp = ix.idx("gen:${g.name}:${t - 1}")
                    rows.add(Row().apply { add(v, 1.0); add(vp, -1.0); rhs = g.rampMwPerHour; type = -1 })
                    rows.add(Row().apply { add(vp, 1.0); add(v, -1.0); rhs = g.rampMwPerHour; type = -1 })
                }
            }
        }

        // ---- Renewable availability ----
        for (r in activeRenewables) {
            for (t in 0 until T) {
                val v = ix.idx("re:${r.name}:$t")
                val avail = r.capacityMw * r.hourlyProfile[t]
                rows.add(Row().apply { add(v, 1.0); rhs = avail; type = -1 })
            }
        }

        // ---- Electrolyzer capacity + hydrogen accumulation ----
        for (h in activeElectrolyzers) {
            for (t in 0 until T) {
                val v = ix.idx("elz:${h.name}:$t")
                rows.add(Row().apply { add(v, 1.0); rhs = h.capacityMw; type = -1 })
                val hv = ix.idx("h2:${h.name}:$t")
                if (t == 0) {
                    rows.add(Row().apply { add(hv, 1.0); add(v, -h.efficiencyTonPerMwh); rhs = 0.0; type = 0 })
                } else {
                    val hvPrev = ix.idx("h2:${h.name}:${t - 1}")
                    rows.add(Row().apply { add(hv, 1.0); add(hvPrev, -1.0); add(v, -h.efficiencyTonPerMwh); rhs = 0.0; type = 0 })
                }
            }
        }

        // ---- Storage bounds + dynamics ----
        val storageEnergyCap = HashMap<String, Double>()
        for (s in activeStorages) {
            val energyCap = s.tech.durationHours * s.powerRatingMw
            storageEnergyCap[s.name] = energyCap
            val eInit = 0.5 * energyCap
            for (t in 0 until T) {
                val ch = ix.idx("ch:${s.name}:$t")
                val dis = ix.idx("dis:${s.name}:$t")
                val soc = ix.idx("soc:${s.name}:$t")
                rows.add(Row().apply { add(ch, 1.0); rhs = s.powerRatingMw; type = -1 })
                rows.add(Row().apply { add(dis, 1.0); rhs = s.powerRatingMw; type = -1 })
                rows.add(Row().apply { add(soc, 1.0); rhs = energyCap; type = -1 })
                if (t == 0) {
                    rows.add(Row().apply {
                        add(soc, 1.0); add(ch, -s.tech.chargeEff); add(dis, 1.0 / s.tech.dischargeEff)
                        rhs = eInit; type = 0
                    })
                } else {
                    val socPrev = ix.idx("soc:${s.name}:${t - 1}")
                    rows.add(Row().apply {
                        add(soc, 1.0); add(socPrev, -1.0); add(ch, -s.tech.chargeEff); add(dis, 1.0 / s.tech.dischargeEff)
                        rhs = 0.0; type = 0
                    })
                }
            }
            // cyclic boundary: soc at last hour returns to eInit
            val socLast = ix.idx("soc:${s.name}:${T - 1}")
            rows.add(Row().apply { add(socLast, 1.0); rhs = eInit; type = 0 })
        }

        // ---- DC line flow definition + thermal limits ----
        fun thetaPlus(bus: Int, t: Int) = if (bus == refBus) null else ix.idx("thP:$bus:$t")
        fun thetaMinus(bus: Int, t: Int) = if (bus == refBus) null else ix.idx("thM:$bus:$t")

        for (l in system.lines) {
            for (t in 0 until T) {
                val fp = ix.idx("flP:${l.id}:$t")
                val fm = ix.idx("flM:${l.id}:$t")
                val row = Row().apply { add(fp, 1.0); add(fm, -1.0); rhs = 0.0; type = 0 }
                thetaPlus(l.a, t)?.let { row.add(it, -1.0 / l.reactanceX) }
                thetaMinus(l.a, t)?.let { row.add(it, 1.0 / l.reactanceX) }
                thetaPlus(l.b, t)?.let { row.add(it, 1.0 / l.reactanceX) }
                thetaMinus(l.b, t)?.let { row.add(it, -1.0 / l.reactanceX) }
                rows.add(row)
                rows.add(Row().apply { add(fp, 1.0); rhs = l.ratingMw; type = -1 })
                rows.add(Row().apply { add(fm, 1.0); rhs = l.ratingMw; type = -1 })
            }
        }

        // ---- Nodal power balance (Eq. 7) -- dual = LMP ----
        for (n in system.buses) {
            for (t in 0 until T) {
                val row = Row()
                for (g in system.generators.filter { it.bus == n.id }) row.add(ix.idx("gen:${g.name}:$t"), 1.0)
                for (r in activeRenewables.filter { it.bus == n.id }) row.add(ix.idx("re:${r.name}:$t"), 1.0)
                for (s in activeStorages.filter { it.bus == n.id }) {
                    row.add(ix.idx("dis:${s.name}:$t"), 1.0)
                    row.add(ix.idx("ch:${s.name}:$t"), -1.0)
                }
                for (h in activeElectrolyzers.filter { it.bus == n.id }) row.add(ix.idx("elz:${h.name}:$t"), -1.0)
                for (l in system.lines.filter { it.b == n.id }) { row.add(ix.idx("flP:${l.id}:$t"), 1.0); row.add(ix.idx("flM:${l.id}:$t"), -1.0) }
                for (l in system.lines.filter { it.a == n.id }) { row.add(ix.idx("flP:${l.id}:$t"), -1.0); row.add(ix.idx("flM:${l.id}:$t"), 1.0) }
                row.rhs = demandAt(n.id, t)
                row.type = 0
                row.tag = "balance:${n.id}:$t"
                rows.add(row)
            }
        }

        // ---- Objective ----
        val n = ix.size
        val c = DoubleArray(n)
        for (g in system.generators) for (t in 0 until T) c[ix.idx("gen:${g.name}:$t")] += g.costPerMwh
        for (r in activeRenewables) for (t in 0 until T) c[ix.idx("re:${r.name}:$t")] += -system.renewableIncentivePerMwh

        // ---- Assemble dense A, b, types ----
        val m = rows.size
        val A = Array(m) { DoubleArray(n) }
        val b = DoubleArray(m)
        val types = IntArray(m)
        val tags = arrayOfNulls<String>(m)
        for (i in 0 until m) {
            val row = rows[i]
            for ((v, coef) in row.coeffs) A[i][v] = coef
            b[i] = row.rhs
            types[i] = row.type
            tags[i] = row.tag
        }

        val result = Simplex.solve(A, b, c, types)

        // ---- Post-process ----
        fun seriesFor(prefix: String, name: String): DoubleArray = DoubleArray(T) { t ->
            ix.map["$prefix:$name:$t"]?.let { result.x[it] } ?: 0.0
        }

        val genDispatch = system.generators.map { SeriesByName(it.name, seriesFor("gen", it.name)) }
        val renewableUsed = activeRenewables.map { SeriesByName(it.name, seriesFor("re", it.name)) }
        val renewableAvailable = activeRenewables.map { r ->
            SeriesByName(r.name, DoubleArray(T) { t -> r.capacityMw * r.hourlyProfile[t] })
        }
        val electrolyzerDraw = activeElectrolyzers.map { SeriesByName(it.name, seriesFor("elz", it.name)) }
        val hydrogenCumulative = activeElectrolyzers.map { SeriesByName(it.name, seriesFor("h2", it.name)) }
        val storageCharge = activeStorages.map { SeriesByName(it.name, seriesFor("ch", it.name)) }
        val storageDischarge = activeStorages.map { SeriesByName(it.name, seriesFor("dis", it.name)) }
        val storageSoc = activeStorages.map { SeriesByName(it.name, seriesFor("soc", it.name)) }
        val lineFlow = system.lines.map { l ->
            SeriesByName("L${l.id} (${l.a}-${l.b})", DoubleArray(T) { t ->
                val fp = ix.map["flP:${l.id}:$t"]?.let { result.x[it] } ?: 0.0
                val fm = ix.map["flM:${l.id}:$t"]?.let { result.x[it] } ?: 0.0
                fp - fm
            })
        }

        val lmpByBus = system.buses.map { bus ->
            SeriesByName(bus.name, DoubleArray(T) { t ->
                val rowIdx = tags.indexOf("balance:${bus.id}:$t")
                if (rowIdx >= 0) result.duals[rowIdx] else 0.0
            })
        }

        val totalLoadSeries = DoubleArray(T) { t -> system.buses.sumOf { demandAt(it.id, t) } }

        val totalRenewableAvailMwh = renewableAvailable.sumOf { it.values.sum() }
        val totalRenewableUsedMwh = renewableUsed.sumOf { it.values.sum() }
        val curtailedMwh = max(0.0, totalRenewableAvailMwh - totalRenewableUsedMwh)
        val totalLoadMwh = totalLoadSeries.sum()
        val renewablePenetrationPct = if (totalLoadMwh > 0) 100.0 * totalRenewableUsedMwh / totalLoadMwh else 0.0

        val emissionsTon = system.generators.sumOf { g ->
            val s = seriesFor("gen", g.name)
            s.sum() * g.emissionsTonPerMwh
        }
        val operationalCostK = system.generators.sumOf { g ->
            val s = seriesFor("gen", g.name)
            s.sum() * g.costPerMwh
        } / 1000.0

        val storageCostK = activeStorages.sumOf { s -> s.tech.lcosPerMwhDay * (storageEnergyCap[s.name] ?: 0.0) } / 1000.0

        val carbonCreditK = if (baselineEmissionsTon != null) {
            system.carbonPricePerTon * max(0.0, baselineEmissionsTon - emissionsTon) / 1000.0
        } else 0.0

        val netCostK = operationalCostK + storageCostK - carbonCreditK

        val hydrogenTonsPerDay = hydrogenCumulative.sumOf { it.values.lastOrNull() ?: 0.0 }

        val allLmpValues = lmpByBus.flatMap { it.values.toList() }
        val avgLmp = if (allLmpValues.isNotEmpty()) allLmpValues.average() else 0.0

        val emissionsReductionPct = baselineEmissionsTon?.let {
            if (it > 0) 100.0 * (it - emissionsTon) / it else null
        }

        val metrics = SummaryMetrics(
            renewablePenetrationPct = renewablePenetrationPct,
            renewableUsedMwh = totalRenewableUsedMwh,
            curtailedMwh = curtailedMwh,
            emissionsTonPerDay = emissionsTon,
            emissionsReductionPct = emissionsReductionPct,
            operationalCostK = operationalCostK,
            storageCostK = storageCostK,
            netCostK = netCostK,
            hydrogenTonsPerDay = hydrogenTonsPerDay,
            averageLmp = avgLmp
        )

        return SimulationResult(
            feasible = result.feasible,
            hours = T,
            genDispatch = genDispatch,
            renewableUsed = renewableUsed,
            renewableAvailable = renewableAvailable,
            electrolyzerDraw = electrolyzerDraw,
            hydrogenCumulative = hydrogenCumulative,
            storageCharge = storageCharge,
            storageDischarge = storageDischarge,
            storageSoc = storageSoc,
            lineFlow = lineFlow,
            lmpByBus = lmpByBus,
            totalLoadSeries = totalLoadSeries,
            metrics = metrics
        )
    }
}
