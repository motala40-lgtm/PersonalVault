package com.example.personalvault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletCardDao {
    @Query("SELECT * FROM wallet_cards ORDER BY createdAt DESC")
    fun getAllCards(): Flow<List<WalletCard>>

    @Insert
    suspend fun insertCard(card: WalletCard): Long

    @Update
    suspend fun updateCard(card: WalletCard)

    @Delete
    suspend fun deleteCard(card: WalletCard)
}
