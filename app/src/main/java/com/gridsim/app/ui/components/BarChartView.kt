package com.gridsim.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BarChartView(
    title: String,
    labels: List<String>,
    values: List<Double>,
    color: Color = Color(0xFF1565C0),
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (values.isEmpty()) { Text("No data", fontSize = 12.sp); return@Column }
        val maxV = (values.maxOrNull() ?: 1.0).let { if (it <= 0) 1.0 else it }
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 4.dp)) {
            val w = size.width
            val h = size.height
            val n = values.size
            val gap = w * 0.05f / n
            val barWidth = (w - gap * (n + 1)) / n
            values.forEachIndexed { i, v ->
                val barH = (v / maxV * (h - 16)).toFloat().coerceAtLeast(0f)
                val x = gap + i * (barWidth + gap)
                drawRect(color, topLeft = Offset(x, h - barH), size = Size(barWidth, barH))
            }
        }
        Row(labels, values, maxV)
    }
}

@Composable
private fun Row(labels: List<String>, values: List<Double>, maxV: Double) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            Text(
                "$l\n${"%.1f".format(values.getOrElse(i) { 0.0 })}",
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
