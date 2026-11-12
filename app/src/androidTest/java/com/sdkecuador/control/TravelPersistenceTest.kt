package com.sdkecuador.control

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.budgets.data.BudgetEntity
import com.sdkecuador.control.feature.trips.data.MIGRATION_1_2
import com.sdkecuador.control.feature.trips.data.TravelDatabase
import com.sdkecuador.control.feature.trips.data.TravelRepository
import com.sdkecuador.control.feature.trips.data.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelPersistenceTest {
    @Test fun editingTripKeepsItsExpenses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TravelDatabase::class.java).build()
        val repo = TravelRepository(db.dao(), db)
        try {
            val tripId = repo.createTrip("Quito", "2026-11-07", "USD", 50_000)
            val expenseId = repo.saveExpense(ExpenseEntity(tripId = tripId, amountMinor = 1250, currency = "USD", date = "2026-11-07", category = "food", description = "Almuerzo"))

            assertTrue(repo.updateTrip(tripId, "Cuenca", "2026-11-08", "2026-11-10", "EUR", 75_000))

            val updated = db.dao().trip(tripId)!!
            assertEquals("Cuenca", updated.name)
            assertEquals("2026-11-08", updated.startDate)
            assertEquals("2026-11-10", updated.endDate)
            assertEquals("EUR", updated.baseCurrency)
            assertEquals(75_000L, updated.budgetMinor)
            assertEquals(expenseId, db.dao().expenses(tripId).first().single().id)
        } finally { db.close() }
    }

    @Test fun storedAssessmentsFollowBudgetAndExpenseChanges() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, TravelDatabase::class.java).build()
        val repo = TravelRepository(db.dao(), db)
        try {
            val tripId = repo.createTrip("Quito", "2026-11-07", "USD", null)
            val budget = BudgetEntity(tripId, "food", "USD", 10_000)
            repo.saveBudget(budget)
            val firstId = repo.saveExpense(ExpenseEntity(tripId = tripId, amountMinor = 6_000, currency = "USD", date = "2026-11-07", category = "food", description = "Comida"))
            val secondId = repo.saveExpense(ExpenseEntity(tripId = tripId, amountMinor = 5_000, currency = "USD", date = "2026-11-07", category = "food", description = "Cena"))
            assertEquals("Malo: supera el presupuesto", db.dao().expense(firstId)?.assessment)
            assertEquals("Malo: supera el presupuesto", db.dao().expense(secondId)?.assessment)

            repo.saveBudget(budget.copy(limitMinor = 12_000))
            assertEquals("Bueno: dentro del presupuesto", db.dao().expense(firstId)?.assessment)

            repo.saveBudget(budget)
            repo.deleteExpense(db.dao().expense(secondId)!!)
            assertEquals("Bueno: dentro del presupuesto", db.dao().expense(firstId)?.assessment)
        } finally { db.close() }
    }

    @Test fun expenseSurvivesDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "travel-persistence-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, TravelDatabase::class.java, name).build()
        val tripId = first.dao().insertTrip(TripEntity(name = "Quito", startDate = "2026-11-07", budgetMinor = 50_000))
        first.dao().insertExpense(ExpenseEntity(tripId = tripId, amountMinor = 1250, currency = "USD", date = "2026-11-07", category = "food", description = "Almuerzo", notes = "Prueba", receiptUri = "/test/receipt.jpg"))
        first.close()
        val reopened = Room.databaseBuilder(context, TravelDatabase::class.java, name).build()
        try {
            val expenses = reopened.dao().expenses(tripId).first()
            assertEquals(1, expenses.size)
            assertEquals(1250L, expenses.single().amountMinor)
            assertEquals("/test/receipt.jpg", expenses.single().receiptUri)
            assertEquals(50_000L, reopened.dao().trips().first().single().budgetMinor)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun existingTripAndExpensesSurviveMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "travel-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, startDate TEXT NOT NULL, endDate TEXT, baseCurrency TEXT NOT NULL)")
            old.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, date TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, notes TEXT NOT NULL, receiptUri TEXT, level TEXT NOT NULL, assessment TEXT NOT NULL, FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_expenses_tripId ON expenses (tripId)")
            old.execSQL("CREATE TABLE categories (code TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(code))")
            old.execSQL("CREATE TABLE currencies (code TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(code))")
            old.execSQL("CREATE TABLE budgets (tripId INTEGER NOT NULL, category TEXT NOT NULL, currency TEXT NOT NULL, limitMinor INTEGER NOT NULL, PRIMARY KEY(tripId, category, currency))")
            old.execSQL("INSERT INTO trips (id, name, startDate, baseCurrency) VALUES (1, 'Quito', '2026-11-07', 'USD')")
            old.execSQL("INSERT INTO expenses (tripId, amountMinor, currency, date, category, description, notes, level, assessment) VALUES (1, 1250, 'USD', '2026-11-07', 'food', 'Almuerzo', '', 'Bajo', 'Bueno')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, TravelDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val trip = migrated.dao().trips().first().single()
            assertEquals("Quito", trip.name)
            assertEquals(null, trip.budgetMinor)
            assertEquals(1250L, migrated.dao().expenses(trip.id).first().single().amountMinor)
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
