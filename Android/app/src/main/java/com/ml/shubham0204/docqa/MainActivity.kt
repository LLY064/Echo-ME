package com.ml.shubham0204.docqa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ml.shubham0204.docqa.ui.screens.chat.ChatNavEvent
import com.ml.shubham0204.docqa.ui.screens.chat.ChatScreen
import com.ml.shubham0204.docqa.ui.screens.chat.ChatViewModel
import com.ml.shubham0204.docqa.ui.screens.docs.DocsScreen
import com.ml.shubham0204.docqa.ui.screens.docs.DocsViewModel
import com.ml.shubham0204.docqa.ui.screens.echome.EchoMeScreen
import com.ml.shubham0204.docqa.ui.screens.echome.EchoMeViewModel
import com.ml.shubham0204.docqa.ui.screens.local_models.LocalModelsScreen
import com.ml.shubham0204.docqa.ui.screens.local_models.LocalModelsViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import com.ml.shubham0204.docqa.ui.screens.naiverag.NaiveRAGViewModel
import com.ml.shubham0204.docqa.ui.screens.naiverag.NaiveRAGScreen

@Serializable object ChatRoute
@Serializable object DocsRoute
@Serializable object NaiveRAGRoute
@Serializable object EdgeRAGRoute
@Serializable object EchoMeRoute
@Serializable object LocalModelsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val nav = rememberNavController()
            NavHost(nav, startDestination = ChatRoute, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                composable<EdgeRAGRoute> { be ->
                    val vm: com.ml.shubham0204.docqa.ui.screens.edgerag.EdgeRAGViewModel = koinViewModel(be)
                    val st by vm.uiState.collectAsState()
                    com.ml.shubham0204.docqa.ui.screens.edgerag.EdgeRAGScreen(st, vm::onEvent) { nav.navigateUp() }
                }

                composable<DocsRoute> { be ->
                    val vm: DocsViewModel = koinViewModel(be)
                    val st by vm.docsScreenUIState.collectAsState()
                    DocsScreen(st, { nav.navigateUp() }, vm::onEvent)
                }

                composable<LocalModelsRoute> { be ->
                    val vm: LocalModelsViewModel = koinViewModel(be)
                    val st by vm.uiState.collectAsState()
                    LocalModelsScreen(st, vm::onEvent) { nav.navigateUp() }
                }

                composable<NaiveRAGRoute> { be ->
                    val vm: NaiveRAGViewModel = koinViewModel(be)
                    val st by vm.uiState.collectAsState()
                    NaiveRAGScreen(st, vm::onEvent) { nav.navigateUp() }
                }

                composable<EchoMeRoute> { be ->
                    val vm: EchoMeViewModel = koinViewModel(be)
                    val st by vm.ui.collectAsState()
                    EchoMeScreen(st, vm::onEvent) { nav.navigateUp() }
                }

                composable<ChatRoute> { be ->
                    val vm: ChatViewModel = koinViewModel(be)
                    val st by vm.chatScreenUIState.collectAsState()
                    val navEvt by vm.navEventChannel.collectAsState(ChatNavEvent.None)
                    LaunchedEffect(navEvt) {
                        when (navEvt) {
                            is ChatNavEvent.ToDocsScreen -> nav.navigate(DocsRoute)
                            is ChatNavEvent.ToNaiveRAGScreen -> nav.navigate(NaiveRAGRoute)
                            is ChatNavEvent.ToEdgeRAGScreen -> nav.navigate(EdgeRAGRoute)
                            is ChatNavEvent.ToEchoMeScreen -> nav.navigate(EchoMeRoute)
                            is ChatNavEvent.None -> {}
                        }
                    }
                    ChatScreen(st) { vm.onChatScreenEvent(it) }
                }
            }
        }
    }
}