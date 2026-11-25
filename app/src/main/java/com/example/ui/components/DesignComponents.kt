package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AppConfig
import com.example.data.model.ContentItem
import com.example.ui.theme.BrandGradient
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalHigh
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun GradientButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Box(
    modifier = modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(
        brush = BrandGradient,
        alpha = if (enabled) 1f else 0.4f,
      )
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = Color.White,
    )
  }
}

@Composable
fun ConfigRequiredBanner(
  missingItems: List<String>,
  onOpenDiagnostics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
        Text(
          text = "Backend Configuration Required",
          style = MaterialTheme.typography.titleMedium,
          color = WarningAmber,
        )
      }
      Text(
        text = "No mock data is used. Configure these secrets in the AI Studio Secrets panel and apply the Supabase migration before live data loads:",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      missingItems.forEach { item ->
        Text(
          text = "• $item",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(
        onClick = onOpenDiagnostics,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("open_setup_diagnostics_button"),
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f)),
      ) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("View Setup Checklist & Diagnostics")
      }
    }
  }
}

@Composable
fun TelegramAuthCard(
  isConfigured: Boolean,
  pendingReferral: String,
  onAuthenticateInitData: (String) -> Unit,
  onOpenSetup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showManualInitData by remember { mutableStateOf(false) }
  var initDataText by remember { mutableStateOf("") }

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
          modifier = Modifier.size(40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
        }
        Column {
          Text("Telegram Authentication", style = MaterialTheme.typography.titleLarge)
          Text(
            "Server-verified HMAC-SHA256 initData session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (pendingReferral.isNotBlank()) {
        StatusPill(text = "Referral code queued: $pendingReferral", color = MaterialTheme.colorScheme.secondary)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (AppConfig.isBotUsernameValid) {
          GradientButton(
            text = "Launch in Telegram Bot",
            enabled = isConfigured,
            onClick = {
              val refQuery = if (pendingReferral.isNotBlank()) "?startapp=$pendingReferral" else ""
              val uri = Uri.parse("https://t.me/${AppConfig.telegramBotUsername}$refQuery")
              context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            modifier = Modifier
              .weight(1f)
              .testTag("launch_telegram_bot_button"),
          )
        }
        OutlinedButton(
          onClick = { showManualInitData = !showManualInitData },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .weight(1f)
            .testTag("toggle_initdata_input_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Verify initData")
        }
      }

      if (showManualInitData) {
        OutlinedTextField(
          value = initDataText,
          onValueChange = { initDataText = it },
          label = { Text("Signed Telegram WebApp.initData") },
          placeholder = { Text("query_id=...&user=...&auth_date=...&hash=...") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("telegram_initdata_input"),
          minLines = 2,
          maxLines = 4,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
            onClick = { onAuthenticateInitData(initDataText) },
            enabled = isConfigured && initDataText.isNotBlank(),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("submit_initdata_button"),
          ) {
            Text("Authenticate Session")
          }
          TextButton(
            onClick = onOpenSetup,
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text("Setup Guide")
          }
        }
      }
    }
  }
}

@Composable
fun ComplianceGateCard(
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var ageChecked by remember { mutableStateOf(false) }
  var consentChecked by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_vault_shield),
        contentDescription = stringResource(id = R.string.cd_compliance_shield),
        modifier = Modifier
          .fillMaxWidth()
          .height(140.dp)
          .clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.Crop,
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Age & Rights Compliance Gate", style = MaterialTheme.typography.titleLarge)
      }
      Text(
        "DropMms Pro strictly limits access to lawful, rights-verified, and consensually produced media. Confirm the requirements below to unlock catalog access.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { ageChecked = !ageChecked }
          .padding(vertical = 4.dp),
      ) {
        Checkbox(
          checked = ageChecked,
          onCheckedChange = { ageChecked = it },
          modifier = Modifier.testTag("age_verify_checkbox"),
        )
        Text("I confirm I am at least 18 years old (or legal age in my jurisdiction).", style = MaterialTheme.typography.bodyMedium)
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { consentChecked = !consentChecked }
          .padding(vertical = 4.dp),
      ) {
        Checkbox(
          checked = consentChecked,
          onCheckedChange = { consentChecked = it },
          modifier = Modifier.testTag("consent_verify_checkbox"),
        )
        Text("I acknowledge the Consent, Rights & Removal Compliance Policy.", style = MaterialTheme.typography.bodyMedium)
      }
      GradientButton(
        text = "Confirm & Unlock Catalog",
        enabled = ageChecked && consentChecked,
        onClick = onConfirm,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("confirm_compliance_button"),
      )
    }
  }
}

@Composable
fun ContentCard(
  item: ContentItem,
  categoryName: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .testTag("content_card_${item.id}"),
    colors = CardDefaults.cardColors(containerColor = CharcoalElevated),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(CharcoalHigh),
      ) {
        if (item.thumbnailPath.startsWith("https://")) {
          AsyncImage(
            model = item.thumbnailPath,
            contentDescription = stringResource(id = R.string.cd_content_thumbnail),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (item.isPremium) Icons.Default.Lock else Icons.Default.PlayArrow,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
          }
        }
        Row(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(10.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          StatusPill(
            text = if (item.isPremium) "PREMIUM" else "OPEN",
            color = if (item.isPremium) MaterialTheme.colorScheme.primary else SuccessEmerald,
          )
          if (!categoryName.isNullOrBlank()) {
            StatusPill(text = categoryName, color = MaterialTheme.colorScheme.secondary)
          }
        }
      }

      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (item.description.isNotBlank()) {
          Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
              Icons.Default.Visibility,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${item.viewCount} views",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = item.mediaType.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.18f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      color = color,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun EmptyStateCard(
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp),
      )
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun ConfirmDangerDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
