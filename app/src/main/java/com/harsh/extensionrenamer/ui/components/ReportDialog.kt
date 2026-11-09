package com.harsh.extensionrenamer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harsh.extensionrenamer.data.HistoryOperation

/** Success / failure summary shown right after a rename run. */
@Composable
fun ReportDialog(operation: HistoryOperation, onDismiss: () -> Unit) {
    val failures = operation.items.filter { !it.success }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Rename report", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${operation.successCount} renamed, ${operation.failureCount} failed",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (failures.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    failures.forEach { item ->
                        Text(
                            "${item.oldName} → ${item.newName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            item.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    )
}
