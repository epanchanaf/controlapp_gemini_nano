package com.sdkecuador.control.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TravelStyles {
    val cardShape = RoundedCornerShape(18.dp)
    val cardBorder = BorderStroke(1.dp, TravelColors.border)
    val activeBorder = BorderStroke(1.dp, TravelColors.cyan.copy(alpha = 0.65f))
    val warningBorder = BorderStroke(1.dp, TravelColors.amber.copy(alpha = 0.75f))
    fun Modifier.neonEdge(color: Color = TravelColors.cyan): Modifier = border(1.dp, color.copy(alpha = 0.65f), cardShape)
}
