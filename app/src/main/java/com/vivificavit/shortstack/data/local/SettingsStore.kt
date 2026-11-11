package com.vivificavit.shortstack.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The few settings kept between launches. */
interface SettingsStore {
    /** Which kid's ledger was open, so the app reopens on it. */
    val lastSelectedKid: Flow<String?>
    suspend fun setLastSelectedKid(name: String)

    /** The code of the currency amounts are shown in (see AppCurrency); null until one is set. */
    val currencyCode: Flow<String?>
    suspend fun setCurrencyCode(code: String)
}

/** SharedPreferences file used before DataStore; migrated on first read. */
private const val LEGACY_PREFS_NAME = "allowance_tracker_prefs"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME)) }
)

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.applicationContext.settingsDataStore

    override val lastSelectedKid: Flow<String?> =
        dataStore.data.map { it[KEY_LAST_SELECTED_KID] }

    override suspend fun setLastSelectedKid(name: String) {
        dataStore.edit { it[KEY_LAST_SELECTED_KID] = name }
    }

    override val currencyCode: Flow<String?> =
        dataStore.data.map { it[KEY_CURRENCY] }

    override suspend fun setCurrencyCode(code: String) {
        dataStore.edit { it[KEY_CURRENCY] = code }
    }

    private companion object {
        // Same name as the legacy SharedPreferences key, so the migration carries it over.
        val KEY_LAST_SELECTED_KID = stringPreferencesKey("last_selected_kid")
        val KEY_CURRENCY = stringPreferencesKey("currency")
    }
}
