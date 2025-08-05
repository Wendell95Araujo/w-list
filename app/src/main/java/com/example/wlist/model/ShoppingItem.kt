package com.example.wlist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.Normalizer
import java.util.UUID

@Entity(tableName = "shopping_items_table")
data class ShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val productName: String,
    @ColumnInfo(name = "productNameNormalized") val productNameNormalized: String = normalize(productName),
    val quantity: Int,
    val unitPrice: Double,
    var isPurchased: Boolean = false,
    val category: String?,
    val displayOrder: Long = System.currentTimeMillis()
) {
    companion object {
        fun normalize(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
                .lowercase()
        }
    }
}