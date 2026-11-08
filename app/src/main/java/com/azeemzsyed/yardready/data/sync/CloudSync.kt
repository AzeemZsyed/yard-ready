package com.azeemzsyed.yardready.data.sync

import android.util.Log
import com.azeemzsyed.yardready.data.local.MaintenanceLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/** Backs up maintenance history to the cloud so it survives a phone change. */
interface CloudSync {
    val enabled: Boolean
    /** Uploads the logs and returns the ids that were saved. */
    suspend fun upload(logs: List<MaintenanceLog>): List<Long>
}

/** Used when no google-services.json is present: everything stays on the device. */
object NoOpCloudSync : CloudSync {
    override val enabled = false
    override suspend fun upload(logs: List<MaintenanceLog>): List<Long> = emptyList()
}

/**
 * Stores logs in Firestore under users/{uid}/logs/{id}, signing in anonymously
 * so no account is needed.
 */
class FirebaseCloudSync(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : CloudSync {

    override val enabled = true

    override suspend fun upload(logs: List<MaintenanceLog>): List<Long> {
        if (logs.isEmpty()) return emptyList()
        return try {
            val user = auth.currentUser ?: auth.signInAnonymously().await().user ?: return emptyList()
            val collection = db.collection("users").document(user.uid).collection("logs")
            val batch = db.batch()
            for (log in logs) {
                batch.set(
                    collection.document(log.id.toString()),
                    mapOf(
                        "locationId" to log.locationId,
                        "taskType" to log.taskType.name,
                        "completedAt" to log.completedAt,
                        "notes" to log.notes,
                    ),
                    SetOptions.merge(),
                )
            }
            batch.commit().await()
            logs.map { it.id }
        } catch (e: Exception) {
            Log.w("CloudSync", "Backup failed, will retry later", e)
            emptyList()
        }
    }
}
