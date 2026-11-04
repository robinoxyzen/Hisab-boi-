package com.hisabboi.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.hisabboi.model.BackupRepository
import com.hisabboi.model.LocalNotifier
import com.hisabboi.model.MediaUploader
import com.hisabboi.model.Reminder
import com.hisabboi.model.ReminderRepository
import com.hisabboi.model.TokenRepository
import com.hisabboi.model.VerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : ReminderRepository {
    override fun observe(uid: String, businessId: String): Flow<List<Reminder>> = callbackFlow {
        val reg = db.biz(uid, businessId).collection("reminders").addSnapshotListener { snap, err ->
            if (err != null || snap == null) return@addSnapshotListener
            trySend(snap.documents.map { it.toReminder() }.sortedByDescending { it.createdAt })
        }
        awaitClose { reg.remove() }
    }

    override suspend fun add(uid: String, businessId: String, reminder: Reminder): Result<Reminder> = runCatching {
        val ref = db.biz(uid, businessId).collection("reminders").document()
        val saved = reminder.copy(id = ref.id, createdAt = System.currentTimeMillis())
        ref.set(saved.toMap()).await()
        saved
    }.mapError()

    override suspend fun markQueued(uid: String, businessId: String, id: String): Result<Unit> = runCatching {
        db.biz(uid, businessId).collection("reminders").document(id)
            .update(mapOf("status" to "queued")).await()
        Unit
    }.mapError()
}

@Singleton
class TokenRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : TokenRepository {
    override suspend fun save(uid: String, token: String) {
        val id = token.hashCode().toUInt().toString(16)
        db.collection("users").document(uid).collection("fcmTokens").document(id)
            .set(
                mapOf(
                    "token" to token,
                    "updatedAt" to System.currentTimeMillis(),
                ),
            ).await()
    }
}

@Singleton
class AndroidNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalNotifier {
    override fun show(title: String, body: String, dedupeKey: String?) {
        val prefs = context.getSharedPreferences("hisab_notify", Context.MODE_PRIVATE)
        if (dedupeKey != null && prefs.getBoolean(dedupeKey, false)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("hisab_pay", "পেমেন্ট ও তাগাদা", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "hisab_pay")
            .setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify((dedupeKey ?: body).hashCode(), notification)
            if (dedupeKey != null) prefs.edit().putBoolean(dedupeKey, true).apply()
        }
    }
}

/**
 * Full images also go to Cloud Storage under the owner's uid.
 * Firestore keeps a copy so the ledger still syncs if Storage is not enabled yet.
 */
@Singleton
class FirebaseMediaUploader @Inject constructor(
    private val storage: FirebaseStorage,
) : MediaUploader {
    override suspend fun upload(storagePath: String, jpeg: ByteArray): String {
        val ref = storage.reference.child(storagePath)
        ref.putBytes(jpeg).await()
        return ref.path
    }
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
) : BackupRepository {
    override suspend fun exportJson(uid: String): Result<String> = runCatching {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val businesses = JSONArray()
        val bizSnap = db.collection("users").document(uid).collection("businesses").get().await()
        for (doc in bizSnap.documents) {
            val obj = JSONObject(doc.data ?: emptyMap<String, Any>())
            obj.put("id", doc.id)
            // Badge is not portable. Restore always writes an unverified shop.
            obj.put("verified", false)
            obj.put("verificationStatus", "none")
            obj.put("verificationReason", "")
            val bid = doc.id
            obj.put("contacts", arrayOf(uid, bid, "contacts"))
            obj.put("transactions", arrayOf(uid, bid, "transactions"))
            obj.put("cashEntries", arrayOf(uid, bid, "cashEntries"))
            obj.put("products", arrayOf(uid, bid, "products"))
            obj.put("reminders", arrayOf(uid, bid, "reminders"))
            val payment = db.biz(uid, bid).collection("paymentProfile").document("default").get().await()
            if (payment.exists()) obj.put("paymentProfile", JSONObject(payment.data ?: emptyMap<String, Any>()))
            businesses.put(obj)
        }
        root.put("businesses", businesses)
        root.toString(2)
    }.mapError()

    private suspend fun arrayOf(uid: String, bid: String, name: String): JSONArray {
        val snap = db.biz(uid, bid).collection(name).get().await()
        val array = JSONArray()
        snap.documents.forEach { doc ->
            val obj = JSONObject(doc.data ?: emptyMap<String, Any>())
            obj.put("id", doc.id)
            array.put(obj)
        }
        return array
    }

    override suspend fun restoreJson(uid: String, json: String): Result<Int> = runCatching {
        val root = JSONObject(json)
        val businesses = root.getJSONArray("businesses")
        var count = 0
        for (i in 0 until businesses.length()) {
            val obj = businesses.getJSONObject(i)
            val bid = obj.getString("id")
            val ref = db.biz(uid, bid)
            val map = obj.toFlatMap().toMutableMap()
            map["verified"] = false
            map["verificationStatus"] = VerificationStatus.NONE.name.lowercase()
            map["verificationReason"] = ""
            listOf("contacts", "transactions", "cashEntries", "products", "reminders", "paymentProfile", "id")
                .forEach { map.remove(it) }
            ref.set(map).await()
            restoreCol(ref.collection("contacts"), obj.optJSONArray("contacts"))
            restoreCol(ref.collection("transactions"), obj.optJSONArray("transactions"))
            restoreCol(ref.collection("cashEntries"), obj.optJSONArray("cashEntries"))
            restoreCol(ref.collection("products"), obj.optJSONArray("products"))
            restoreCol(ref.collection("reminders"), obj.optJSONArray("reminders"))
            if (obj.has("paymentProfile")) {
                ref.collection("paymentProfile").document("default").set(obj.getJSONObject("paymentProfile").toFlatMap()).await()
            }
            count++
        }
        count
    }.mapError()

    private suspend fun restoreCol(col: com.google.firebase.firestore.CollectionReference, array: JSONArray?) {
        if (array == null) return
        var batch = db.batch()
        var n = 0
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val map = obj.toFlatMap().toMutableMap()
            map.remove("id")
            batch.set(col.document(id), map)
            n++
            if (n == 400) {
                batch.commit().await()
                batch = db.batch()
                n = 0
            }
        }
        if (n > 0) batch.commit().await()
    }
}

private fun JSONObject.toFlatMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = unwrap(get(key))
    }
    return map
}

private fun unwrap(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.toFlatMap()
    is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
    else -> value
}
