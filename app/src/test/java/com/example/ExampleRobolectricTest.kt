package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name and owner id match DropMms Pro requirements`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("DropMms Pro", context.getString(R.string.app_name))
    assertEquals(8254788387L, AppConfig.OWNER_TELEGRAM_ID)
  }

  @Test
  fun `unconfigured placeholders are honestly reported`() {
    if (BuildConfig.SUPABASE_URL.startsWith("MY_") || BuildConfig.SUPABASE_URL.isBlank()) {
      assertFalse(AppConfig.isBackendConfigured)
      assertTrue(AppConfig.missingConfigurationItems.isNotEmpty())
    }
  }

  @Test
  fun `bottom navigation traverses primary tabs and setup diagnostics`() {
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("nav_home").assertIsDisplayed()
    composeRule.onNodeWithTag("nav_explore").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("explore_search_input").assertIsDisplayed()

    composeRule.onNodeWithTag("nav_premium").performClick()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("nav_profile").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("profile_setup_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("setup_back_button").assertIsDisplayed()
  }
}
