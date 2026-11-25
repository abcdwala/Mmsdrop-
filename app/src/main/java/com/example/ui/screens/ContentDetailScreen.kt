package com.example.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.ContentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.MainUiState

@Composable
fun ContentDetailScreen(
  contentId: String,
  state: MainUiState,
  onBack: () -> Unit,
  onEnsureLoaded: (String) -> Unit,
  onRequestPlayback: (String) -> Unit,
  onToggleBookmark: (String) -> Unit,
  onSubmitReport: (String, String, String, Boolean) -> Unit,
  onRelatedClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val item = state.latestContent
    .plus(state.featuredContent)
    .plus(state.trendingContent)
    .plus(state.exploreContent)
    .firstOrNull { it.id == contentId }
    ?: state.bookmarks.firstOrNull { it.contentId == contentId }?.content
    ?: state.viewingHistory.firstOrNull { it.contentId == contentId }?.content
    ?: state.detailFallbackContent[contentId]

  LaunchedEffect(contentId, item == null, state.isAuthenticated) {
    if (item == null && state.isAuthenticated) {
      onEnsureLoaded(contentId)
    }
  }

  val isBookmarked = state.bookmarks.any { it.contentId == contentId }
  val playback = state.activePlayback[contentId]
  val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }
  val related = remember(item, state.latestContent) {
    if (item == null) emptyList()
    else state.latestContent.filter { it.id != item.id && it.categoryId == item.categoryId }.take(4)
  }

  var showReportDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("detail_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_back))
        }
        Text("Content Details", style = MaterialTheme.typography.titleLarge)
      }
    }

    if (item == null) {
      item {
        EmptyStateCard(
          title = "Item Not Loaded",
          description = "This content record could not be loaded from your active session.",
        )
      }
      return@LazyColumn
    }

    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .clip(RoundedCornerShape(18.dp))
          .background(CharcoalElevated),
      ) {
        if (playback != null && playback.playbackUrl.startsWith("https://")) {
          if (playback.mediaType == "image") {
            AsyncImage(
              model = playback.playbackUrl,
              contentDescription = item.title,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Fit,
            )
          } else {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  val controller = MediaController(ctx)
                  controller.setAnchorView(this)
                  setMediaController(controller)
                  setVideoURI(Uri.parse(playback.playbackUrl))
                  setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                  }
                }
              },
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else if (item.thumbnailPath.startsWith("https://")) {
          AsyncImage(
            model = item.thumbnailPath,
            contentDescription = stringResource(id = R.string.cd_content_thumbnail),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
          )
        } else {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          StatusPill(
            text = if (item.isPremium) "PREMIUM" else "OPEN ACCESS",
            color = if (item.isPremium) MaterialTheme.colorScheme.primary else SuccessEmerald,
          )
          item.categoryId?.let { cid ->
            categoryMap[cid]?.let { cat ->
              StatusPill(text = cat.name, color = MaterialTheme.colorScheme.secondary)
            }
          }
          if (item.rightsVerified && item.consentDocumented) {
            StatusPill(text = "RIGHTS VERIFIED", color = SuccessEmerald)
          }
        }
        Text(item.title, style = MaterialTheme.typography.headlineMedium)
        Text(
          item.description.ifBlank { "No description provided." },
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      GradientButton(
        text = if (playback != null) "Playback Authorized (Signed 5m Stream)" else "Authorize & Play Media",
        onClick = { onRequestPlayback(item.id) },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("authorize_play_button"),
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = { onToggleBookmark(item.id) },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("bookmark_button"),
        ) {
          Icon(
            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = stringResource(id = R.string.cd_bookmark),
          )
          Spacer(Modifier.width(6.dp))
          Text(if (isBookmarked) "Saved" else "Bookmark")
        }
        OutlinedButton(
          onClick = { showReportDialog = true },
          border = BorderStroke(1.dp, DangerCrimson.copy(alpha = 0.5f)),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("report_content_button"),
        ) {
          Icon(Icons.Default.Flag, contentDescription = stringResource(id = R.string.cd_report), tint = DangerCrimson)
          Spacer(Modifier.width(6.dp))
          Text("Report / Removal", color = DangerCrimson)
        }
      }
    }

    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessEmerald)
          Text(
            "Playback URLs are minted per-request via Supabase Edge Functions only after server-side age verification, block status, and membership entitlement checks.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    if (related.isNotEmpty()) {
      item {
        Text("Related Content", style = MaterialTheme.typography.titleLarge)
      }
      items(related, key = { "rel_${it.id}" }) { rel ->
        ContentCard(
          item = rel,
          categoryName = rel.categoryId?.let { categoryMap[it]?.name },
          onClick = { onRelatedClick(rel.id) },
        )
      }
    }
  }

  if (showReportDialog) {
    ReportContentDialog(
      onDismiss = { showReportDialog = false },
      onSubmit = { reason, details, removal ->
        onSubmitReport(contentId, reason, details, removal)
        showReportDialog = false
      },
    )
  }
}

@Composable
private fun ReportContentDialog(
  onDismiss: () -> Unit,
  onSubmit: (String, String, Boolean) -> Unit,
) {
  val reasons = listOf(
    "copyright_dmca" to "Copyright / DMCA",
    "non_consensual" to "Non-consensual",
    "underage_safety" to "Safety Violation",
    "spam_abuse" to "Spam / Abuse",
    "other" to "Other",
  )
  var selectedReason by remember { mutableStateOf("copyright_dmca") }
  var details by remember { mutableStateOf("") }
  var removalRequested by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Report Content / Removal Request") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          reasons.forEach { (key, label) ->
            FilterChip(
              selected = selectedReason == key,
              onClick = { selectedReason = key },
              label = { Text(label) },
            )
          }
        }
        OutlinedTextField(
          value = details,
          onValueChange = { details = it },
          label = { Text("Details & rights/evidence description") },
          minLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = removalRequested, onCheckedChange = { removalRequested = it })
          Text("Formal removal request", style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(selectedReason, details, removalRequested) },
        enabled = details.trim().length >= 5,
        colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
      ) {
        Text("Submit Report", color = Color.White)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
