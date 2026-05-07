package com.karting.chrono

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.karting.chrono.location.GpsManager
import com.karting.chrono.service.TimingService
import com.karting.chrono.session.SessionViewModel
import com.karting.chrono.ui.LiveTimingScreen
import com.karting.chrono.ui.PermissionScreen
import com.karting.chrono.ui.SetupScreen
import com.karting.chrono.ui.SummaryScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val nav = rememberSwipeDismissableNavController()

            var hasLocation by remember { mutableStateOf(hasFineLocation()) }
            val askLocation = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            }

            if (!hasLocation) {
                PermissionScreen(onRequest = {
                    val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms += Manifest.permission.POST_NOTIFICATIONS
                    }
                    askLocation.launch(perms.toTypedArray())
                })
                return@setContent
            }

            val vm: SessionViewModel = viewModel()
            // Stream GPS samples into the setup view model so the setup screen
            // can show fix status and capture the start/finish line.
            LaunchedEffect(Unit) {
                GpsManager(this@MainActivity).samples().collectLatest { sample ->
                    vm.onSample(sample, headingDeg = sample.bearingDeg)
                }
            }

            SwipeDismissableNavHost(navController = nav, startDestination = "setup") {
                composable("setup") {
                    val gps by vm.gps.collectAsStateWithLifecycle()
                    val line by vm.line.collectAsStateWithLifecycle()
                    val pendingA by vm.pendingPointA.collectAsStateWithLifecycle()
                    SetupScreen(
                        gps = gps,
                        line = line,
                        pendingA = pendingA,
                        onSetStartA = vm::captureStartLine,
                        onSetEndB = vm::captureLineEnd,
                        onCancelPending = vm::cancelPendingLine,
                        onSinglePoint = { vm.captureSinglePointWithHeading() },
                        onClearLine = vm::clearLine,
                        onStart = {
                            TimingService.start(this@MainActivity)
                            nav.navigate("live")
                        },
                        onOpenSummary = { nav.navigate("summary") },
                    )
                }
                composable("live") {
                    val state by TimingService.stateFlow.collectAsState()
                    LiveTimingScreen(
                        state = state,
                        onStop = {
                            TimingService.stop(this@MainActivity)
                            nav.navigate("summary") {
                                popUpTo("setup")
                            }
                        },
                    )
                }
                composable("summary") {
                    val state by TimingService.stateFlow.collectAsState()
                    SummaryScreen(
                        context = this@MainActivity,
                        laps = state.laps,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
