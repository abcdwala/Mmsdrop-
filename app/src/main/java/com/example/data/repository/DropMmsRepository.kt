package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.AppConfig
import com.example.core.SessionStore
import com.example.data.model.ActivityLog
import com.example.data.model.Announcement
import com.example.data.model.AppSetting
import com.example.data.model.AuthResponse
import com.example.data.model.BookmarkRow
import com.example.data.model.Category
import com.example.data.model.ContentItem
import com.example.data.model.ContentReport
import com.example.data.model.DashboardStats
import com.example.data.model.PaymentRequest
import com.example.data.model.PlaybackResponse
import com.example.data.model.PremiumPlan
import com.example.data.model.Profile
import com.example.data.model.ReferralRecord
import com.example.data.model.Subscription
import com.example.data.model.SupportRequest
import com.example.data.model.ViewingHistoryRow
import com.example.data.remote.SupabaseFunctionsApi
import com.example.data.remote.SupabaseRestApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class DropMmsRepository(
  private val context: Context,
  val sessionStore: SessionStore,
) {
  private val okHttp = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val restApi: SupabaseRestApi? by lazy { buildRetrofit()?.create(SupabaseRestApi::class.java) }
  private val fnApi: SupabaseFunctionsApi? by lazy { buildRetrofit()?.create(SupabaseFunctionsApi::class.java) }

  private fun buildRetrofit(): Retrofit? {
    if (!AppConfig.isBackendConfigured) return null
    return Retrofit.Builder()
      .baseUrl("${AppConfig.supabaseUrl}/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }

  private fun parseError(t: Throwable): String {
    if (t is HttpException) {
      val raw = t.response()?.errorBody()?.string().orEmpty()
      if (raw.isNotBlank()) {
        runCatching {
          val obj = JSONObject(raw)
          return obj.optString("error").ifBlank { obj.optString("message").ifBlank { raw } }
        }
      }
      return "Server error (${t.code()})"
    }
    return t.message ?: "Unexpected network error"
  }

  private suspend fun requireSession(): Pair<String, Long> {
    val s = sessionStore.sessionFlow.first()
    if (s.accessToken.isBlank() || s.telegramId == 0L) {
      throw IllegalStateException("Telegram authentication required")
    }
    return s.accessToken to s.telegramId
  }

  private fun anonBearer() = "Bearer ${AppConfig.supabaseAnonKey}"

  suspend fun authenticateWithTelegramInitData(initData: String, startParam: String = ""): Result<AuthResponse> =
    runCatching {
      val api = fnApi ?: throw IllegalStateException("Supabase URL & Anon Key not configured.")
      val payload = buildMap<String, Any> {
        put("initData", initData.trim())
        if (startParam.isNotBlank()) put("startParam", startParam.trim())
      }
      val auth = api.authenticateTelegram(AppConfig.supabaseAnonKey, anonBearer(), payload)
      sessionStore.saveSession(auth.accessToken, auth.profile.telegramId, auth.isOwner)
      auth
    }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun signOut() {
    sessionStore.clearSession()
  }

  suspend fun restoreProfile(): Result<Profile> = runCatching {
    val api = restApi ?: throw IllegalStateException("Backend not configured")
    val (token, tid) = requireSession()
    api.getProfile(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid").firstOrNull()
      ?: throw IllegalStateException("Profile not found; please re-authenticate.")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun confirmCompliance(): Result<Profile> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.confirmCompliance(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf("action" to "confirm_compliance", "age_confirmed" to true, "consent_confirmed" to true),
    ).profile
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchCategories(includeInactive: Boolean = false): Result<List<Category>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, _) = requireSession()
    api.getCategories(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      activeFilter = if (includeInactive) null else "eq.true",
    )
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAnnouncements(includeInactive: Boolean = false): Result<List<Announcement>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, _) = requireSession()
    api.getAnnouncements(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      activeFilter = if (includeInactive) null else "eq.true",
    )
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchContent(
    search: String = "",
    categoryId: String? = null,
    sortBy: String = "latest",
    featuredOnly: Boolean? = null,
    includeUnpublished: Boolean = false,
    limit: Int = 20,
    offset: Int = 0,
  ): Result<List<ContentItem>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, _) = requireSession()
    val order = if (sortBy == "popular") "view_count.desc" else "created_at.desc"
    val cleanQuery = search.trim().replace(Regex("[,()*]"), " ").trim()
    val searchOr = if (cleanQuery.isNotBlank()) "(title.ilike.*$cleanQuery*,description.ilike.*$cleanQuery*)" else null
    api.getContent(
      apiKey = AppConfig.supabaseAnonKey,
      bearer = "Bearer $token",
      publishedFilter = if (includeUnpublished) null else "eq.true",
      categoryFilter = categoryId?.takeIf { it.isNotBlank() }?.let { "eq.$it" },
      featuredFilter = featuredOnly?.let { "eq.$it" },
      searchOr = searchOr,
      order = order,
      limit = limit,
      offset = offset,
    )
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchContentById(id: String): Result<ContentItem?> = runCatching {
    val api = restApi ?: return@runCatching null
    val (token, _) = requireSession()
    api.getContent(
      apiKey = AppConfig.supabaseAnonKey,
      bearer = "Bearer $token",
      idFilter = "eq.$id",
      publishedFilter = null,
      limit = 1,
    ).firstOrNull()
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun requestPlaybackUrl(contentId: String, positionSeconds: Int = 0): Result<PlaybackResponse> =
    runCatching {
      val api = fnApi ?: throw IllegalStateException("Backend not configured")
      val (token, _) = requireSession()
      api.requestContentPlayback(
        AppConfig.supabaseAnonKey,
        "Bearer $token",
        token,
        mapOf("contentId" to contentId, "positionSeconds" to positionSeconds),
      )
    }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun toggleBookmark(contentId: String): Result<Boolean> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.toggleBookmark(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf("action" to "toggle_bookmark", "content_id" to contentId),
    ).bookmarked
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun submitReport(
    contentId: String,
    reason: String,
    details: String,
    removalRequested: Boolean,
  ): Result<Unit> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.userActionRaw(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf(
        "action" to "submit_report",
        "content_id" to contentId,
        "reason" to reason,
        "details" to details,
        "removal_requested" to removalRequested,
      ),
    )
    Unit
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun submitSupportRequest(subject: String, message: String, category: String): Result<Unit> =
    runCatching {
      val api = fnApi ?: throw IllegalStateException("Backend not configured")
      val (token, _) = requireSession()
      api.userActionRaw(
        AppConfig.supabaseAnonKey,
        "Bearer $token",
        token,
        mapOf(
          "action" to "submit_support",
          "subject" to subject,
          "message" to message,
          "category" to category,
        ),
      )
      Unit
    }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchPlans(includeInactive: Boolean = false): Result<List<PremiumPlan>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, _) = requireSession()
    api.getPremiumPlans(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      activeFilter = if (includeInactive) null else "eq.true",
    )
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAppSettings(): Result<Map<String, AppSetting>> = runCatching {
    val api = restApi ?: return@runCatching emptyMap()
    val (token, _) = requireSession()
    api.getAppSettings(AppConfig.supabaseAnonKey, "Bearer $token").associateBy { it.key }
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserBookmarks(): Result<List<BookmarkRow>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getBookmarks(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserHistory(): Result<List<ViewingHistoryRow>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getViewingHistory(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserPayments(): Result<List<PaymentRequest>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getMyPayments(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserSubscriptions(): Result<List<Subscription>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getMySubscriptions(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserReferrals(): Result<List<ReferralRecord>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getMyReferrals(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchUserSupport(): Result<List<SupportRequest>> = runCatching {
    val api = restApi ?: return@runCatching emptyList()
    val (token, tid) = requireSession()
    api.getMySupportRequests(AppConfig.supabaseAnonKey, "Bearer $token", "eq.$tid")
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun uploadUriToSignedUrl(uri: Uri, signedUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      val mimeType = resolver.getType(uri) ?: "application/octet-stream"
      val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Unable to read selected media file")
      val req = Request.Builder()
        .url(signedUrl)
        .put(bytes.toRequestBody(mimeType.toMediaType()))
        .build()
      okHttp.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw IllegalStateException("Storage upload failed (${resp.code})")
      }
    }
  }

  suspend fun submitPayment(
    planId: String,
    paymentMethod: String,
    transactionRef: String,
    notes: String,
    proofUri: Uri?,
  ): Result<Unit> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    var proofPath = ""
    if (proofUri != null) {
      val signed = api.paymentUploadSign(
        AppConfig.supabaseAnonKey,
        "Bearer $token",
        token,
        mapOf("action" to "create_proof_upload_url", "fileExt" to "jpg"),
      )
      uploadUriToSignedUrl(proofUri, signed.signedUrl).getOrThrow()
      proofPath = signed.path
    }
    api.paymentActionRaw(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf(
        "action" to "submit_payment",
        "plan_id" to planId,
        "payment_method" to paymentMethod,
        "transaction_reference" to transactionRef,
        "proof_path" to proofPath,
        "notes" to notes,
      ),
    )
    Unit
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  // =========================================================================
  // OWNER-ONLY ADMIN CALLS (Server enforces Telegram ID 8254788387)
  // =========================================================================

  suspend fun fetchAdminStats(): Result<DashboardStats> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminStats(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "dashboard_stats")).stats
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminUsers(): Result<List<Profile>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminUsers(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_users")).users
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun setUserBlocked(telegramId: Long, blocked: Boolean, reason: String): Result<Unit> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminRaw(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf("action" to "set_user_blocked", "telegram_id" to telegramId, "is_blocked" to blocked, "reason" to reason),
    )
    Unit
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminPayments(): Result<List<PaymentRequest>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminPayments(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_payments")).payments
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun reviewPayment(paymentId: String, decision: String, reviewNotes: String): Result<Unit> =
    runCatching {
      val api = fnApi ?: throw IllegalStateException("Backend not configured")
      val (token, _) = requireSession()
      api.paymentActionRaw(
        AppConfig.supabaseAnonKey,
        "Bearer $token",
        token,
        mapOf(
          "action" to "review_payment",
          "payment_id" to paymentId,
          "decision" to decision,
          "review_notes" to reviewNotes,
        ),
      )
      Unit
    }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminSubscriptions(): Result<List<Subscription>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminSubscriptions(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_subscriptions")).subscriptions
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminReports(): Result<List<ContentReport>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminReports(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_reports")).reports
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminSupport(): Result<List<SupportRequest>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminSupport(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_support")).supportRequests
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun fetchAdminLogs(): Result<List<ActivityLog>> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    api.adminLogs(AppConfig.supabaseAnonKey, "Bearer $token", token, mapOf("action" to "list_activity_logs")).logs
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun adminUploadThumbnail(uri: Uri): Result<String> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    val sign = api.adminUploadSign(
      AppConfig.supabaseAnonKey,
      "Bearer $token",
      token,
      mapOf("action" to "create_upload_url", "bucket" to "thumbnails", "file_ext" to "jpg"),
    )
    uploadUriToSignedUrl(uri, sign.signedUrl).getOrThrow()
    sign.publicOrStoragePath ?: sign.path
  }.recoverCatching { throw IllegalStateException(parseError(it)) }

  suspend fun adminInvoke(action: String, payload: Map<String, Any>): Result<Unit> = runCatching {
    val api = fnApi ?: throw IllegalStateException("Backend not configured")
    val (token, _) = requireSession()
    val body = buildMap<String, Any> {
      put("action", action)
      putAll(payload)
    }
    api.adminRaw(AppConfig.supabaseAnonKey, "Bearer $token", token, body)
    Unit
  }.recoverCatching { throw IllegalStateException(parseError(it)) }
}
