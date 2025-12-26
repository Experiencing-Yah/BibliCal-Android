package com.example.biblicalmonth.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
        val INCLUDE_HANUKKAH = booleanPreferencesKey("include_hanukkah")
        val INCLUDE_PURIM = booleanPreferencesKey("include_purim")

        val LAST_MOON_PROMPT_EPOCH_DAY = longPreferencesKey("last_moon_prompt_epoch_day")
        val LAST_AVIV_PROMPT_EPOCH_DAY = longPreferencesKey("last_aviv_prompt_epoch_day")
        val LAST_SHABBAT_REMINDER_EPOCH_DAY = longPreferencesKey("last_shabbat_reminder_epoch_day")
        val LAST_FEAST_REMINDER_EPOCH_DAY = longPreferencesKey("last_feast_reminder_epoch_day")
        val PROJECT_EXTRA_MONTH = booleanPreferencesKey("project_extra_month")
        val PROJECTED_MONTH_LENGTHS = stringPreferencesKey("projected_month_lengths") // JSON: {"year-month": "29" or "30"}
        
        // Cached location for widget use
        val CACHED_LATITUDE = doublePreferencesKey("cached_latitude")
        val CACHED_LONGITUDE = doublePreferencesKey("cached_longitude")
        val CACHED_LOCATION_TIMESTAMP = longPreferencesKey("cached_location_timestamp")
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

    val includeHanukkah: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_HANUKKAH] ?: false }

    val includePurim: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.INCLUDE_PURIM] ?: false }

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

    suspend fun setIncludeHanukkah(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_HANUKKAH] = enabled }
    }

    suspend fun setIncludePurim(enabled: Boolean) {
        context.dataStore.edit { it[Keys.INCLUDE_PURIM] = enabled }
    }

    suspend fun getLastShabbatReminderEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_SHABBAT_REMINDER_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastShabbatReminderEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_SHABBAT_REMINDER_EPOCH_DAY] = epochDay }
    }

    suspend fun getLastFeastReminderEpochDay(): Long =
        context.dataStore.data.first()[Keys.LAST_FEAST_REMINDER_EPOCH_DAY] ?: Long.MIN_VALUE

    suspend fun setLastFeastReminderEpochDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.LAST_FEAST_REMINDER_EPOCH_DAY] = epochDay }
    }

    val projectExtraMonth: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PROJECT_EXTRA_MONTH] ?: false }

    suspend fun setProjectExtraMonth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PROJECT_EXTRA_MONTH] = enabled }
    }

    /**
     * Get projected month length (29 or 30) for a specific year-month, or null if not set
     */
    suspend fun getProjectedMonthLength(year: Int, month: Int): Int? {
        val jsonStr = context.dataStore.data.first()[Keys.PROJECTED_MONTH_LENGTHS] ?: return null
        if (jsonStr.isEmpty()) return null
        
        return try {
            val json = org.json.JSONObject(jsonStr)
            val key = "$year-$month"
            val value = json.optString(key, null)
            if (value != null && value.isNotEmpty()) {
                value.toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set projected month length (29 or 30) for a specific year-month, or null to remove
     */
    suspend fun setProjectedMonthLength(year: Int, month: Int, length: Int?) {
        context.dataStore.edit { prefs ->
            val currentJsonStr = prefs[Keys.PROJECTED_MONTH_LENGTHS] ?: "{}"
            val json = try {
                org.json.JSONObject(currentJsonStr)
            } catch (e: Exception) {
                org.json.JSONObject()
            }
            
            val key = "$year-$month"
            if (length != null) {
                json.put(key, length.toString())
            } else {
                json.remove(key)
            }
            
            prefs[Keys.PROJECTED_MONTH_LENGTHS] = json.toString()
        }
    }

    /**
     * Get all projected month lengths for a given year
     */
    suspend fun getProjectedMonthsForYear(year: Int): Map<Int, Int> {
        val jsonStr = context.dataStore.data.first()[Keys.PROJECTED_MONTH_LENGTHS] ?: return emptyMap()
        if (jsonStr.isEmpty()) return emptyMap()
        
        return try {
            val json = org.json.JSONObject(jsonStr)
            val result = mutableMapOf<Int, Int>()
            json.keys().forEach { key ->
                if (key.startsWith("$year-")) {
                    val month = key.substringAfter("-").toIntOrNull()
                    val length = json.optString(key, null)?.toIntOrNull()
                    if (month != null && length != null) {
                        result[month] = length
                    }
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Cache location for widget use. Location is considered valid for 24 hours.
     */
    suspend fun cacheLocation(latitude: Double, longitude: Double) {
        context.dataStore.edit {
            it[Keys.CACHED_LATITUDE] = latitude
            it[Keys.CACHED_LONGITUDE] = longitude
            it[Keys.CACHED_LOCATION_TIMESTAMP] = System.currentTimeMillis()
        }
    }

    /**
     * Get cached location if it exists and is recent (within 24 hours).
     * Returns null if no cached location or if it's stale.
     */
    suspend fun getCachedLocation(): Pair<Double, Double>? {
        val prefs = context.dataStore.data.first()
        val lat = prefs[Keys.CACHED_LATITUDE]
        val lon = prefs[Keys.CACHED_LONGITUDE]
        val timestamp = prefs[Keys.CACHED_LOCATION_TIMESTAMP]
        
        if (lat == null || lon == null || timestamp == null) {
            return null
        }
        
        // Check if location is recent (within 24 hours)
        val ageMillis = System.currentTimeMillis() - timestamp
        val ageHours = ageMillis / (1000 * 60 * 60)
        if (ageHours > 24) {
            return null // Location is stale
        }
        
        return Pair(lat, lon)
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

