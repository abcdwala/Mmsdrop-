package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityLog
import com.example.data.model.Announcement
import com.example.data.model.Category
import com.example.data.model.ContentItem
import com.example.data.model.ContentReport
import com.example.data.model.DashboardStats
import com.example.data.model.PaymentRequest
import com.example.data.model.PremiumPlan
import com.example.data.model.Profile
import com.example.data.model.Subscription
import com.example.data.model.SupportRequest
import com.example.data.repository.DropMmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
  val isLoading: Boolean = false,
  val stats: DashboardStats? = null,
  val users: List<Profile> = emptyList(),
  val content: List<ContentItem> = emptyList(),
  val categories: List<Category> = emptyList(),
  val plans: List<PremiumPlan> = emptyList(),
  val payments: List<PaymentRequest> = emptyList(),
  val subscriptions: List<Subscription> = emptyList(),
  val announcements: List<Announcement> = emptyList(),
  val reports: List<ContentReport> = emptyList(),
  val supportTickets: List<SupportRequest> = emptyList(),
  val logs: List<ActivityLog> = emptyList(),
  val statusMessage: String? = null,
  val errorMessage: String? = null,
)

class AdminViewModel(private val repository: DropMmsRepository) : ViewModel() {
  private val _state = MutableStateFlow(AdminUiState())
  val state: StateFlow<AdminUiState> = _state.asStateFlow()

