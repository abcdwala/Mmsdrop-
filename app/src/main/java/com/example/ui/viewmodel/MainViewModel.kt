package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppConfig
import com.example.core.SessionStore
import com.example.data.model.Announcement
import com.example.data.model.AppSetting
import com.example.data.model.BookmarkRow
import com.example.data.model.Category
import com.example.data.model.ContentItem
import com.example.data.model.PaymentRequest
import com.example.data.model.PlaybackResponse
import com.example.data.model.PremiumPlan
import com.example.data.model.Profile
import com.example.data.model.ReferralRecord
import com.example.data.model.Subscription
import com.example.data.model.SupportRequest
import com.example.data.model.ViewingHistoryRow
import com.example.data.repository.DropMmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class MainUiState(
  val isBackendConfigured: Boolean = AppConfig.isBackendConfigured,
  val missingConfigItems: List<String> = AppConfig.missingConfigurationItems,
  val isLoading: Boolean = false,
  val isAuthenticated: Boolean = false,
  val isOwner: Boolean = false,
  val profile: Profile? = null,
  val activeSubscription: Subscription? = null,
  val announcements: List<Announcement> = emptyList(),
  val categories: List<Category> = emptyList(),
  val featuredContent: List<ContentItem> = emptyList(),
  val trendingContent: List<ContentItem> = emptyList(),
  val latestContent: List<ContentItem> = emptyList(),
  val exploreContent: List<ContentItem> = emptyList(),
  val searchQuery: String = "",
  val selectedCategoryId: String? = null,
  val exploreSort: String = "latest",
  val explorePage: Int = 0,
  val exploreHasMore: Boolean = false,
  val bookmarks: List<BookmarkRow> = emptyList(),
  val viewingHistory: List<ViewingHistoryRow> = emptyList(),
  val plans: List<PremiumPlan> = emptyList(),
  val myPayments: List<PaymentRequest> = emptyList(),
  val myReferrals: List<ReferralRecord> = emptyList(),
  val mySupportRequests: List<SupportRequest> = emptyList(),
  val settings: Map<String, AppSetting> = emptyMap(),
  val activePlayback: Map<String, PlaybackResponse> = emptyMap(),
  val detailFallbackContent: Map<String, ContentItem> = emptyMap(),
  val pendingReferralCode: String = "",
  val statusMessage: String? = null,
  val errorMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val sessionStore = SessionStore(application.applicationContext)
  val repository = DropMmsRepository(application.applicationContext, sessionStore)

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  init {
    bootstrap()
  }

  fun bootstrap() {
    viewModelScope.launch {
      val stored = sessionStore.sessionFlow.first()
      _uiState.update {
        it.copy(
          isBackendConfigured = AppConfig.isBackendConfigured,
          missingConfigItems = AppConfig.missingConfigurationItems,
          pendingReferralCode = stored.pendingReferralCode,
        )
      }
      if (!AppConfig.isBackendConfigured) return@launch
      if (stored.accessToken.isNotBlank() && stored.telegramId != 0L) {
        _uiState.update { it.copy(isLoading = true) }
        repository.restoreProfile()
          .onSuccess { profile ->
            val ownerVerified = stored.isOwner && profile.telegramId == AppConfig.OWNER_TELEGRAM_ID
            _uiState.update {
              it.copy(
                isAuthenticated = true,
                isOwner = ownerVerified,
                profile = profile,
              )
            }
            refreshAllData()
          }
          .onFailure {
            sessionStore.clearSession()
            _uiState.update { it.copy(isLoading = false, isAuthenticated = false, profile = null) }
          }
      }
    }
  }

  fun handleDeepLink(uri: Uri?) {
    if (uri == null) return
    val ref = uri.getQueryParameter("startapp")
      ?: uri.getQueryParameter("ref")
      ?: uri.getQueryParameter("start")
    val initData = uri.getQueryParameter("initData")
    viewModelScope.launch {
      if (!ref.isNullOrBlank()) {
        sessionStore.savePendingReferral(ref)
        _uiState.update { it.copy(pendingReferralCode = ref) }
      }
      if (!initData.isNullOrBlank()) {
        authenticateTelegram(initData, ref.orEmpty())
      }
    }
  }

  fun authenticateTelegram(initData: String, startParam: String = "") {
    viewModelScope.launch {
      if (!AppConfig.isBackendConfigured) {
        _uiState.update {
          it.copy(errorMessage = "Configure SUPABASE_URL and SUPABASE_ANON_KEY in AI Studio Secrets first.")
        }
        return@launch
      }
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val effectiveStart = startParam.ifBlank { _uiState.value.pendingReferralCode }
      repository.authenticateWithTelegramInitData(initData, effectiveStart)
        .onSuccess { auth ->
          _uiState.update {
            it.copy(
              isLoading = false,
              isAuthenticated = true,
              isOwner = auth.isOwner && auth.profile.telegramId == AppConfig.OWNER_TELEGRAM_ID,
              profile = auth.profile,
              activeSubscription = auth.activeSubscription,
              statusMessage = "Authenticated via Telegram (${auth.profile.firstName.ifBlank { "#${auth.profile.telegramId}" }})",
            )
          }
          refreshAllData()
        }
        .onFailure { err ->
          _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
        }
    }
  }

  fun confirmAgeAndConsent() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      repository.confirmCompliance()
        .onSuccess { updatedProfile ->
          _uiState.update {
            it.copy(
              isLoading = false,
              profile = updatedProfile,
              statusMessage = "18+ age verification and consent terms recorded.",
            )
          }
          refreshAllData()
        }
        .onFailure { err ->
          _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
        }
    }
  }

  fun refreshAllData() {
    viewModelScope.launch {
      if (!AppConfig.isBackendConfigured || !_uiState.value.isAuthenticated) return@launch
      _uiState.update { it.copy(isLoading = true) }

      val categories = repository.fetchCategories().getOrDefault(emptyList())
      val announcements = repository.fetchAnnouncements().getOrDefault(emptyList())
      val featured = repository.fetchContent(featuredOnly = true, limit = 10).getOrDefault(emptyList())
      val trending = repository.fetchContent(sortBy = "popular", limit = 10).getOrDefault(emptyList())
      val latest = repository.fetchContent(sortBy = "latest", limit = 12).getOrDefault(emptyList())
      val plans = repository.fetchPlans().getOrDefault(emptyList())
      val settings = repository.fetchAppSettings().getOrDefault(emptyMap())
      val bookmarks = repository.fetchUserBookmarks().getOrDefault(emptyList())
      val history = repository.fetchUserHistory().getOrDefault(emptyList())
      val payments = repository.fetchUserPayments().getOrDefault(emptyList())
      val subs = repository.fetchUserSubscriptions().getOrDefault(emptyList())
      val referrals = repository.fetchUserReferrals().getOrDefault(emptyList())
      val support = repository.fetchUserSupport().getOrDefault(emptyList())

      val nowUtcIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }.format(Date())
      val activeSub = subs.firstOrNull { sub ->
        sub.status == "active" && (sub.expiresAt.isBlank() || sub.expiresAt.take(19) > nowUtcIso)
      }

      _uiState.update {
        it.copy(
          isLoading = false,
          categories = categories,
          announcements = announcements,
          featuredContent = featured,
          trendingContent = trending,
          latestContent = latest,
          plans = plans,
          settings = settings,
          bookmarks = bookmarks,
          viewingHistory = history,
          myPayments = payments,
          activeSubscription = activeSub,
          myReferrals = referrals,
          mySupportRequests = support,
        )
      }
      loadExplorePage()
    }
  }

  fun updateSearch(query: String) {
    _uiState.update { it.copy(searchQuery = query, explorePage = 0) }
    loadExplorePage()
  }

  fun selectCategory(categoryId: String?) {
    _uiState.update { it.copy(selectedCategoryId = categoryId, explorePage = 0) }
    loadExplorePage()
  }

  fun updateSort(sortBy: String) {
    _uiState.update { it.copy(exploreSort = sortBy, explorePage = 0) }
    loadExplorePage()
  }

  fun changeExplorePage(delta: Int) {
    val next = (_uiState.value.explorePage + delta).coerceAtLeast(0)
    _uiState.update { it.copy(explorePage = next) }
    loadExplorePage()
  }

  private fun loadExplorePage() {
    viewModelScope.launch {
      if (!AppConfig.isBackendConfigured || !_uiState.value.isAuthenticated) return@launch
      val st = _uiState.value
      val pageSize = 12
      repository.fetchContent(
        search = st.searchQuery,
        categoryId = st.selectedCategoryId,
        sortBy = st.exploreSort,
        limit = pageSize,
        offset = st.explorePage * pageSize,
      ).onSuccess { list ->
        _uiState.update {
          it.copy(
            exploreContent = list,
            exploreHasMore = list.size >= pageSize,
          )
        }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = err.message) }
      }
    }
  }

  fun findContentById(id: String): ContentItem? {
    val st = _uiState.value
    return (st.featuredContent + st.trendingContent + st.latestContent + st.exploreContent)
      .firstOrNull { it.id == id }
      ?: st.bookmarks.firstOrNull { it.contentId == id }?.content
      ?: st.viewingHistory.firstOrNull { it.contentId == id }?.content
      ?: st.detailFallbackContent[id]
  }

  fun ensureContentLoaded(contentId: String) {
    if (findContentById(contentId) != null) return
    viewModelScope.launch {
      if (!AppConfig.isBackendConfigured || !_uiState.value.isAuthenticated) return@launch
      repository.fetchContentById(contentId).onSuccess { loaded ->
        if (loaded != null) {
          _uiState.update { it.copy(detailFallbackContent = it.detailFallbackContent + (contentId to loaded)) }
        }
      }
    }
  }

  fun requestAuthorizedPlayback(contentId: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      repository.requestPlaybackUrl(contentId)
        .onSuccess { playback ->
          _uiState.update {
            it.copy(
              isLoading = false,
              activePlayback = it.activePlayback + (contentId to playback),
            )
          }
          refreshUserActivityOnly()
        }
        .onFailure { err ->
          _uiState.update { it.copy(isLoading = false, errorMessage = err.message) }
        }
    }
  }

  private suspend fun refreshUserActivityOnly() {
    val history = repository.fetchUserHistory().getOrDefault(_uiState.value.viewingHistory)
    val bookmarks = repository.fetchUserBookmarks().getOrDefault(_uiState.value.bookmarks)
    _uiState.update { it.copy(viewingHistory = history, bookmarks = bookmarks) }
  }

  fun toggleBookmark(contentId: String) {
    viewModelScope.launch {
      repository.toggleBookmark(contentId)
        .onSuccess { added ->
          refreshUserActivityOnly()
          _uiState.update {
            it.copy(statusMessage = if (added) "Saved to Bookmarks" else "Removed from Bookmarks")
          }
        }
        .onFailure { err -> _uiState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun submitContentReport(contentId: String, reason: String, details: String, removalRequested: Boolean) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      repository.submitReport(contentId, reason, details, removalRequested)
        .onSuccess {
          _uiState.update {
            it.copy(
              isLoading = false,
              statusMessage = "Report & compliance review submitted. Thank you.",
            )
          }
        }
        .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
    }
  }

  fun submitPayment(planId: String, method: String, reference: String, notes: String, proofUri: Uri?) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      repository.submitPayment(planId, method, reference, notes, proofUri)
        .onSuccess {
          val updatedPayments = repository.fetchUserPayments().getOrDefault(_uiState.value.myPayments)
          _uiState.update {
            it.copy(
              isLoading = false,
              myPayments = updatedPayments,
              statusMessage = "Payment request submitted with status: PENDING",
            )
          }
        }
        .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
    }
  }

  fun submitSupportTicket(subject: String, message: String, category: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      repository.submitSupportRequest(subject, message, category)
        .onSuccess {
          val updated = repository.fetchUserSupport().getOrDefault(_uiState.value.mySupportRequests)
          _uiState.update {
            it.copy(
              isLoading = false,
              mySupportRequests = updated,
              statusMessage = "Support ticket submitted.",
            )
          }
        }
        .onFailure { err -> _uiState.update { it.copy(isLoading = false, errorMessage = err.message) } }
    }
  }

  fun signOut() {
    viewModelScope.launch {
      repository.signOut()
      _uiState.update {
        MainUiState(
          isBackendConfigured = AppConfig.isBackendConfigured,
          missingConfigItems = AppConfig.missingConfigurationItems,
          statusMessage = "Signed out cleanly.",
        )
      }
    }
  }

  fun clearMessages() {
    _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
  }
}
