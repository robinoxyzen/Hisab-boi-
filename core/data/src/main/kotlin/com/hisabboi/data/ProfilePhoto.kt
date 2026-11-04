package com.hisabboi.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.ProfilePhotoRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile photos stay under the signed-in user's own prefix.
 * The download URL is written to users/{uid}.photoUrl. KYC files are never touched here.
 */
@Singleton
class ProfilePhotoRepositoryImpl @Inject constructor(
    private val storage: FirebaseStorage,
    private val db: FirebaseFirestore,
    private val auth: AuthGateway,
) : ProfilePhotoRepository {
    override suspend fun upload(uid: String, jpeg: ByteArray): Result<String> = runCatching {
        require(jpeg.isNotEmpty()) { "ছবি পড়া যায়নি। অন্য একটি বেছে নিন।" }
        require(jpeg.size < 5 * 1024 * 1024) { "ছবি ৫ এমবির কম হতে হবে।" }
        val ref = storage.reference.child("users/$uid/profile.jpg")
        val meta = StorageMetadata.Builder().setContentType("image/jpeg").build()
        ref.putBytes(jpeg, meta).await()
        val url = ref.downloadUrl.await().toString()
        db.collection("users").document(uid).set(
            mapOf(
                "photoUrl" to url,
                "hideGooglePhoto" to false,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
        auth.applyLocalPhoto(custom = url, hideGoogle = false)
        url
    }.mapError()

    override suspend fun remove(uid: String): Result<Unit> = runCatching {
        val custom = auth.currentUser.value?.customPhotoUrl.orEmpty()
        if (custom.isNotBlank()) {
            runCatching { storage.reference.child("users/$uid/profile.jpg").delete().await() }
            db.collection("users").document(uid).set(
                mapOf(
                    "photoUrl" to FieldValue.delete(),
                    "hideGooglePhoto" to false,
                ),
                SetOptions.merge(),
            ).await()
            auth.applyLocalPhoto(custom = null, hideGoogle = false)
        } else {
            db.collection("users").document(uid).set(
                mapOf("hideGooglePhoto" to true),
                SetOptions.merge(),
            ).await()
            auth.applyLocalPhoto(custom = null, hideGoogle = true)
        }
    }.mapError()
}
