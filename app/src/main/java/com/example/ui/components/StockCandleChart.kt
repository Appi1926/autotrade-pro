package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.CandleStick
import com.example.ui.theme.GainGreen
import com.example.ui.theme.LossRed
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

@Composable
fun StockCandleChart(
    candles: List<CandleStick>,
    modifier: Modifier = Modifier,
    isCandleMode: Boolean = true,
    showSMA: Boolean = true
) {
    if (candles.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No Chart Data Available", color = TextSecondary, fontSize = 13.sp)
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        // Tooltip header on touch
        val activeCandle = selectedIndex?.let { if (it in candles.indices) candles[it] else null } ?: candles.lastOrNull()

        if (activeCandle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "O: ₹${activeCandle.open}  H: ₹${activeCandle.high}  L: ₹${activeCandle.low}  C: ₹${activeCandle.close}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeCandle.close >= activeCandle.open) GainGreen else LossRed
                )
                Text(
                    text = "Vol: ${activeCandle.volume}",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFFFAFAFA), RoundedCornerShape(16.dp))
                .padding(8.dp)
                .pointerInput(candles) {
                    detectTapGestures { offset ->
                        val count = candles.size
                        if (count > 0) {
                            val slotWidth = size.width / count
                            val index = (offset.x / slotWidth).toInt().coerceIn(0, count - 1)
                            selectedIndex = index
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val count = candles.size
                if (count == 0) return@Canvas

                val minPrice = candles.minOf { it.low }
                val maxPrice = candles.maxOf { it.high }
                val priceRange = if (maxPrice - minPrice > 0.001) maxPrice - minPrice else 1.0

                val maxVolume = candles.maxOfOrNull { it.volume } ?: 1L
                val volHeight = h * 0.22f
                val chartHeight = h * 0.72f

                val slotWidth = w / count
                val candleWidth = max(2f, slotWidth * 0.65f)

                // Draw Horizontal Gridlines
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val y = chartHeight * (i.toFloat() / gridSteps)
                    drawLine(
                        color = Color(0xFFE2E8F0),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }

                // Draw Volume bars at bottom
                candles.forEachIndexed { i, c ->
                    val x = i * slotWidth + (slotWidth / 2f)
                    val vHeight = (c.volume.toFloat() / maxVolume) * volHeight
                    val vColor = if (c.close >= c.open) GainGreen.copy(alpha = 0.25f) else LossRed.copy(alpha = 0.25f)

                    drawRect(
                        color = vColor,
                        topLeft = Offset(x - (candleWidth / 2f), h - vHeight),
                        size = Size(candleWidth, vHeight)
                    )
                }

                if (isCandleMode) {
                    // Draw Candlesticks (Wick and Real Body)
                    candles.forEachIndexed { i, c ->
                        val x = i * slotWidth + (slotWidth / 2f)
                        val highY = chartHeight - ((c.high - minPrice) / priceRange * chartHeight).toFloat()
                        val lowY = chartHeight - ((c.low - minPrice) / priceRange * chartHeight).toFloat()
                        val openY = chartHeight - ((c.open - minPrice) / priceRange * chartHeight).toFloat()
                        val closeY = chartHeight - ((c.close - minPrice) / priceRange * chartHeight).toFloat()

                        val isUp = c.close >= c.open
                        val color = if (isUp) GainGreen else LossRed

                        // Wick line
                        drawLine(
                            color = color,
                            start = Offset(x, highY),
                            end = Offset(x, lowY),
                            strokeWidth = 2f
                        )

                        // Body rect
                        val topY = min(openY, closeY)
                        val bodyH = max(2f, kotlin.math.abs(closeY - openY))

                        drawRect(
                            color = color,
                            topLeft = Offset(x - (candleWidth / 2f), topY),
                            size = Size(candleWidth, bodyH)
                        )
                    }
                } else {
                    // Draw Line Chart
                    val linePath = Path()
                    candles.forEachIndexed { i, c ->
                        val x = i * slotWidth + (slotWidth / 2f)
                        val y = chartHeight - ((c.close - minPrice) / priceRange * chartHeight).toFloat()
                        if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                    }
                    drawPath(
                        path = linePath,
                        color = PrimaryPurple,
                        style = Stroke(width = 3f)
                    )
                }

                // Draw SMA 20 Overlay
                if (showSMA && candles.size >= 10) {
                    val smaPeriod = min(20, candles.size / 2)
                    val smaPath = Path()
                    var started = false

                    for (i in (smaPeriod - 1) until candles.size) {
                        val sub = candles.subList(i - smaPeriod + 1, i + 1)
                        val avg = sub.map { it.close }.average()
                        val x = i * slotWidth + (slotWidth / 2f)
                        val y = chartHeight - ((avg - minPrice) / priceRange * chartHeight).toFloat()

                        if (!started) {
                            smaPath.moveTo(x, y)
                            started = true
                        } else {
                            smaPath.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = smaPath,
                        color = Color(0xFFEAB308), // Amber SMA
                        style = Stroke(width = 2f)
                    )
                }

                // Selected Crosshair
                selectedIndex?.let { idx ->
                    if (idx in candles.indices) {
                        val cx = idx * slotWidth + (slotWidth / 2f)
                        drawLine(
                            color = PrimaryPurple.copy(alpha = 0.6f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                    }
                }
            }
        }
    }
}
