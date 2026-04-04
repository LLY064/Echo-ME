package com.ml.shubham0204.docqa.ui.screens.edgerag

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeRAGScreen(
    uiState: EdgeRAGUIState,
    onEvent: (EdgeRAGEvent) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onEvent(EdgeRAGEvent.OnModelSelected(it, context)) } }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onEvent(EdgeRAGEvent.OnJsonSelected(it, context)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edge RAG Test") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { modelPickerLauncher.launch(arrayOf("*/*")) }, enabled = !uiState.isLoading && !uiState.isTesting) { Text("选择模型") }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = uiState.modelName)
            }

            if (uiState.loadingStatus.isNotEmpty()) Text(text = uiState.loadingStatus, color = MaterialTheme.colorScheme.primary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { jsonPickerLauncher.launch(arrayOf("application/json", "*/*")) }, enabled = !uiState.isLoading && !uiState.isTesting) { Text("选择测试JSON") }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = uiState.jsonFileName)
            }

            Text("索引状态: ${uiState.indexStatus}", color = Color.DarkGray)

            Button(
                onClick = { onEvent(EdgeRAGEvent.StartTest(context)) },
                enabled = uiState.isModelReady && !uiState.isTesting && uiState.jsonFileName != "未选择JSON",
                modifier = Modifier.fillMaxWidth()
            ) { Text("开始测试") }

            if (uiState.isTesting || uiState.showResults) Text(text = uiState.testProgress, color = Color.Gray)

            if (uiState.showResults) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("实验数据", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. 平均向量搜索时间: ${String.format("%.2f", uiState.avgSearchTime)} ms")
                        Text("2. 平均 TTFT: ${String.format("%.2f", uiState.avgTTFT)} ms")
                        Text("3. 平均解码速度: ${String.format("%.2f", uiState.avgDecodeSpeed)} tokens/sec")
                    }
                }
            }
        }
    }
}