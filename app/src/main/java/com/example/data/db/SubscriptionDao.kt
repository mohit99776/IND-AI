package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM user_subscription WHERE id = 1 LIMIT 1")
    fun getSubscriptionFlow(): Flow<UserSubscriptionEntity?>

    @Query("SELECT * FROM user_subscription WHERE id = 1 LIMIT 1")
    suspend fun getSubscription(): UserSubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSubscription(subscription: UserSubscriptionEntity)
}
