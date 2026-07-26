package com.example.personalvault.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personalvault.repository.VaultRepository

class TrashCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            VaultRepository(applicationContext).purgeOldTrash(30)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
