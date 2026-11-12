package com.sdkecuador.control.feature.trips.data
import com.sdkecuador.control.feature.expenses.data.ExpenseRules
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.budgets.data.BudgetEntity

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TravelRepository @Inject constructor(private val dao: TravelDao, private val database: TravelDatabase) {
    fun trips() = dao.trips()
    fun expenses(id: Long) = dao.expenses(id)
    fun allExpenses() = dao.allExpenses()
    fun budgets(id: Long) = dao.budgets(id)
    fun categories() = dao.categories()
    fun currencies() = dao.currencies()
    suspend fun createTrip(name: String, date: String, currency: String, budgetMinor: Long?, endDate: String? = null): Long =
        dao.insertTrip(TripEntity(name = name.trim(), startDate = date, endDate = endDate, baseCurrency = currency, budgetMinor = budgetMinor))
    suspend fun updateTrip(id: Long, name: String, startDate: String, endDate: String?, currency: String, budgetMinor: Long?): Boolean = database.withTransaction {
        val trip = dao.trip(id) ?: return@withTransaction false
        dao.updateTrip(trip.copy(name = name.trim(), startDate = startDate, endDate = endDate, baseCurrency = currency, budgetMinor = budgetMinor))
        true
    }
    suspend fun saveExpense(expense: ExpenseEntity): Long = database.withTransaction {
        val id = if (expense.id == 0L) dao.insertExpense(expense) else { dao.updateExpense(expense); expense.id }
        refreshAssessments(expense.tripId)
        id
    }
    suspend fun deleteExpense(expense: ExpenseEntity) = database.withTransaction {
        dao.deleteExpense(expense)
        refreshAssessments(expense.tripId)
    }
    suspend fun saveBudget(budget: BudgetEntity) = database.withTransaction {
        dao.saveBudget(budget)
        refreshAssessments(budget.tripId)
    }
    suspend fun seed() = database.withTransaction {
        dao.saveCategories(listOf("food" to "Comida", "transport" to "Transporte", "lodging" to "Alojamiento", "activities" to "Actividades", "shopping" to "Compras", "other" to "Otros").map { CategoryEntity(it.first, it.second) })
        dao.saveCurrencies(listOf("USD" to "Dólar", "EUR" to "Euro", "PEN" to "Sol", "COP" to "Peso colombiano").map { CurrencyEntity(it.first, it.second) })
        dao.tripSnapshot().forEach { refreshAssessments(it.id) }
    }
    private suspend fun refreshAssessments(tripId: Long) {
        val expenses = dao.expenseSnapshot(tripId)
        val current = ExpenseRules.currentAssessments(expenses, dao.budgetSnapshot(tripId))
        current.zip(expenses).forEach { (updated, stored) ->
            if (updated != stored) dao.updateExpense(updated)
        }
    }
}

@Module @InstallIn(SingletonComponent::class) object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TravelDatabase = Room.databaseBuilder(context, TravelDatabase::class.java, "travel.db").addMigrations(MIGRATION_1_2).build()
    @Provides fun dao(database: TravelDatabase): TravelDao = database.dao()
}
