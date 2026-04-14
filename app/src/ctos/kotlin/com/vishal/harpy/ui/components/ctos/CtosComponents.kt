package com.vishal.harpy.ui.components.ctos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishal.harpy.ui.theme.*

@Composable
fun CtosCommandStrip(
    actions: List<CtosAction>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            CommandTile(
                label = action.label,
                icon = action.icon,
                color = action.color,
                modifier = Modifier.weight(1f),
                onClick = action.onClick
            )
        }
    }
}

data class CtosAction(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun RowScope.CommandTile(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        color = CtosDarkGrey,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CtosSystemStatus(
    title: String = "SYSTEM_LINK // STABLE",
    subtitle1: Pair<String, String>,
    subtitle2: Pair<String, String>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, CtosCyan.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            .background(CtosDarkGrey)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(CtosCyan).clip(CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = CtosCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusItem(subtitle1.first, subtitle1.second)
                StatusItem(subtitle2.first, subtitle2.second)
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CtosWhite.copy(alpha = 0.5f), fontSize = 8.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, color = CtosWhite, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun CtosLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val size = this.size.minDimension
        val strokeWidth = 2.dp.toPx()
        
        drawCircle(
            color = CtosCyan,
            radius = size / 2,
            style = Stroke(width = strokeWidth)
        )
        
        val margin = size * 0.2f
        drawLine(
            color = CtosCyan,
            start = androidx.compose.ui.geometry.Offset(margin, size / 2),
            end = androidx.compose.ui.geometry.Offset(size - margin, size / 2),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = CtosCyan,
            start = androidx.compose.ui.geometry.Offset(size / 2, margin),
            end = androidx.compose.ui.geometry.Offset(size / 2, size - margin),
            strokeWidth = strokeWidth
        )
    }
}
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = CtosCyan
) {
    Surface(
        modifier = modifier,
        color = CtosDarkGrey,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = CtosWhite,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = "$title //",
        style = MaterialTheme.typography.labelSmall,
        color = CtosCyan,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun CtosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
        placeholder = { Text(placeholder, color = CtosWhite.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace) },
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = CtosWhite,
            fontFamily = FontFamily.Monospace
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CtosCyan,
            unfocusedBorderColor = CtosCyan.copy(alpha = 0.3f),
            focusedLabelColor = CtosCyan,
            unfocusedLabelColor = CtosCyan.copy(alpha = 0.5f),
            cursorColor = CtosCyan
        ),
        shape = RoundedCornerShape(2.dp),
        singleLine = true,
        trailingIcon = trailingIcon
    )
}
