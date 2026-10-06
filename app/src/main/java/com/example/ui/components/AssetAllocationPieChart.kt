package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.HoldingEntity
import com.example.ui.theme.GainGreen
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val sliceColors = listOf(
    Color(0xFF6750A4),
    Color(0xFF0284C7),
    Color(0xFF16A34A),
    Color(0xFFEAB308),
    Color(0xFFDC2626),
    Color(0xFF9333EA),
    Color(0xFFEA580C)
)

@Composable
fun AssetAllocationPieChart(
    holdings: List<HoldingEntity>,
    virtualCash: Double,
    modifier: Modifier = Modifier
) {
    val totalEquityValue = holdings.sumOf { it.currentValue }
    val grandTotal = totalEquityValue + virtualCash
    if (grandTotal <= 0) return

    val items = mutableListOf<Pair<String, Double>>()
    items.add("Virtual Cash" to virtualCash)
    holdings.forEach {
        items.add(it.symbol to it.currentValue)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Donut Chart Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var startAngle = -90f
                items.forEachIndexed { index, pair ->
                    val sweepAngle = ((pair.second / grandTotal) * 360f).toFloat()
                    val color = sliceColors[index % sliceColors.size]

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${items.size}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "Assets",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        // Legend Column
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.take(4).forEachIndexed { index, pair ->
                val pct = (pair.second / grandTotal) * 100.0
                val color = sliceColors[index % sliceColors.size]

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        text = pair.first,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.widthIn(max = 85.dp),
                        maxLines = 1
                    )
                    Text(
                        text = "${String.format("%.1f", pct)}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
