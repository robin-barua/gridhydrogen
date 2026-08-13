package com.gridsim.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gridsim.app.model.CaseToggle
import com.gridsim.app.model.GridSystem
import com.gridsim.app.model.PjmDefaults
import com.gridsim.app.model.StorageTech
import com.gridsim.app.solver.LpBuilder
import com.gridsim.app.solver.SimulationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel : ViewModel() {

    // The editable system. Demo mode uses this unmodified; Custom mode edits it.
    var system = mutableStateOf(PjmDefaults.defaultSystem())
        private set

    var selectedCase = mutableStateOf(CaseToggle.CASE3_HYDROGEN)
        private set

    var storageTech = mutableStateOf(StorageTech.BATTERY)
        private set

    var storagePowerMw = mutableStateOf(150.0)
        private set

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _result = MutableStateFlow<SimulationResult?>(null)
    val result: StateFlow<SimulationResult?> = _result

    private val _baselineEmissions = MutableStateFlow<Double?>(null)

    fun setCase(c: CaseToggle) { selectedCase.value = c }

    fun setStorageChoice(tech: StorageTech, powerMw: Double) {
        storageTech.value = tech
        storagePowerMw.value = powerMw
        system.value = system.value.copy(storages = PjmDefaults.defaultStorages(tech, powerMw))
    }

    fun resetToDefaults() {
        system.value = PjmDefaults.defaultSystem()
        storageTech.value = StorageTech.BATTERY
        storagePowerMw.value = 150.0
    }

    fun updateSystem(update: (GridSystem) -> GridSystem) {
        system.value = update(system.value)
    }

    /** Runs Case 1 (conventional-only) to get a baseline emissions figure, then runs the selected case. */
    fun runSimulation() {
        viewModelScope.launch {
            _isRunning.value = true
            val sys = system.value
            val case = selectedCase.value
            val res = withContext(Dispatchers.Default) {
                val baseline = LpBuilder.solve(sys, CaseToggle.CASE1_CONVENTIONAL)
                val baselineEmissions = baseline.metrics.emissionsTonPerDay
                _baselineEmissions.value = baselineEmissions
                LpBuilder.solve(sys, case, baselineEmissionsTon = baselineEmissions)
            }
            _result.value = res
            _isRunning.value = false
        }
    }
}
