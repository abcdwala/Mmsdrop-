package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AppConfig
import com.example.ui.components.ContentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientButton
import com.example.ui.components.StatusPill
import com.example.ui.components.TelegramAuthCard
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.MainUiState

@Composable
fun ProfileScreen(
  state: MainUiState,
  onAuthenticateInitData: (String) -> Unit,
  onContentClick: (String) -> Unit,
  onSubmitSupport: (subject: String, message: String, category: String) -> Unit,
  onOpenSetup: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var supportSubject by remember { mutableStateOf("") }
  var supportMessage by remember { mutableStateOf("") }
  var supportCategory by remember { mutableStateOf("general") }
  val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Profile & Vault", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(
          onClick = onOpenSetup,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("profile_setup_button"),
        ) {
          Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Setup")
        }
      }
    }

    if (!state.isAuthenticated || state.profile == null) {
      item {
        TelegramAuthCard(
          isConfigured = state.isBackendConfigured,
          pendingReferral = state.pendingReferralCode,
          onAuthenticateInitData = onAuthenticateInitData,
          onOpenSetup = onOpenSetup,
        )
      }
      return@LazyColumn
    }

    val profile = state.profile
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!profile.photoUrl.isNullOrBlank() && profile.photoUrl.startsWith("https://")) {
              AsyncImage(
                model = profile.photoUrl,
                contentDescription = "Telegram profile avatar",
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape),
                contentScale = ContentScale.Crop,
              )
            } else {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(56.dp),
              ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(14.dp))
              }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "${profile.firstName} ${profile.lastName.orEmpty()}".trim().ifBlank { "Telegram User" },
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = buildString {
                  if (!profile.username.isNullOrBlank()) append("@${profile.username} • ")
                  append("TG ID: ${profile.telegramId}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(
                  text = if (state.activeSubscription != null) "PREMIUM ACTIVE" else "STANDARD",
                  color = if (state.activeSubscription != null) SuccessEmerald else MaterialTheme.colorScheme.secondary,
                )
                if (state.isOwner) {
                  StatusPill(text = "OWNER", color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }

          OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("sign_out_button"),
          ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Sign Out")
          }
        }
      }
    }

    // Referral card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Referral Program", style = MaterialTheme.typography.titleLarge)
          Text(
            "Your Referral Code: ${profile.referralCode} • Referrals recorded: ${state.myReferrals.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          val botUrl = if (AppConfig.isBotUsernameValid) {
            "https://t.me/${AppConfig.telegramBotUsername}?startapp=ref_${profile.referralCode}"
          } else {
            "dropmms://auth?startapp=ref_${profile.referralCode}"
          }
          OutlinedButton(
            onClick = {
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Join DropMms Pro: $botUrl")
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Referral Link"))
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("share_referral_button"),
          ) {
            Icon(Icons.Default.Share, contentDescription = stringResource(id = R.string.cd_share_referral))
            Spacer(Modifier.width(8.dp))
            Text("Share Referral Link")
          }
        }
      }
    }

    // Saved Bookmarks
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Saved Items (${state.bookmarks.size})", style = MaterialTheme.typography.titleLarge)
      }
    }
    if (state.bookmarks.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Bookmarks Yet",
          description = "Bookmark items from Content Details to save them to your PostgreSQL profile.",
        )
      }
    } else {
      items(state.bookmarks, key = { "bm_${it.id}" }) { bm ->
        bm.content?.let { item ->
          ContentCard(
            item = item,
            categoryName = item.categoryId?.let { categoryMap[it]?.name },
            onClick = { onContentClick(item.id) },
          )
        }
      }
    }

    // Viewing History
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Text("Viewing History (${state.viewingHistory.size})", style = MaterialTheme.typography.titleLarge)
      }
    }
    if (state.viewingHistory.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Viewing History",
          description = "Authorized media playback automatically records your progress here.",
        )
      }
    } else {
      items(state.viewingHistory.take(10), key = { "hist_${it.id}" }) { hist ->
        hist.content?.let { item ->
          ContentCard(
            item = item,
            categoryName = item.categoryId?.let { categoryMap[it]?.name },
            onClick = { onContentClick(item.id) },
          )
        }
      }
    }

    // Support
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Support & Removal Requests", style = MaterialTheme.typography.titleLarge)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("general" to "General", "billing" to "Billing", "rights_removal" to "Removal").forEach { (key, label) ->
              FilterChip(
                selected = supportCategory == key,
                onClick = { supportCategory = key },
                label = { Text(label) },
              )
            }
          }
          OutlinedTextField(
            value = supportSubject,
            onValueChange = { supportSubject = it },
            label = { Text("Subject") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("support_subject_input"),
          )
          OutlinedTextField(
            value = supportMessage,
            onValueChange = { supportMessage = it },
            label = { Text("Message") },
            minLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("support_message_input"),
          )
          GradientButton(
            text = "Submit Support Ticket",
            enabled = supportSubject.trim().length >= 3 && supportMessage.trim().length >= 5,
            onClick = {
              onSubmitSupport(supportSubject.trim(), supportMessage.trim(), supportCategory)
              supportSubject = ""
              supportMessage = ""
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("submit_support_button"),
          )
        }
      }
    }

    items(state.mySupportRequests, key = { "sup_${it.id}" }) { ticket ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(ticket.subject, style = MaterialTheme.typography.titleMedium)
            StatusPill(text = ticket.status.uppercase(), color = MaterialTheme.colorScheme.secondary)
          }
          Text(ticket.message, style = MaterialTheme.typography.bodyMedium)
          if (!ticket.adminReply.isNullOrBlank()) {
            Text("Owner Reply: ${ticket.adminReply}", style = MaterialTheme.typography.bodyMedium, color = SuccessEmerald)
          }
        }
      }
    }
  }
}
