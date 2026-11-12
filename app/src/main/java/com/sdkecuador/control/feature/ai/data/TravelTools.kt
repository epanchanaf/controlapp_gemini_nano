package com.sdkecuador.control.feature.ai.data

import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionAccessLevel
import androidx.appfunctions.ExperimentalAppFunctionsApi
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.annotation.RequiresApi
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.trips.data.TravelDao
import com.sdkecuador.control.feature.trips.data.TravelRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Locale

@AppFunctionServiceEntryPoint(serviceName = "TravelAppFunctionService", appFunctionXmlFileName = "travel_app_functions")
@RequiresApi(36)
abstract class TravelTools : AppFunctionService() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun travelDao(): TravelDao
        fun travelRepository(): TravelRepository
    }

    private val dao: TravelDao
        get() = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).travelDao()

    private val repository: TravelRepository
        get() = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).travelRepository()

    /**
     * Lists trips and their numeric IDs. Call this before add_expense_to_trip to resolve the
     * user's trip name to one unambiguous ID. Do not guess an ID or add a charge if two trips
     * have the same name; ask the user which trip they mean.
     */
    @OptIn(ExperimentalAppFunctionsApi::class)
    @AppFunctionAccessLevel(level = AppFunctionMetadata.ACCESS_LEVEL_ANDROID_TRUSTED, isCompatEnforcementEnabled = true)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun list_trips(): String =
        dao.tripSnapshot().joinToString("; ") { "id=${it.id}, nombre=${it.name}" }
            .ifBlank { "No hay viajes registrados" }

    /**
     * Registers one confirmed expense in an existing trip. Use list_trips first and only invoke
     * this after the user explicitly asks to save the expense. The amount is in minor currency
     * units (for example, 24.50 USD is 2450). Return the result to the user; do not retry after
     * a successful response, because that would create a duplicate expense.
     *
     * @param tripId Numeric trip ID obtained from list_trips.
     * @param amountMinor Positive amount in cents or the equivalent minor unit; never a decimal.
     * @param currency ISO-like supported currency code: USD, EUR, PEN, or COP.
     * @param date Local expense date in AAAA-MM-DD format. Resolve relative dates such as today
     *   using the device date before calling this function.
     * @param category One of food, transport, lodging, activities, shopping, or other.
     * @param description Short description of what was paid for, such as "Taxi al aeropuerto".
     * @param notes Optional supporting detail. Use an empty string when there is none.
     */
    @OptIn(ExperimentalAppFunctionsApi::class)
    @AppFunctionAccessLevel(level = AppFunctionMetadata.ACCESS_LEVEL_ANDROID_TRUSTED, isCompatEnforcementEnabled = true)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun add_expense_to_trip(
        tripId: Long,
        amountMinor: Long,
        currency: String,
        date: String,
        category: String,
        description: String,
        notes: String,
    ): String {
        val normalizedCurrency = currency.trim().uppercase(Locale.US)
        val normalizedCategory = category.trim().lowercase(Locale.US)
        val normalizedDate = date.trim()
        val normalizedDescription = description.trim()
        val validationError = externalExpenseValidation(
            amountMinor = amountMinor,
            currency = normalizedCurrency,
            date = normalizedDate,
            category = normalizedCategory,
            description = normalizedDescription,
        )
        if (validationError != null) return "No se registró el gasto: $validationError"

        val trip = dao.trip(tripId) ?: return "No existe el viaje con id=$tripId"
        val expenseId = repository.saveExpense(
            ExpenseEntity(
                tripId = tripId,
                amountMinor = amountMinor,
                currency = normalizedCurrency,
                date = normalizedDate,
                category = normalizedCategory,
                description = normalizedDescription,
                notes = notes.trim(),
            )
        )
        return "Gasto registrado: id=$expenseId; viaje=${trip.name}; " +
            "${money(amountMinor, normalizedCurrency)}; $normalizedDescription; fecha=$normalizedDate"
    }

    /** Returns the general and category budgets for a trip, without calculating expenses.
     * @param tripId Numeric trip ID obtained from list_trips.
     */
    @OptIn(ExperimentalAppFunctionsApi::class)
    @AppFunctionAccessLevel(level = AppFunctionMetadata.ACCESS_LEVEL_SELF, isCompatEnforcementEnabled = true)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun trip_budgets(tripId: Long): String {
        val trip = dao.tripSnapshot().find { it.id == tripId }
            ?: return "No existe el viaje con id=$tripId"
        val general = trip.budgetMinor?.let { "presupuesto general=${money(it, trip.baseCurrency)}" }
            ?: "sin presupuesto general"
        val categories = dao.budgetSnapshot(tripId).joinToString("; ") {
            "${it.category}=${money(it.limitMinor, it.currency)}"
        }.ifBlank { "sin presupuestos por categoría" }
        return "${trip.name}: moneda base=${trip.baseCurrency}; $general; $categories"
    }

    /** Returns recorded expense totals grouped by currency for a trip.
     * @param tripId Numeric trip ID obtained from list_trips.
     */
    @OptIn(ExperimentalAppFunctionsApi::class)
    @AppFunctionAccessLevel(level = AppFunctionMetadata.ACCESS_LEVEL_SELF, isCompatEnforcementEnabled = true)
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun trip_expenses(tripId: Long): String {
        val trip = dao.tripSnapshot().find { it.id == tripId }
            ?: return "No existe el viaje con id=$tripId"
        val totals = dao.expenseSnapshot(tripId).groupBy { it.currency }
            .entries.joinToString("; ") { (currency, expenses) ->
                "$currency=${money(expenses.sumOf { it.amountMinor }, currency)} (${expenses.size} gastos)"
            }.ifBlank { "sin gastos registrados" }
        return "${trip.name}: $totals"
    }

    private fun money(minor: Long, currency: String): String =
        "$currency ${String.format(Locale.US, "%.2f", minor / 100.0)}"
}

internal fun externalExpenseValidation(
    amountMinor: Long,
    currency: String,
    date: String,
    category: String,
    description: String,
): String? = when {
    amountMinor <= 0 -> "el importe debe ser positivo y estar expresado en centavos"
    currency !in ExpenseRules.currencies -> "la moneda debe ser USD, EUR, PEN o COP"
    runCatching { java.time.LocalDate.parse(date) }.isFailure -> "la fecha debe usar AAAA-MM-DD"
    category !in ExpenseRules.categories -> "la categoría no es válida"
    description.isBlank() -> "falta la descripción del gasto"
    else -> null
}
