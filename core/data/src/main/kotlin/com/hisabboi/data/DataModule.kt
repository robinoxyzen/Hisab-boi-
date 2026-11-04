package com.hisabboi.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.BackupRepository
import com.hisabboi.model.CashRepository
import com.hisabboi.model.ContactRepository
import com.hisabboi.model.IdentityVerifier
import com.hisabboi.model.KycRepository
import com.hisabboi.model.LocalNotifier
import com.hisabboi.model.MediaUploader
import com.hisabboi.model.PaymentRepository
import com.hisabboi.model.ProfilePhotoRepository
import com.hisabboi.model.ProductRepository
import com.hisabboi.model.PublicShopRepository
import com.hisabboi.model.ReminderRepository
import com.hisabboi.model.ScanBus
import com.hisabboi.model.SettingsStore
import com.hisabboi.model.TokenRepository
import com.hisabboi.model.TransactionRepository
import com.hisabboi.model.VerificationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        val cache = PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cache)
            .build()
        return db
    }

    @Provides
    @Singleton
    fun storage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds @Singleton abstract fun auth(impl: GoogleAuthGateway): AuthGateway
    @Binds @Singleton abstract fun session(impl: SessionRepositoryImpl): ActiveBusinessSource
    @Binds @Singleton abstract fun contacts(impl: ContactRepositoryImpl): ContactRepository
    @Binds @Singleton abstract fun transactions(impl: TransactionRepositoryImpl): TransactionRepository
    @Binds @Singleton abstract fun cash(impl: CashRepositoryImpl): CashRepository
    @Binds @Singleton abstract fun products(impl: ProductRepositoryImpl): ProductRepository
    @Binds @Singleton abstract fun payments(impl: PaymentRepositoryImpl): PaymentRepository
    @Binds @Singleton abstract fun shops(impl: PublicShopRepositoryImpl): PublicShopRepository
    @Binds @Singleton abstract fun kyc(impl: KycRepositoryImpl): KycRepository
    @Binds @Singleton abstract fun verification(impl: VerificationRepositoryImpl): VerificationRepository
    @Binds @Singleton abstract fun identity(impl: ManualAdminVerifier): IdentityVerifier
    @Binds @Singleton abstract fun reminders(impl: ReminderRepositoryImpl): ReminderRepository
    @Binds @Singleton abstract fun backup(impl: BackupRepositoryImpl): BackupRepository
    @Binds @Singleton abstract fun settings(impl: UserPrefs): SettingsStore
    @Binds @Singleton abstract fun scan(impl: ScanBusImpl): ScanBus
    @Binds @Singleton abstract fun notifier(impl: AndroidNotifier): LocalNotifier
    @Binds @Singleton abstract fun tokens(impl: TokenRepositoryImpl): TokenRepository
    @Binds @Singleton abstract fun media(impl: FirebaseMediaUploader): MediaUploader
    @Binds @Singleton abstract fun profilePhoto(impl: ProfilePhotoRepositoryImpl): ProfilePhotoRepository
}
