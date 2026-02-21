package com.otavio.opticcore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.otavio.opticcore.ui.screen.CameraInfoScreen
import com.otavio.opticcore.ui.screen.CameraScreen
import com.otavio.opticcore.ui.theme.OpticCoreTheme
import com.otavio.opticcore.ui.viewmodel.CameraInfoViewModel
import com.otavio.opticcore.ui.viewmodel.CameraViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OpticCore.Main"
    }

    private lateinit var cameraInfoViewModel: CameraInfoViewModel
    private lateinit var cameraViewModel: CameraViewModel

    private var permissionGranted = mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "✅ Permissão de câmera concedida")
            permissionGranted.value = true
        } else {
            Log.w(TAG, "❌ Permissão de câmera negada")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        cameraInfoViewModel = ViewModelProvider(this)[CameraInfoViewModel::class.java]
        cameraViewModel = ViewModelProvider(this)[CameraViewModel::class.java]

        checkAndRequestCameraPermission()

        setContent {
            OpticCoreTheme {
                val hasPermission by remember { permissionGranted }
                var showInfoScreen by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (!hasPermission) {
                        // Aguardando permissão
                        val scanState by cameraInfoViewModel.scanState.collectAsState()
                        CameraInfoScreen(
                            scanState = scanState,
                            onRescan = { cameraInfoViewModel.startScan() },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else if (showInfoScreen) {
                        // Tela de info das câmeras
                        val scanState by cameraInfoViewModel.scanState.collectAsState()
                        if (scanState is com.otavio.opticcore.ui.viewmodel.ScanState.Idle) {
                            cameraInfoViewModel.startScan()
                        }
                        CameraInfoScreen(
                            scanState = scanState,
                            onRescan = { cameraInfoViewModel.startScan() },
                            modifier = Modifier.padding(innerPadding)
                        )
                        // TODO: Add back button to return to camera
                    } else {
                        // Viewfinder principal
                        val previewState by cameraViewModel.previewState.collectAsState()
                        val captureState by cameraViewModel.captureState.collectAsState()
                        val processingState by cameraViewModel.processingState.collectAsState()
                        val currentLensIndex by cameraViewModel.currentLensIndex.collectAsState()
                        val availableLenses by cameraViewModel.availableLenses.collectAsState()
                        val processingSettings by cameraViewModel.processingSettings.collectAsState()
                        val showSettings by cameraViewModel.showSettings.collectAsState()

                        CameraScreen(
                            viewModel = cameraViewModel,
                            previewState = previewState,
                            captureState = captureState,
                            processingState = processingState,
                            currentLensIndex = currentLensIndex,
                            availableLenses = availableLenses,
                            processingSettings = processingSettings,
                            showSettings = showSettings,
                            onInfoClick = {
                                showInfoScreen = !showInfoScreen
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permissão de câmera já concedida")
            permissionGranted.value = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}