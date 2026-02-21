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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.otavio.opticcore.ui.screen.CameraInfoScreen
import com.otavio.opticcore.ui.theme.OpticCoreTheme
import com.otavio.opticcore.ui.viewmodel.CameraInfoViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OpticCore.Main"
    }

    private lateinit var viewModel: CameraInfoViewModel

    // Launcher para solicitar permissão de câmera em runtime
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(TAG, "✅ Permissão de câmera concedida — iniciando scan")
            viewModel.startScan()
        } else {
            Log.w(TAG, "❌ Permissão de câmera negada pelo usuário")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[CameraInfoViewModel::class.java]

        // Verifica permissão e dispara o scan
        checkAndRequestCameraPermission()

        setContent {
            OpticCoreTheme {
                val scanState by viewModel.scanState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraInfoScreen(
                        scanState = scanState,
                        onRescan = { viewModel.startScan() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "Permissão de câmera já concedida")
                viewModel.startScan()
            }
            else -> {
                Log.i(TAG, "Solicitando permissão de câmera ao usuário...")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}