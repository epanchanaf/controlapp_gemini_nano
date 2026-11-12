package com.sdkecuador.control.feature.expenses.data

import androidx.room.*
import com.sdkecuador.control.feature.trips.data.TripEntity

@Entity(tableName = "expenses", foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("tripId")])
data class ExpenseEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val amountMinor: Long, val currency: String, val date: String, val category: String, val description: String, val notes: String = "", val receiptUri: String? = null, val level: String = "", val assessment: String = "")