  fun loadDashboard() {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, errorMessage = null) }
      val statsRes = repository.fetchAdminStats()
      if (statsRes.isFailure) {
        _state.update {
          it.copy(isLoading = false, errorMessage = statsRes.exceptionOrNull()?.message)
        }
        return@launch
      }
      val users = repository.fetchAdminUsers().getOrDefault(emptyList())
      val content = repository.fetchContent(includeUnpublished = true, limit = 100).getOrDefault(emptyList())
      val categories = repository.fetchCategories(includeInactive = true).getOrDefault(emptyList())
      val plans = repository.fetchPlans(includeInactive = true).getOrDefault(emptyList())
      val payments = repository.fetchAdminPayments().getOrDefault(emptyList())
      val subs = repository.fetchAdminSubscriptions().getOrDefault(emptyList())
      val announcements = repository.fetchAnnouncements(includeInactive = true).getOrDefault(emptyList())
      val reports = repository.fetchAdminReports().getOrDefault(emptyList())
      val support = repository.fetchAdminSupport().getOrDefault(emptyList())
      val logs = repository.fetchAdminLogs().getOrDefault(emptyList())

      _state.update {
        it.copy(
          isLoading = false,
          stats = statsRes.getOrNull(),
          users = users,
          content = content,
          categories = categories,
          plans = plans,
          payments = payments,
          subscriptions = subs,
          announcements = announcements,
          reports = reports,
          supportTickets = support,
          logs = logs,
        )
      }
    }
  }

  fun toggleUserBlocked(telegramId: Long, block: Boolean, reason: String) {
    runAdminTask("User updated") { repository.setUserBlocked(telegramId, block, reason) }
  }

  fun reviewPayment(paymentId: String, decision: String, notes: String) {
    runAdminTask("Payment $decision") { repository.reviewPayment(paymentId, decision, notes) }
  }

  fun upsertContent(
    id: String?,
    title: String,
    description: String,
    categoryId: String?,
    mediaType: String,
    thumbnailUrl: String,
    thumbnailUri: Uri?,
    mediaPath: String,
    isFeatured: Boolean,
    isPremium: Boolean,
    isPublished: Boolean,
    rightsVerified: Boolean,
    consentDocumented: Boolean,
  ) {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, errorMessage = null) }
      var resolvedThumb = thumbnailUrl
      if (thumbnailUri != null) {
        val uploadRes = repository.adminUploadThumbnail(thumbnailUri)
        if (uploadRes.isFailure) {
          _state.update { it.copy(isLoading = false, errorMessage = uploadRes.exceptionOrNull()?.message) }
          return@launch
        }
        resolvedThumb = uploadRes.getOrThrow()
      }
      val contentObj = buildMap<String, Any> {
        if (!id.isNullOrBlank()) put("id", id)
        put("title", title)
        put("description", description)
        if (!categoryId.isNullOrBlank()) put("category_id", categoryId)
        put("media_type", mediaType)
        put("thumbnail_path", resolvedThumb)
        put("media_path", mediaPath)
        put("is_featured", isFeatured)
        put("is_premium", isPremium)
        put("is_published", isPublished)
        put("rights_verified", rightsVerified)
        put("consent_documented", consentDocumented)
      }
      repository.adminInvoke("upsert_content", mapOf("content" to contentObj))
        .onSuccess {
          _state.update { it.copy(statusMessage = "Content saved") }
          loadDashboard()
        }
        .onFailure { err -> _state.update { it.copy(isLoading = false, errorMessage = err.message) } }
    }
  }

  fun deleteContent(id: String) {
    runAdminTask("Content deleted") { repository.adminInvoke("delete_content", mapOf("id" to id)) }
  }

  fun upsertCategory(id: String?, name: String, description: String) {
    val obj = buildMap<String, Any> {
      if (!id.isNullOrBlank()) put("id", id)
      put("name", name)
      put("description", description)
      put("is_active", true)
    }
    runAdminTask("Category saved") { repository.adminInvoke("upsert_category", mapOf("category" to obj)) }
  }

  fun deleteCategory(id: String) {
    runAdminTask("Category deleted") { repository.adminInvoke("delete_category", mapOf("id" to id)) }
  }

  fun upsertPlan(id: String?, name: String, price: Double, currency: String, days: Int, description: String) {
    val obj = buildMap<String, Any> {
      if (!id.isNullOrBlank()) put("id", id)
      put("name", name)
      put("price_amount", price)
      put("currency", currency)
      put("duration_days", days)
      put("description", description)
      put("is_active", true)
    }
    runAdminTask("Plan saved") { repository.adminInvoke("upsert_plan", mapOf("plan" to obj)) }
  }

  fun deletePlan(id: String) {
    runAdminTask("Plan deleted") { repository.adminInvoke("delete_plan", mapOf("id" to id)) }
  }

  fun grantSubscription(userTelegramId: Long, days: Int) {
    runAdminTask("Premium granted") {
      repository.adminInvoke("grant_subscription", mapOf("user_telegram_id" to userTelegramId, "duration_days" to days))
    }
  }

  fun revokeSubscription(subscriptionId: String) {
    runAdminTask("Subscription revoked") {
      repository.adminInvoke("revoke_subscription", mapOf("subscription_id" to subscriptionId))
    }
  }

  fun upsertAnnouncement(title: String, body: String, severity: String) {
    runAdminTask("Announcement published") {
      repository.adminInvoke(
        "upsert_announcement",
        mapOf("announcement" to mapOf("title" to title, "body" to body, "severity" to severity, "is_active" to true)),
      )
    }
  }

  fun deleteAnnouncement(id: String) {
    runAdminTask("Announcement removed") { repository.adminInvoke("delete_announcement", mapOf("id" to id)) }
  }

  fun updateSetting(key: String, value: Map<String, Any>) {
    runAdminTask("Settings saved") {
      repository.adminInvoke("update_setting", mapOf("key" to key, "value" to value))
    }
  }

  fun resolveReport(reportId: String, status: String, notes: String) {
    runAdminTask("Report updated") {
      repository.adminInvoke(
        "resolve_report",
        mapOf("report_id" to reportId, "status" to status, "resolution_notes" to notes),
      )
    }
  }

  fun replySupport(supportId: String, status: String, reply: String) {
    runAdminTask("Support ticket updated") {
      repository.adminInvoke(
        "reply_support",
        mapOf("support_id" to supportId, "status" to status, "admin_reply" to reply),
      )
    }
  }

  private fun runAdminTask(successMsg: String, block: suspend () -> Result<Unit>) {
    viewModelScope.launch {
      _state.update { it.copy(isLoading = true, errorMessage = null) }
      block()
        .onSuccess {
          _state.update { it.copy(statusMessage = successMsg) }
          loadDashboard()
        }
        .onFailure { err ->
          _state.update { it.copy(isLoading = false, errorMessage = err.message) }
        }
    }
  }

  fun clearMessages() {
    _state.update { it.copy(statusMessage = null, errorMessage = null) }
  }
}
