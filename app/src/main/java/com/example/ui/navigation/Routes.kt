package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object HomeRoute

@Serializable object ExploreRoute

@Serializable object PremiumRoute

@Serializable object ProfileRoute

@Serializable object AdminRoute

@Serializable data class ContentDetailRoute(val contentId: String)

@Serializable object SetupDiagnosticsRoute
