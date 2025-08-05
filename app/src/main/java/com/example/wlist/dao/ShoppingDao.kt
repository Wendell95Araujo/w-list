package com.example.wlist.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.wlist.model.ShoppingItem

@Dao
interface ShoppingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem)

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Update
    suspend fun updateItems(items: List<ShoppingItem>)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    @Query("SELECT * FROM shopping_items_table ORDER BY category ASC, displayOrder ASC")
    suspend fun getAllItems(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items_table WHERE productNameNormalized LIKE :query ORDER BY category ASC, displayOrder ASC")
    suspend fun searchItemsByName(query: String): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items_table WHERE id = :itemId")
    suspend fun getItemById(itemId: String): ShoppingItem?
}