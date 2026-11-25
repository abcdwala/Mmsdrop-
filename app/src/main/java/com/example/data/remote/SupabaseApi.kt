package com.example.data.remote

import com.example.data.model.AdminLogsResponse
import com.example.data.model.AdminPaymentsResponse
import com.example.data.model.AdminReportsResponse
import com.example.data.model.AdminStatsResponse
import com.example.data.model.AdminSubscriptionsResponse
import com.example.data.model.AdminSupportResponse
import com.example.data.model.AdminUsersResponse
import com.example.data.model.Announcement
import com.example.data.model.AppSetting
import com.example.data.model.AuthResponse
import com.example.data.model.BookmarkRow
import com.example.data.model.BookmarkToggleResponse
import com.example.data.model.Category
import com.example.data.model.ContentItem
import com.example.data.model.PaymentRequest
import com.example.data.model.PlaybackResponse
import com.example.data.model.PremiumPlan
import com.example.data.model.Profile
import com.example.data.model.ProfileResponse
import com.example.data.model.ReferralRecord
import com.example.data.model.Subscription
import com.example.data.model.SupportRequest
import com.example.data.model.UploadSignResponse
import com.example.data.model.ViewingHistoryRow
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseRestApi {
  @GET("rest/v1/profiles")
  suspend fun getProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("telegram_id") telegramIdFilter: String,
    @Query("select") select: String = "*",
  ): List<Profile>

  @GET("rest/v1/categories")
  suspend fun getCategories(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("is_active") activeFilter: String? = "eq.true",
    @Query("order") order: String = "sort_order.asc",
  ): List<Category>

  @GET("rest/v1/content")
  suspend fun getContent(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("id") idFilter: String? = null,
    @Query("is_published") publishedFilter: String? = "eq.true",
    @Query("category_id") categoryFilter: String? = null,
    @Query("is_featured") featuredFilter: String? = null,
    @Query("or") searchOr: String? = null,
    @Query("order") order: String = "created_at.desc",
    @Query("limit") limit: Int = 20,
    @Query("offset") offset: Int = 0,
  ): List<ContentItem>

  @GET("rest/v1/announcements")
  suspend fun getAnnouncements(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("is_active") activeFilter: String? = "eq.true",
    @Query("order") order: String = "created_at.desc",
  ): List<Announcement>

  @GET("rest/v1/premium_plans")
  suspend fun getPremiumPlans(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("is_active") activeFilter: String? = "eq.true",
    @Query("order") order: String = "sort_order.asc",
  ): List<PremiumPlan>

  @GET("rest/v1/app_settings")
  suspend fun getAppSettings(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("select") select: String = "key,value,description",
  ): List<AppSetting>

  @GET("rest/v1/bookmarks")
  suspend fun getBookmarks(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("user_telegram_id") userFilter: String,
    @Query("select") select: String = "*,content(*)",
    @Query("order") order: String = "created_at.desc",
  ): List<BookmarkRow>

  @GET("rest/v1/viewing_history")
  suspend fun getViewingHistory(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("user_telegram_id") userFilter: String,
    @Query("select") select: String = "*,content(*)",
    @Query("order") order: String = "viewed_at.desc",
  ): List<ViewingHistoryRow>

  @GET("rest/v1/payment_requests")
  suspend fun getMyPayments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("user_telegram_id") userFilter: String,
    @Query("select") select: String = "*,premium_plans(name,duration_days)",
    @Query("order") order: String = "created_at.desc",
  ): List<PaymentRequest>

  @GET("rest/v1/subscriptions")
  suspend fun getMySubscriptions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("user_telegram_id") userFilter: String,
    @Query("select") select: String = "*,premium_plans(name,duration_days)",
    @Query("order") order: String = "expires_at.desc",
  ): List<Subscription>

  @GET("rest/v1/referrals")
  suspend fun getMyReferrals(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("referrer_telegram_id") referrerFilter: String,
    @Query("order") order: String = "created_at.desc",
  ): List<ReferralRecord>

  @GET("rest/v1/support_requests")
  suspend fun getMySupportRequests(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Query("user_telegram_id") userFilter: String,
    @Query("order") order: String = "created_at.desc",
  ): List<SupportRequest>
}

interface SupabaseFunctionsApi {
  @POST("functions/v1/telegram-auth")
  suspend fun authenticateTelegram(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AuthResponse

  @POST("functions/v1/content-access")
  suspend fun requestContentPlayback(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): PlaybackResponse

  @POST("functions/v1/user-actions")
  suspend fun confirmCompliance(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): ProfileResponse

  @POST("functions/v1/user-actions")
  suspend fun toggleBookmark(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): BookmarkToggleResponse

  @POST("functions/v1/user-actions")
  suspend fun userActionRaw(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): Map<String, @JvmSuppressWildcards Any?>

  @POST("functions/v1/payment-workflow")
  suspend fun paymentUploadSign(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): UploadSignResponse

  @POST("functions/v1/payment-workflow")
  suspend fun paymentActionRaw(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): Map<String, @JvmSuppressWildcards Any?>

  @POST("functions/v1/admin-api")
  suspend fun adminStats(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminStatsResponse

  @POST("functions/v1/admin-api")
  suspend fun adminUsers(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminUsersResponse

  @POST("functions/v1/admin-api")
  suspend fun adminPayments(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminPaymentsResponse

  @POST("functions/v1/admin-api")
  suspend fun adminSubscriptions(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminSubscriptionsResponse

  @POST("functions/v1/admin-api")
  suspend fun adminReports(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminReportsResponse

  @POST("functions/v1/admin-api")
  suspend fun adminSupport(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminSupportResponse

  @POST("functions/v1/admin-api")
  suspend fun adminLogs(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): AdminLogsResponse

  @POST("functions/v1/admin-api")
  suspend fun adminUploadSign(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): UploadSignResponse

  @POST("functions/v1/admin-api")
  suspend fun adminRaw(
    @Header("apikey") apiKey: String,
    @Header("Authorization") bearer: String,
    @Header("x-dropmms-session") sessionJwt: String,
    @Body body: Map<String, @JvmSuppressWildcards Any>,
  ): Map<String, @JvmSuppressWildcards Any?>
}
