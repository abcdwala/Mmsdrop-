package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppConfig
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainUiState

@Composable
fun SetupDiagnosticsScreen(
  state: MainUiState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("setup_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
        }
        Text("Backend & Telegram Setup", style = MaterialTheme.typography.headlineSmall)
      }
    }

    item {
      DiagnosticRow("SUPABASE_URL", AppConfig.isSupabaseUrlValid, "Set in AI Studio Secrets -> Settings")
    }
    item {
      DiagnosticRow("SUPABASE_ANON_KEY", AppConfig.isAnonKeyValid, "Set Supabase public anon key in AI Studio Secrets")
    }
    item {
      DiagnosticRow("TELEGRAM_BOT_USERNAME", AppConfig.isBotUsernameValid, "Set your BotFather username (without @)")
    }
    item {
      DiagnosticRow(
        "Telegram Session",
        state.isAuthenticated,
        state.profile?.let { "Authenticated as TG #${it.telegramId} (Owner=${state.isOwner})" }
          ?: "Pending Telegram WebApp.initData authentication",
      )
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Required Deployment Steps", style = MaterialTheme.typography.titleLarge)
          Text(
            "1. Apply supabase/migrations/001_initial_schema.sql in your Supabase SQL Editor.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "2. In Supabase Dashboard -> Edge Functions -> Secrets, set `TELEGRAM_BOT_TOKEN`, `OWNER_TELEGRAM_ID=8254788387`, and `DROPMMS_JWT_SECRET` (your Legacy JWT Secret).",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "3. Deploy the 5 Edge Functions (`telegram-auth`, `content-access`, `payment-workflow`, `admin-api`, `user-actions`) and disable gateway 'Verify JWT with legacy secret' on each.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "4. Host `webapp/` over HTTPS and link it in @BotFather as your Telegram Mini App URL.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "5. Configure `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `TELEGRAM_BOT_USERNAME` in the AI Studio Secrets panel.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun DiagnosticRow(
  label: String,
  ok: Boolean,
  detail: String,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, if (ok) SuccessEmerald.copy(alpha = 0.45f) else WarningAmber.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = if (ok) SuccessEmerald else WarningAmber,
      )
      Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
