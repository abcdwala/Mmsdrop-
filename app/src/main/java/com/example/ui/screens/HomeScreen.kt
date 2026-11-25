package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ComplianceGateCard
import com.example.ui.components.ConfigRequiredBanner
import com.example.ui.components.ContentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TelegramAuthCard
import com.example.ui.theme.HeroOverlayGradient
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainUiState

@Composable
fun HomeScreen(
  state: MainUiState,
  onAuthenticateInitData: (String) -> Unit,
  onConfirmCompliance: () -> Unit,
  onSearchSubmit: (String) -> Unit,
  onCategorySelect: (String) -> Unit,
  onContentClick: (String) -> Unit,
  onOpenSetup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var localSearch by remember { mutableStateOf(state.searchQuery) }
  val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(176.dp)
          .clip(RoundedCornerShape(22.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_backdrop),
          contentDescription = stringResource(id = R.string.cd_hero_banner),
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(HeroOverlayGradient)
            .padding(18.dp),
        ) {
          Column(modifier = Modifier.align(Alignment.BottomStart)) {
            StatusPill(
              text = if (state.activeSubscription != null) "PRO MEMBER" else "DROPMMS PRO",
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = "DropMms Pro",
              style = MaterialTheme.typography.displaySmall,
            )
            Text(
              text = "Authorized media vault • Real-time Supabase & Telegram Mini App sync",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (!state.isBackendConfigured) {
      item {
        ConfigRequiredBanner(
          missingItems = state.missingConfigItems,
          onOpenDiagnostics = onOpenSetup,
        )
      }
    }

    if (!state.isAuthenticated) {
      item {
        TelegramAuthCard(
          isConfigured = state.isBackendConfigured,
          pendingReferral = state.pendingReferralCode,
          onAuthenticateInitData = onAuthenticateInitData,
          onOpenSetup = onOpenSetup,
        )
      }
    } else if (state.profile?.ageVerifiedAt == null || state.profile.consentAcceptedAt == null) {
      item {
        ComplianceGateCard(onConfirm = onConfirmCompliance)
      }
    } else {
      // Announcements
      if (state.announcements.isNotEmpty()) {
        items(state.announcements, key = { "ann_${it.id}" }) { ann ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
              1.dp,
              if (ann.severity == "warning") WarningAmber.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            ),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ann.title, style = MaterialTheme.typography.titleMedium)
                Text(ann.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }

      // Real search
      item {
        OutlinedTextField(
          value = localSearch,
          onValueChange = { localSearch = it },
          label = { Text("Search verified catalog") },
          singleLine = true,
          trailingIcon = {
            IconButton(
              onClick = { onSearchSubmit(localSearch) },
              modifier = Modifier.testTag("home_search_button"),
            ) {
              Icon(Icons.Default.Search, contentDescription = stringResource(id = R.string.cd_search))
            }
          },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(localSearch) }),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("home_search_input"),
        )
      }

      // Categories
      if (state.categories.isNotEmpty()) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(state.categories, key = { it.id }) { cat ->
                FilterChip(
                  selected = state.selectedCategoryId == cat.id,
                  onClick = { onCategorySelect(cat.id) },
                  label = { Text(cat.name) },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("home_category_chip_${cat.slug}"),
                )
              }
            }
          }
        }
      }

      // Featured
      if (state.featuredContent.isNotEmpty()) {
        item {
          Text("Featured", style = MaterialTheme.typography.titleLarge)
        }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.featuredContent, key = { "feat_${it.id}" }) { item ->
              ContentCard(
                item = item,
                categoryName = item.categoryId?.let { categoryMap[it]?.name },
                onClick = { onContentClick(item.id) },
                modifier = Modifier.width(272.dp),
              )
            }
          }
        }
      }

      // Trending
      if (state.trendingContent.isNotEmpty()) {
        item {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text("Trending Now", style = MaterialTheme.typography.titleLarge)
          }
        }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.trendingContent, key = { "trend_${it.id}" }) { item ->
              ContentCard(
                item = item,
                categoryName = item.categoryId?.let { categoryMap[it]?.name },
                onClick = { onContentClick(item.id) },
                modifier = Modifier.width(250.dp),
              )
            }
          }
        }
      }

      // Latest Uploads
      item {
        Text("Latest Uploads", style = MaterialTheme.typography.titleLarge)
      }
      if (state.latestContent.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Published Content Yet",
            description = "Your connected PostgreSQL database currently has 0 published items. The Owner can publish verified uploads from the Owner Admin tab.",
          )
        }
      } else {
        items(state.latestContent, key = { "latest_${it.id}" }) { item ->
          ContentCard(
            item = item,
            categoryName = item.categoryId?.let { categoryMap[it]?.name },
            onClick = { onContentClick(item.id) },
          )
        }
      }
    }
  }
}
