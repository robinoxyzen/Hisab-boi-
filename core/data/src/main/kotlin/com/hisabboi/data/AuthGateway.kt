package com.hisabboi.data

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.ProfilePhoto
import com.hisabboi.model.ProfileRead
import com.hisabboi.model.SessionUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google is the only sign-in provider. [AuthGateway] is the extension point for a later provider.
 * Admin is never decided here by email. The Cloud Function grants the custom claim; we only read it.
 */
@Singleton
class GoogleAuthGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
) : AuthGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentialManager = CredentialManager.create(context)
    private val _user = MutableStateFlow<SessionUser?>(null)
    override val currentUser: StateFlow<SessionUser?> = _user

    init {
        auth.addAuthStateListener { firebase ->
            scope.launch { publish(firebase.currentUser, forceRefresh = false) }
        }
    }

    override suspend fun signIn(host: Any): Result<SessionUser> = withContext(Dispatchers.Main) {
        runCatching {
            val activity = host as? Activity ?: error("সাইন-ইন স্ক্রিন পাওয়া যায়নি")
            val clientId = webClientId()
            if (clientId.isBlank() || clientId.contains("placeholder")) {
                error("google-services.json এখনো প্লেসহোল্ডার। README অনুযায়ী Firebase ও Google সাইন-ইন সেটআপ করুন।")
            }
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                error("গুগল ক্রেডেনশিয়াল পাওয়া যায়নি")
            }
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(google.idToken, null)
            val signed = auth.signInWithCredential(firebaseCred).await()
            val user = signed.user ?: error("সাইন-ইন সম্পূর্ণ হয়নি")
            // A missing or unreachable user document must not block sign-in.
            // The shopkeeper continues to onboarding when there is no business yet.
            runCatching { ensureProfile(user) }
            runCatching { refreshAdminClaim() }
            publish(user, forceRefresh = true)
            _user.value ?: error("সেশন তৈরি হয়নি")
        }
    }

    override suspend fun reauthenticate(host: Any): Result<SessionUser> {
        val before = auth.currentUser?.uid
        return signIn(host).mapCatching { user ->
            if (before != null && user.uid != before) error("একই গুগল অ্যাকাউন্ট দিয়ে নিশ্চিত করুন")
            user
        }
    }

    override suspend fun refreshAdminClaim(): Result<Boolean> = runCatching {
        functions.getHttpsCallable("refreshAdminClaim").call().await()
        val user = auth.currentUser ?: return@runCatching false
        publish(user, forceRefresh = true)
        _user.value?.isAdmin == true
    }

    override fun signOut() {
        auth.signOut()
        _user.value = null
    }

    override fun applyLocalPhoto(custom: String?, hideGoogle: Boolean) {
        val current = _user.value ?: return
        val uploaded = custom?.takeIf { ProfilePhoto.isUploaded(it) }
        val hide = hideGoogle && uploaded == null
        _user.value = current.copy(
            customPhotoUrl = uploaded,
            hideGooglePhoto = hide,
            photoUrl = ProfilePhoto.resolve(uploaded, current.googlePhotoUrl, hide),
        )
    }

    private suspend fun publish(user: FirebaseUser?, forceRefresh: Boolean) {
        if (user == null) {
            _user.value = null
            return
        }
        val token = runCatching { user.getIdToken(forceRefresh).await() }.getOrNull()
        val google = user.photoUrl?.toString()
        val prefs = readPhotoPrefs(user.uid)
        val custom = prefs.stored?.takeIf { ProfilePhoto.isUploaded(it) }
        _user.value = SessionUser(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            photoUrl = ProfilePhoto.resolve(prefs.stored, google, prefs.hideGoogle),
            isAdmin = token?.claims?.get("admin") == true,
            googlePhotoUrl = google,
            customPhotoUrl = custom,
            hideGooglePhoto = prefs.hideGoogle && custom == null,
        )
    }

    /**
     * Reads users/{uid} with a short retry. Offline, cache, or a brand-new database
     * all resolve to "continue" instead of failing the Google sign-in.
     */
    private suspend fun ensureProfile(user: FirebaseUser) {
        val ref = db.collection("users").document(user.uid)
        when (readProfile(ref)) {
            ProfileHit.Exists -> Unit
            ProfileHit.Missing -> createProfile(ref, user)
            ProfileHit.Unknown -> Unit
        }
    }

    private enum class ProfileHit { Exists, Missing, Unknown }

    private suspend fun readProfile(ref: DocumentReference): ProfileHit {
        val max = 3
        var attempt = 0
        while (attempt < max) {
            val server = runCatching { withTimeout(4_000) { ref.get(Source.SERVER).await() } }
            if (server.isSuccess) {
                return if (server.getOrThrow().exists()) ProfileHit.Exists else ProfileHit.Missing
            }
            val cached = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
            when (
                ProfileRead.next(
                    serverSucceeded = false,
                    serverExists = false,
                    cacheExists = cached?.exists() == true,
                    transient = ProfileRead.isTransient(server.exceptionOrNull()?.message),
                    attempt = attempt,
                    maxAttempts = max,
                )
            ) {
                ProfileRead.Step.USE_CACHE -> return ProfileHit.Exists
                ProfileRead.Step.RETRY -> {
                    attempt++
                    delay(400L * attempt)
                }
                else -> return ProfileHit.Unknown
            }
        }
        return ProfileHit.Unknown
    }

    private suspend fun createProfile(ref: DocumentReference, user: FirebaseUser) {
        runCatching {
            ref.set(
                mapOf(
                    "email" to (user.email ?: ""),
                    "displayName" to (user.displayName ?: ""),
                    "createdAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()
        }
    }

    private data class PhotoPrefs(val stored: String?, val hideGoogle: Boolean)

    private suspend fun readPhotoPrefs(uid: String): PhotoPrefs {
        val ref = db.collection("users").document(uid)
        val cached = runCatching { ref.get(Source.CACHE).await() }.getOrNull()
        if (cached != null && cached.exists()) {
            return PhotoPrefs(cached.getString("photoUrl"), cached.getBoolean("hideGooglePhoto") == true)
        }
        val server = runCatching { withTimeout(2_500) { ref.get(Source.SERVER).await() } }.getOrNull()
        if (server != null && server.exists()) {
            return PhotoPrefs(server.getString("photoUrl"), server.getBoolean("hideGooglePhoto") == true)
        }
        return PhotoPrefs(null, false)
    }

    private fun webClientId(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }
}
