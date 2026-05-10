package com.karting.chrono.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.karting.chrono.phone.data.ActiveSessionStore
import com.karting.chrono.phone.ui.SessionDetailScreen
import com.karting.chrono.phone.ui.SessionListScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val active by ActiveSessionStore.state.collectAsState()

                    NavHost(navController = nav, startDestination = "list") {
                        composable("list") {
                            SessionListScreen(
                                active = active,
                                onOpen = { id -> nav.navigate("session/$id") },
                            )
                        }
                        composable("session/{id}") { entry ->
                            val id = entry.arguments?.getString("id")?.toLongOrNull()
                                ?: return@composable
                            SessionDetailScreen(
                                sessionId = id,
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
