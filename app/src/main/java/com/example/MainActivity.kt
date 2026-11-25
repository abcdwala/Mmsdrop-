package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.AppConfig
import com.example.ui.navigation.AdminRoute
import com.example.ui.navigation.ContentDetailRoute
import com.example.ui.navigation.ExploreRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PremiumRoute
import com.example.ui.navigation.ProfileRoute
import com.example.ui.navigation.SetupDiagnosticsRoute
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.ContentDetailScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PremiumScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SetupDiagnosticsScreen
import com.example.ui.theme.DropMmsTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  private val mainViewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    mainViewModel.handleDeepLink(intent?.data)
    setContent {
      DropMmsTheme {
        DropMmsApp(mainViewModel = mainViewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    mainViewModel.handleDeepLink(intent.data)
  }
}

private data class NavTabItem(
  val labelRes: Int,
  val route: Any,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

@Composable
fun DropMmsApp(mainViewModel: MainViewModel) {
  val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
  val adminViewModel = remember(mainViewModel) { AdminViewModel(mainViewModel.repository) }
  val adminState by adminViewModel.state.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.statusMessage, uiState.errorMessage, adminState.statusMessage, adminState.errorMessage) {
    val msg = uiState.errorMessage ?: uiState.statusMessage ?: adminState.errorMessage ?: adminState.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      mainViewModel.clearMessages()
      adminViewModel.clearMessages()
    }
  }

  val showOwnerTab = uiState.isAuthenticated &&
    uiState.isOwner &&
    uiState.profile?.telegramId == AppConfig.OWNER_TELEGRAM_ID

  val tabs = remember(showOwnerTab) {
    buildList {
      add(NavTabItem(R.string.nav_home, HomeRoute, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"))
      add(NavTabItem(R.string.nav_explore, ExploreRoute, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"))
      add(NavTabItem(R.string.nav_premium, PremiumRoute, Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium, "nav_premium"))
      add(NavTabItem(R.string.nav_profile, ProfileRoute, Icons.Filled.Person, Icons.Outlined.Person, "nav_profile"))
      if (showOwnerTab) {
        add(NavTabItem(R.string.nav_admin, AdminRoute, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_admin"))
      }
    }
  }

  val isTopLevelRoute = currentDest?.let { dest ->
    dest.hasRoute<HomeRoute>() ||
      dest.hasRoute<ExploreRoute>() ||
      dest.hasRoute<PremiumRoute>() ||
      dest.hasRoute<ProfileRoute>() ||
      dest.hasRoute<AdminRoute>()
  } ?: true

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 680.dp

    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useRail && isTopLevelRoute) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(HomeRoute) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail && isTopLevelRoute) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { tab ->
              val selected = currentDest?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(tab.route) {
                    popUpTo(HomeRoute) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = HomeRoute,
          modifier = Modifier
            .weight(1f)
            .widthIn(max = 860.dp)
            .align(Alignment.Top),
        ) {
          composable<HomeRoute> {
            HomeScreen(
              state = uiState,
              onAuthenticateInitData = { mainViewModel.authenticateTelegram(it) },
              onConfirmCompliance = { mainViewModel.confirmAgeAndConsent() },
              onSearchSubmit = { query ->
                mainViewModel.updateSearch(query)
                navController.navigate(ExploreRoute) { launchSingleTop = true }
              },
              onCategorySelect = { catId ->
                mainViewModel.selectCategory(catId)
                navController.navigate(ExploreRoute) { launchSingleTop = true }
              },
              onContentClick = { id -> navController.navigate(ContentDetailRoute(id)) },
              onOpenSetup = { navController.navigate(SetupDiagnosticsRoute) },
            )
          }

          composable<ExploreRoute> {
            ExploreScreen(
              state = uiState,
              onSearch = { mainViewModel.updateSearch(it) },
              onSelectCategory = { mainViewModel.selectCategory(it) },
              onSortChange = { mainViewModel.updateSort(it) },
              onPageChange = { mainViewModel.changeExplorePage(it) },
              onContentClick = { id -> navController.navigate(ContentDetailRoute(id)) },
              onOpenSetup = { navController.navigate(SetupDiagnosticsRoute) },
            )
          }

          composable<ContentDetailRoute> { entry ->
            val args = entry.toRoute<ContentDetailRoute>()
            ContentDetailScreen(
              contentId = args.contentId,
              state = uiState,
              onBack = { navController.popBackStack() },
              onEnsureLoaded = { mainViewModel.ensureContentLoaded(it) },
              onRequestPlayback = { mainViewModel.requestAuthorizedPlayback(it) },
              onToggleBookmark = { mainViewModel.toggleBookmark(it) },
              onSubmitReport = { id, reason, details, removal ->
                mainViewModel.submitContentReport(id, reason, details, removal)
              },
              onRelatedClick = { id -> navController.navigate(ContentDetailRoute(id)) },
            )
          }

          composable<PremiumRoute> {
            PremiumScreen(
              state = uiState,
              onSubmitPayment = { planId, method, ref, notes, uri ->
                mainViewModel.submitPayment(planId, method, ref, notes, uri)
              },
              onOpenSetup = { navController.navigate(SetupDiagnosticsRoute) },
            )
          }

          composable<ProfileRoute> {
            ProfileScreen(
              state = uiState,
              onAuthenticateInitData = { mainViewModel.authenticateTelegram(it) },
              onContentClick = { id -> navController.navigate(ContentDetailRoute(id)) },
              onSubmitSupport = { subj, msg, cat -> mainViewModel.submitSupportTicket(subj, msg, cat) },
              onOpenSetup = { navController.navigate(SetupDiagnosticsRoute) },
              onSignOut = { mainViewModel.signOut() },
            )
          }

          composable<AdminRoute> {
            AdminScreen(
              mainState = uiState,
              adminViewModel = adminViewModel,
            )
          }

          composable<SetupDiagnosticsRoute> {
            SetupDiagnosticsScreen(
              state = uiState,
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}
