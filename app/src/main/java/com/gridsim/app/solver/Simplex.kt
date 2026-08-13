package com.gridsim.app.solver

/**
 * Big-M standard-form simplex solver with dual (shadow price) recovery.
 *
 * Solves:  minimize   c^T x
 *          subject to A x {<=, =, >=} b,   x >= 0
 *
 * Constraint row type: -1 means "<=", 0 means "=", +1 means ">=".
 *
 * This mirrors the LP formulation in the paper (Section 2): decision
 * variables are generation, storage, electrolyzer, and (split-signed)
 * angle/flow variables; the nodal power-balance rows are equalities
 * (type = 0), and their recovered duals ARE the locational marginal
 * prices (LMPs), exactly as in the paper's dual-variable formulation
 * of Eq. (7).
 *
 * The algorithm was first validated standalone in plain Java against
 * hand-checked LPs (including a case with a binding equality and a
 * binding inequality) before being ported here line-for-line.
 */
object Simplex {

    class Result(
        val x: DoubleArray,
        val duals: DoubleArray,
        val obj: Double,
        val feasible: Boolean
    )

    private const val BIG_M = 1.0e7
    private const val EPS = 1.0e-9
    private const val MAX_ITER = 20000

    fun solve(A: Array<DoubleArray>, b: DoubleArray, c: DoubleArray, types: IntArray): Result {
        val m = A.size
        val n = c.size

        // Normalize so every b_i >= 0 (flip row sign + type if needed).
        val Arow = arrayOfNulls<DoubleArray>(m)
        val brow = DoubleArray(m)
        val trow = IntArray(m)
        val flipped = BooleanArray(m)
        for (i in 0 until m) {
            if (b[i] < 0) {
                val row = DoubleArray(n)
                for (j in 0 until n) row[j] = -A[i][j]
                Arow[i] = row
                brow[i] = -b[i]
                trow[i] = -types[i]
                flipped[i] = true
            } else {
                Arow[i] = A[i]
                brow[i] = b[i]
                trow[i] = types[i]
            }
        }

        var extra = 0
        val slackCol = IntArray(m) { -1 }
        val artCol = IntArray(m) { -1 }
        for (i in 0 until m) {
            when (trow[i]) {
                -1 -> { slackCol[i] = n + extra; extra += 1 }
                1 -> { slackCol[i] = n + extra; extra += 1; artCol[i] = n + extra; extra += 1 }
                else -> { artCol[i] = n + extra; extra += 1 }
            }
        }
        val totalCols = n + extra

        val T = Array(m + 1) { DoubleArray(totalCols + 1) }
        val basis = IntArray(m)
        for (i in 0 until m) {
            val row = Arow[i]!!
            for (j in 0 until n) T[i][j] = row[j]
            if (slackCol[i] != -1) T[i][slackCol[i]] = if (trow[i] == -1) 1.0 else -1.0
            if (artCol[i] != -1) {
                T[i][artCol[i]] = 1.0
                basis[i] = artCol[i]
            } else {
                basis[i] = slackCol[i]
            }
            T[i][totalCols] = brow[i]
        }

        val cost = DoubleArray(totalCols)
        for (j in 0 until n) cost[j] = c[j]
        for (i in 0 until m) if (artCol[i] != -1) cost[artCol[i]] = BIG_M
        for (j in 0 until totalCols) T[m][j] = cost[j]
        T[m][totalCols] = 0.0

        for (i in 0 until m) {
            val cb = cost[basis[i]]
            if (cb != 0.0) {
                for (j in 0..totalCols) T[m][j] -= cb * T[i][j]
            }
        }

        for (iter in 0 until MAX_ITER) {
            var enter = -1
            var best = -EPS
            for (j in 0 until totalCols) {
                if (T[m][j] < best) { best = T[m][j]; enter = j }
            }
            if (enter == -1) break

            var leave = -1
            var bestRatio = Double.POSITIVE_INFINITY
            for (i in 0 until m) {
                if (T[i][enter] > EPS) {
                    val ratio = T[i][totalCols] / T[i][enter]
                    if (ratio < bestRatio - 1e-12) { bestRatio = ratio; leave = i }
                }
            }
            if (leave == -1) {
                return Result(DoubleArray(n), DoubleArray(m), 0.0, false) // unbounded
            }

            val piv = T[leave][enter]
            for (j in 0..totalCols) T[leave][j] /= piv
            for (i in 0..m) {
                if (i == leave) continue
                val factor = T[i][enter]
                if (factor != 0.0) {
                    for (j in 0..totalCols) T[i][j] -= factor * T[leave][j]
                }
            }
            basis[leave] = enter
        }

        var artSum = 0.0
        for (i in 0 until m) {
            if (artCol[i] != -1) {
                for (r in 0 until m) if (basis[r] == artCol[i]) artSum += T[r][totalCols]
            }
        }
        val feasible = artSum < 1e-4

        val x = DoubleArray(n)
        for (i in 0 until m) if (basis[i] < n) x[basis[i]] = T[i][totalCols]

        var obj = 0.0
        for (j in 0 until n) obj += c[j] * x[j]

        val duals = DoubleArray(m)
        for (i in 0 until m) {
            val col: Int
            val k: Double
            if (trow[i] == -1) { col = slackCol[i]; k = 0.0 } else { col = artCol[i]; k = BIG_M }
            var dual = k - T[m][col]
            if (flipped[i]) dual = -dual
            duals[i] = dual
        }

        return Result(x, duals, obj, feasible)
    }
}
