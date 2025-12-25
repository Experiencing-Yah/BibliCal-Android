package com.example.biblicalmonth.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "biblical_month_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val STATUS_NOTIFICATION_ENABLED = booleanPreferencesKey("status_notification_enabled")
        val PROMPTS_ENABLED = booleanPreferencesKey("prompts_enabled")
        val SELECTED_CALENDAR_ID = longPreferencesKey("selected_calendar_id")
        val MONTH_NAMING_MODE = stringPreferencesKey("month_naming_mode")
        val FIRSTFRUITS_RULE = stringPreferencesKey("firstfruits_rule")

        val LAST_MOON_PROMPT_EPOCH_DAY = longPreferencesKey("last_moon_prompt_epoch_day")
        val LAST_AVIV_PROMPT_EPOCH_DAY = longPreferencesKey("last_aviv_prompt_epoch_day")
    }

    val statusNotificationEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.STATUS_NOTIFICATION_ENABLED] ?: true }

    val promptsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PROMPTS_ENABLED] ?: true }

    val selectedCalendarId: Flow<Long> =
        context.dataStore.data.map { it[Keys.SELECTED_CALENDAR_ID] ?: -1L }

    val monthNamingMode: Flow<MonthNamingMode> =
        context.dataStore.data.map { prefs ->
            MonthNamingMode.fromStored(prefs[Keys.MONTH_NAMING_MODE])
        }

    val firstfruitsRule: Flow<FirstfruitsRule> =
        context.dataStore.data.map { prefs ->
            FirstfruitsRule.fromStored(prefs[Keys.FIRSTFRUITS_RULE])
        }

    suspend fun setStatusNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STATUS_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setPromptsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROMPTS_ENABLED] = enabled }
    }

    suspend fun setSelectedCalendarId(calendarId: Long) {
        context.dataStore.edit { it[Keys.SELECTED_CALENDAR_ID] = calendarId }
    }

    suspend fun setMonthNamingMode(mode: MonthNamingMode) {
        context.dataStore.edit { it[Keys.MONTH_NAMING_MODE] = mode.storedValue }
    }

    suspend fun setFirstfruitsRule(rule: FirstfruitsRule) {
        context.dataStore.edit { it[Keys.FIRSTFRUITS_RULE] = rule.storedValue }
    }

    suspend fun getLastMoonPromptEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_MOON_PROMPT_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastMoonPromptEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_MOON_PROMPT_EPOCH_DAY] = epochDay }
    }

    suspend fun getLastAvivPromptEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_AVIV_PROMPT_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastAvivPromptEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_AVIV_PROMPT_EPOCH_DAY] = epochDay }
    }
}

enum class MonthNamingMode(val storedValue: String) {
    ORDINAL("ordinal"),
    NUMBERED("numbered");

    companion object {
        fun fromStored(stored: String?): MonthNamingMode =
            values().firstOrNull { it.storedValue == stored } ?: ORDINAL
    }
}

enum class FirstfruitsRule(val storedValue: String) {
    FIXED_DAY_16("fixed_day_16"),
    SUNDAY_DURING_UNLEAVENED_BREAD("sunday_during_unleavened_bread");

    companion object {
        fun fromStored(stored: String?): FirstfruitsRule =
            values().firstOrNull { it.storedValue == stored } ?: FIXED_DAY_16
    }
}

