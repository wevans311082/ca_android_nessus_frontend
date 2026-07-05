package com.wevans.caandroidnessusfrontend.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wevans.caandroidnessusfrontend.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun getSeverityColor(severity: Int?): Color {
    return when (severity) {
        4 -> ErrorRed
        3 -> WarningYellow
        2 -> CyberCyanVariant
        1 -> SuccessGreen
        0 -> InfoBlue
        else -> Color.Gray
    }
}

@Composable
fun SeverityBadge(label: String, count: Int, color: Color) {
    if (count > 0) {
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, color)
        ) {
            Text(
                text = "$label: $count",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

fun sharePdf(context: Context, path: String) {
    val file = File(path)
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    } catch (e: Exception) {
        // no-op for caller to handle
    }
}

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this * 1000))
}

fun getAgentStatusColor(status: String?): Color {
    return when (status?.lowercase()) {
        "on", "online" -> SuccessGreen
        "off", "offline" -> CyberSlate
        else -> Color.Gray
    }
}
