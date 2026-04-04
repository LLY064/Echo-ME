package com.ml.shubham0204.docqa.ui.screens.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Synagogue
import androidx.compose.material.icons.filled.Brain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun ChatScreenMoreOptionsPopup(expanded: Boolean, onDismiss: () -> Unit, onItemClick: (ChatScreenUIEvent) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("管理文档", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.FolderOpen, "文档") },
            onClick = { onItemClick(ChatScreenUIEvent.OnOpenDocsClick); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Naive RAG", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.Synagogue, "Naive") },
            onClick = { onItemClick(ChatScreenUIEvent.OnNaiveRAGClick); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Edge RAG", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.AcUnit, "Edge") },
            onClick = { onItemClick(ChatScreenUIEvent.OnEdgeRAGClick); onDismiss() }
        )
        DropdownMenuItem(
            text = { Text("Echo-ME 高级推理", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = { Icon(Icons.Default.Brain, "Echo-ME") },
            onClick = { onItemClick(ChatScreenUIEvent.OnEchoMeClick); onDismiss() }
        )
    }
}