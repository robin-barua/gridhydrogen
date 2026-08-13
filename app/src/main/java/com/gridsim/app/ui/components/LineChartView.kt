package com.gridsim.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val palette = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFEF6C00),
    Color(0xFF8E24AA), Color(0xFFC62828), Color(0xFF00838F)
)

/** A minimal multi-series line chart. Each series must have the same length. */
@Composable
fun LineChartView(
    title: String,
    series: List<Pair<String, DoubleArray>>,
    modifier: Modifier = Modifier,
    yLabel: String = ""
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Row {
            series.forEachIndexed { i, (name, _) ->
                Text(
                    "● $name  ",
                    color = palette[i % palette.size],
                    fontSize = 11.sp,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        if (series.isEmpty() || series.all { it.second.isEmpty() }) {
            Text("No data", fontSize = 12.sp)
            return@Column
        }
        val allValues = series.flatMap { it.second.toList() }
        val minV = (allValues.minOrNull() ?: 0.0).let { if (it > 0) 0.0 else it }
        val maxV = (allValues.maxOrNull() ?: 1.0).let { if (it <= minV) minV + 1.0 else it }

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).padding(top = 4.dp)) {
            val w = size.width
            val h = size.height
            val zeroY = h - ((0.0 - minV) / (maxV - minV) * h).toFloat()
            drawLine(Color.LightGray, Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 1f)

            series.forEachIndexed { si, (_, values) ->
                if (values.size < 2) return@forEachIndexed
                val color = palette[si % palette.size]
                val stepX = w / (values.size - 1).coerceAtLeast(1)
                val points = values.mapIndexed { i, v ->
                    val x = i * stepX
                    val y = h - ((v - minV) / (maxV - minV) * h).toFloat()
                    Offset(x, y)
                }
                for (i in 0 until points.size - 1) {
                    drawLine(color, points[i], points[i + 1], strokeWidth = 3f)
                }
            }
        }
        Text("min ${"%.1f".format(minV)}  max ${"%.1f".format(maxV)} $yLabel", fontSize = 10.sp, color = Color.Gray)
    }
}
