package com.sdkecuador.control.feature.budgets.data

import androidx.room.*

@Entity(tableName = "budgets", primaryKeys = ["tripId", "category", "currency"])
data class BudgetEntity(val tripId: Long, val category: String, val currency: String, val limitMinor: Long)
