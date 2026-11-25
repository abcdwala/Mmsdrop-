package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppConfig
import com.example.ui.components.EmptyStateCard
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainUiState

@Composable
fun AdminScreen(
  mainState: MainUiState,
  adminViewModel: AdminViewModel,
  modifier: Modifier = Modifier,
) {
  val adminState by adminViewModel.state.collectAsStateWithLifecycle()
  val tabs = listOf(
    "stats" to "Stats",
    "payments" to "Payments",
    "content" to "Content",
    "catalog" to "Categories & Plans",
    "users" to "Users & Reports",
    "settings" to "Settings & Logs",
  )
  var selectedTab by remember { mutableStateOf("stats") }

  val isAuthorizedOwner = mainState.isAuthenticated &&
    mainState.isOwner &&
    mainState.profile?.telegramId == AppConfig.OWNER_TELEGRAM_ID

  LaunchedEffect(isAuthorizedOwner) {
    if (isAuthorizedOwner) {
      adminViewModel.loadDashboard()
    }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Owner Admin Panel", style = MaterialTheme.typography.headlineMedium)
        }
        if (isAuthorizedOwner) {
          IconButton(
            onClick = { adminViewModel.loadDashboard() },
            modifier = Modifier.testTag("admin_refresh_button"),
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh admin dashboard")
          }
        }
      }
    }

    if (!isAuthorizedOwner) {
      item {
        EmptyStateCard(
          title = "Owner Authorization Required",
          description = "Access is strictly protected server-side for Owner Telegram ID ${AppConfig.OWNER_TELEGRAM_ID}.",
        )
      }
      return@LazyColumn
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tabs, key = { it.first }) { (key, label) ->
          FilterChip(
            selected = selectedTab == key,
            onClick = { selectedTab = key },
            label = { Text(label) },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("admin_tab_$key"),
          )
        }
      }
    }

    when (selectedTab) {
      "stats" -> adminStatsSection(adminState)
      "payments" -> adminPaymentsAndSubsSection(adminState, adminViewModel)
      "content" -> adminContentSection(adminState, adminViewModel)
      "catalog" -> adminCategoriesAndPlansSection(adminState, adminViewModel)
      "users" -> adminUsersAndReportsSection(adminState, adminViewModel)
      "settings" -> adminSettingsAndLogsSection(adminState, mainState.settings, adminViewModel)
    }
  }
}
