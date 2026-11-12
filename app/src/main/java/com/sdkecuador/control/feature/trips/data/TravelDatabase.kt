package com.sdkecuador.control.feature.trips.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.sdkecuador.control.feature.expenses.data.ExpenseEntity
import com.sdkecuador.control.feature.budgets.data.BudgetEntity

@Entity(tableName = "trips") data class TripEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val startDate: String, val endDate: String? = null, val baseCurrency: String = "USD", val budgetMinor: Long? = null)
@Entity(tableName = "categories") data class CategoryEntity(@PrimaryKey val code: String, val name: String)
@Entity(tableName = "currencies") data class CurrencyEntity(@PrimaryKey val code: String, val name: String)

@Dao interface TravelDao {
    @Query("SELECT * FROM trips ORDER BY id DESC") fun trips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM trips") suspend fun tripSnapshot(): List<TripEntity>
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1") suspend fun trip(id: Long): TripEntity?
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY date DESC, id DESC") fun expenses(tripId: Long): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM expenses WHERE tripId = :tripId") suspend fun expenseSnapshot(tripId: Long): List<ExpenseEntity>
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC") fun allExpenses(): Flow<List<ExpenseEntity>>
    @Query("SELECT * FROM budgets WHERE tripId = :tripId") fun budgets(tripId: Long): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE tripId = :tripId") suspend fun budgetSnapshot(tripId: Long): List<BudgetEntity>
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun expense(id: Long): ExpenseEntity?
    @Insert suspend fun insertTrip(trip: TripEntity): Long
    @Update suspend fun updateTrip(trip: TripEntity)
    @Insert suspend fun insertExpense(expense: ExpenseEntity): Long
    @Update suspend fun updateExpense(expense: ExpenseEntity)
    @Delete suspend fun deleteExpense(expense: ExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBudget(budget: BudgetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCategories(categories: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCurrencies(currencies: List<CurrencyEntity>)
    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM currencies ORDER BY code") fun currencies(): Flow<List<CurrencyEntity>>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN budgetMinor INTEGER")
    }
}

@Database(entities = [TripEntity::class, ExpenseEntity::class, CategoryEntity::class, CurrencyEntity::class, BudgetEntity::class], version = 2, exportSchema = false)
abstract class TravelDatabase : RoomDatabase() { abstract fun dao(): TravelDao }
