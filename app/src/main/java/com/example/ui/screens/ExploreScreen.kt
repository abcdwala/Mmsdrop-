package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.ConfigRequiredBanner
import com.example.ui.components.ContentCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.viewmodel.MainUiState

@Composable
fun ExploreScreen(
  state: MainUiState,
  onSearch: (String) -> Unit,
  onSelectCategory: (String?) -> Unit,
  onSortChange: (String) -> Unit,
  onPageChange: (Int) -> Unit,
  onContentClick: (String) -> Unit,
  onOpenSetup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var queryText by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
  val categoryMap = remember(state.categories) { state.categories.associateBy { it.id } }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text("Explore Catalog", style = MaterialTheme.typography.headlineMedium)
    }

    if (!state.isBackendConfigured) {
      item {
        ConfigRequiredBanner(missingItems = state.missingConfigItems, onOpenDiagnostics = onOpenSetup)
      }
    }

    item {
      OutlinedTextField(
        value = queryText,
        onValueChange = { queryText = it },
        label = { Text("Search database by title or description") },
        singleLine = true,
        trailingIcon = {
          IconButton(
            onClick = { onSearch(queryText) },
            modifier = Modifier.testTag("explore_search_button"),
          ) {
            Icon(Icons.Default.Search, contentDescription = stringResource(id = R.string.cd_search))
          }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(queryText) }),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("explore_search_input"),
      )
    }

    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = state.exploreSort == "latest",
          onClick = { onSortChange("latest") },
          label = { Text("Latest") },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("sort_latest_chip"),
        )
        FilterChip(
          selected = state.exploreSort == "popular",
          onClick = { onSortChange("popular") },
          label = { Text("Popular") },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("sort_popular_chip"),
        )
      }
    }

    if (state.categories.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = state.selectedCategoryId == null,
              onClick = { onSelectCategory(null) },
              label = { Text("All") },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("explore_cat_all"),
            )
          }
          items(state.categories, key = { it.id }) { cat ->
            FilterChip(
              selected = state.selectedCategoryId == cat.id,
              onClick = { onSelectCategory(cat.id) },
              label = { Text(cat.name) },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("explore_cat_${cat.slug}"),
            )
          }
        }
      }
    }

    if (!state.isAuthenticated) {
      item {
        EmptyStateCard(
          title = "Authentication Required",
          description = "Sign in via Telegram on the Home tab and complete the 18+ compliance gate to query live catalog records.",
        )
      }
    } else if (state.exploreContent.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Matching Results",
          description = "No published database records matched your current filter or search query.",
        )
      }
    } else {
      items(state.exploreContent, key = { it.id }) { item ->
        ContentCard(
          item = item,
          categoryName = item.categoryId?.let { categoryMap[it]?.name },
          onClick = { onContentClick(item.id) },
        )
      }

      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { onPageChange(-1) },
            enabled = state.explorePage > 0,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("explore_prev_page"),
          ) {
            Text("Previous")
          }
          Text(
            text = "Page ${state.explorePage + 1}",
            style = MaterialTheme.typography.labelLarge,
          )
          OutlinedButton(
            onClick = { onPageChange(1) },
            enabled = state.exploreHasMore,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("explore_next_page"),
          ) {
            Text("Next")
          }
        }
      }
    }
  }
}
