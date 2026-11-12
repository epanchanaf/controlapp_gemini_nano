package com.sdkecuador.control.feature.budgets.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.budgets.data.BudgetEntity
import com.sdkecuador.control.feature.trips.data.TravelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(val budgets: List<BudgetEntity> = emptyList(), val expenses: List<ExpenseEntity> = emptyList(), val error: String? = null)
@HiltViewModel class BudgetViewModel @Inject constructor(private val repo: TravelRepository) : ViewModel() {
    private val _state = MutableStateFlow(BudgetUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    fun load(tripId: Long) {
        if (tripId == 0L) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { combine(repo.budgets(tripId), repo.expenses(tripId)) { budgets, expenses -> BudgetUiState(budgets, expenses) }.collect { _state.value = it } }
    }
    fun save(tripId: Long, category: String, currency: String, amount: String) = viewModelScope.launch {
        val minor = ExpenseRules.amountMinor(amount)
        if (minor == null || tripId == 0L || category !in ExpenseRules.categories || currency !in ExpenseRules.currencies) { _state.update { it.copy(error = "Presupuesto inválido") }; return@launch }
        repo.saveBudget(BudgetEntity(tripId, category, currency, minor))
    }
}
