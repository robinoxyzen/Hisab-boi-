package com.hisabboi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisabboi.model.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.hisabStore by preferencesDataStore("hisab_prefs")

@Singleton
class UserPrefs @Inject constructor(@ApplicationContext context: Context) : SettingsStore {
    private val store = context.hisabStore
    private val activeKey = stringPreferencesKey("active_business")
    private val smsKey = stringPreferencesKey("sms_mode")
    private val notifyKey = booleanPreferencesKey("notifications")

    val activeBusinessId: Flow<String?> = store.data.map { it[activeKey] }
    override val smsMode: Flow<String> = store.data.map { it[smsKey] ?: "device" }
    override val notifications: Flow<Boolean> = store.data.map { it[notifyKey] ?: true }

    suspend fun setActive(id: String) {
        store.edit { it[activeKey] = id }
    }

    override suspend fun setSmsMode(mode: String) {
        store.edit { it[smsKey] = mode }
    }

    override suspend fun setNotifications(enabled: Boolean) {
        store.edit { it[notifyKey] = enabled }
    }
}
