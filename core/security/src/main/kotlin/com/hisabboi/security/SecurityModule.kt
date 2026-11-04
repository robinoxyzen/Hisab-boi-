package com.hisabboi.security

import com.hisabboi.model.FieldCipher
import com.hisabboi.model.LockStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds @Singleton abstract fun lock(impl: LockStoreImpl): LockStore
    @Binds @Singleton abstract fun cipher(impl: AndroidFieldCipher): FieldCipher
}
