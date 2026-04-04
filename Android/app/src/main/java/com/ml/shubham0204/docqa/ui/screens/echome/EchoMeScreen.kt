package com.ml.shubham0204.docqa.ui.screens.echome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun EchoMeScreen(ui: EchoMeUIState, onEvent: (EchoMeEvent) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val modelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { onEvent(EchoMeEvent.ModelSelected(it, ctx)) } }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { onEvent(EchoMeEvent.JsonSelected(it, ctx)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Echo-ME 双记忆引擎") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { onEvent(EchoMeEvent.Refresh) }) { Text("刷新") } }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("双记忆架构 (L1 RAM + L2 Flash)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { modelLauncher.launch(arrayOf("*/*")) }, enabled = !ui.isLoading && !ui.isTesting) { Text("挂载 LLM") }
                Spacer(Modifier.width(16.dp))
                Text(ui.modelName, maxLines = 1)
            }

            if (ui.status.isNotEmpty()) Text(ui.status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { jsonLauncher.launch(arrayOf("application/json", "*/*")) }, enabled = !ui.isLoading && !ui.isTesting) { Text("装载 Query") }
                Spacer(Modifier.width(16.dp))
                Text(ui.jsonFileName, maxLines = 1)
            }

            Button(onClick = { onEvent(EchoMeEvent.StartTest) }, enabled = ui.isModelReady && !ui.isTesting && ui.jsonFileName != "未选择JSON",
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("启动多智能体协同推理") }

            if (ui.isTesting || ui.showResults) Text(ui.progress, color = Color.Gray, style = MaterialTheme.typography.bodySmall)

            // 性能监控
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("性能指标", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("检索", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.2f", ui.avgSearch)} ms") }
                        Column { Text("缺页", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.2f", ui.avgFaults)}") }
                        Column { Text("TTFT", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.2f", ui.avgTTFT)} ms") }
                        Column { Text("解码", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.2f", ui.avgDecode)} t/s") }
                    }
                }
            }

            // 双记忆状态
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("双记忆状态", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("PFM", style = MaterialTheme.typography.labelSmall); Text("${ui.memoryState.pfmCount} 条") }
                        Column { Text("PEK", style = MaterialTheme.typography.labelSmall); Text("${ui.memoryState.pekCount} 条") }
                        Column { Text("L1命中率", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.1f", ui.memoryState.l1HitRate * 100)}%") }
                    }
                }
            }

            // 缓存状态
            ui.cacheStats?.let { s ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("缓存状态", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("L1", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.1f", s.l1Hit * 100)}%") }
                            Column { Text("L2", style = MaterialTheme.typography.labelSmall); Text("${String.format("%.1f", s.l2Hit * 100)}%") }
                            Column { Text("驱逐", style = MaterialTheme.typography.labelSmall); Text("${s.evictions}") }
                            Column { Text("预取", style = MaterialTheme.typography.labelSmall); Text("${s.prefetched}") }
                        }
                    }
                }
            }

            // 内存压力
            ui.memoryInfo?.let { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("内存压力", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), progress = { m.ratio }, color = if (m.ratio > 0.85f) Color.Red else MaterialTheme.colorScheme.primary)
                        Text("已用: ${m.usedMb}MB / ${m.maxMb}MB (${String.format("%.1f", m.ratio * 100)}%)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Top 轨迹
            if (ui.topTrajectories.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Top 轨迹 (GRPO)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        ui.topTrajectories.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}