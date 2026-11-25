package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dropmms_session")

data class StoredSession(
  val accessToken: String = "",
  val telegramId: Long = 0L,
  val isOwner: Boolean = false,
  val pendingReferralCode: String = "",
)

class SessionStore(private val context: Context) {
  private object Keys {
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val TELEGRAM_ID = longPreferencesKey("telegram_id")
    val IS_OWNER = booleanPreferencesKey("is_owner")
    val REFERRAL_CODE = stringPreferencesKey("pending_referral_code")
  }

  val sessionFlow: Flow<StoredSession> = context.dataStore.data.map { prefs ->
    StoredSession(
      accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
      telegramId = prefs[Keys.TELEGRAM_ID] ?: 0L,
      isOwner = prefs[Keys.IS_OWNER] ?: false,
      pendingReferralCode = prefs[Keys.REFERRAL_CODE].orEmpty(),
    )
  }

  suspend fun saveSession(accessToken: String, telegramId: Long, isOwner: Boolean) {
    context.dataStore.edit { prefs ->
      prefs[Keys.ACCESS_TOKEN] = accessToken
      prefs[Keys.TELEGRAM_ID] = telegramId
      prefs[Keys.IS_OWNER] = isOwner
    }
  }

  suspend fun savePendingReferral(code: String) {
    context.dataStore.edit { prefs ->
      prefs[Keys.REFERRAL_CODE] = code
    }
  }

  suspend fun clearSession() {
    context.dataStore.edit { prefs ->
      prefs.remove(Keys.ACCESS_TOKEN)
      prefs.remove(Keys.TELEGRAM_ID)
      prefs.remove(Keys.IS_OWNER)
    }
  }
}
