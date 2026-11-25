package com.example.core

import com.example.BuildConfig

object AppConfig {
  const val OWNER_TELEGRAM_ID: Long = 8254788387L

  val supabaseUrl: String
    get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

  val supabaseAnonKey: String
    get() = BuildConfig.SUPABASE_ANON_KEY.trim()

  val telegramBotUsername: String
    get() = BuildConfig.TELEGRAM_BOT_USERNAME.trim().removePrefix("@")

  val isSupabaseUrlValid: Boolean
    get() = supabaseUrl.startsWith("https://") &&
      supabaseUrl.removePrefix("https://").contains(".") &&
      !supabaseUrl.contains("MY_SUPABASE_URL") &&
      !supabaseUrl.contains("YOUR_PROJECT")

  val isAnonKeyValid: Boolean
    get() = supabaseAnonKey.isNotBlank() &&
      !supabaseAnonKey.startsWith("MY_") &&
      !supabaseAnonKey.startsWith("YOUR_")

  val isBotUsernameValid: Boolean
    get() = telegramBotUsername.isNotBlank() &&
      !telegramBotUsername.startsWith("MY_") &&
      !telegramBotUsername.startsWith("YOUR_")

  val isBackendConfigured: Boolean
    get() = isSupabaseUrlValid && isAnonKeyValid

  val missingConfigurationItems: List<String>
    get() = buildList {
      if (!isSupabaseUrlValid) add("SUPABASE_URL (configure in AI Studio Secrets panel)")
      if (!isAnonKeyValid) add("SUPABASE_ANON_KEY (configure in AI Studio Secrets panel)")
      if (!isBotUsernameValid) add("TELEGRAM_BOT_USERNAME (configure in AI Studio Secrets panel)")
    }
}
