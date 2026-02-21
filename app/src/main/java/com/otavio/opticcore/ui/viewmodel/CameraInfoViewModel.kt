package com.otavio.opticcore.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.otavio.opticcore.data.hardware.CameraHardwareScanner
import com.otavio.opticcore.data.model.CameraDeviceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel para gerenciar o estado do scan de hardware de câmera.
 * Segue a arquitetura MVVM, separando a lógica de hardware da UI.
 */
class CameraInfoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "OpticCore.ViewModel"
    }

    private val scanner = CameraHardwareScanner(application.applicationContext)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /**
     * Inicia o scan de hardware em background thread.
     */
    fun startScan() {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.value = ScanState.Scanning

            try {
                Log.i(TAG, "Iniciando scan de hardware...")
                val report = scanner.scanAllCameras()
                _scanState.value = ScanState.Success(report)
                Log.i(TAG, "Scan concluído com sucesso! ${report.totalCameras} câmeras encontradas.")
            } catch (e: Exception) {
                Log.e(TAG, "Falha no scan: ${e.message}", e)
                _scanState.value = ScanState.Error(e.message ?: "Erro desconhecido")
            }
        }
    }
}

/**
 * Estados possíveis do scan de hardware.
 */
sealed class ScanState {
    data object Idle : ScanState()
    data object Scanning : ScanState()
    data class Success(val report: CameraDeviceReport) : ScanState()
    data class Error(val message: String) : ScanState()
}
