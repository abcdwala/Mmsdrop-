package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Profile(
  @Json(name = "telegram_id") val telegramId: Long,
  val username: String? = null,
  @Json(name = "first_name") val firstName: String = "",
  @Json(name = "last_name") val lastName: String? = null,
  @Json(name = "photo_url") val photoUrl: String? = null,
  @Json(name = "is_blocked") val isBlocked: Boolean = false,
  @Json(name = "block_reason") val blockReason: String? = null,
  @Json(name = "age_verified_at") val ageVerifiedAt: String? = null,
  @Json(name = "consent_accepted_at") val consentAcceptedAt: String? = null,
  @Json(name = "referral_code") val referralCode: String = "",
  @Json(name = "referred_by") val referredBy: Long? = null,
  @Json(name = "created_at") val createdAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class Category(
  val id: String,
  val name: String,
  val slug: String = "",
  val description: String = "",
  @Json(name = "icon_name") val iconName: String = "folder",
  @Json(name = "sort_order") val sortOrder: Int = 0,
  @Json(name = "is_active") val isActive: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class ContentItem(
  val id: String,
  val title: String,
  val description: String = "",
  @Json(name = "category_id") val categoryId: String? = null,
  @Json(name = "thumbnail_path") val thumbnailPath: String = "",
  @Json(name = "media_path") val mediaPath: String = "",
  @Json(name = "media_type") val mediaType: String = "video",
  @Json(name = "duration_seconds") val durationSeconds: Int = 0,
  @Json(name = "is_featured") val isFeatured: Boolean = false,
  @Json(name = "is_premium") val isPremium: Boolean = false,
  @Json(name = "is_published") val isPublished: Boolean = false,
  @Json(name = "rights_verified") val rightsVerified: Boolean = false,
  @Json(name = "consent_documented") val consentDocumented: Boolean = false,
  @Json(name = "view_count") val viewCount: Long = 0L,
  @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class BookmarkRow(
  val id: String,
  @Json(name = "user_telegram_id") val userTelegramId: Long,
  @Json(name = "content_id") val contentId: String,
  @Json(name = "created_at") val createdAt: String = "",
  val content: ContentItem? = null,
)

@JsonClass(generateAdapter = true)
data class ViewingHistoryRow(
  val id: String,
  @Json(name = "user_telegram_id") val userTelegramId: Long,
  @Json(name = "content_id") val contentId: String,
  @Json(name = "last_position_seconds") val lastPositionSeconds: Int = 0,
  @Json(name = "viewed_at") val viewedAt: String = "",
  val content: ContentItem? = null,
)

@JsonClass(generateAdapter = true)
data class PremiumPlan(
  val id: String,
  val name: String,
  val description: String = "",
  @Json(name = "price_amount") val priceAmount: Double = 0.0,
  val currency: String = "USD",
  @Json(name = "duration_days") val durationDays: Int = 30,
  val features: List<String> = emptyList(),
  @Json(name = "is_active") val isActive: Boolean = true,
  @Json(name = "sort_order") val sortOrder: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PlanSummary(
  val name: String = "",
  @Json(name = "duration_days") val durationDays: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PaymentRequest(
  val id: String,
  @Json(name = "user_telegram_id") val userTelegramId: Long,
  @Json(name = "plan_id") val planId: String,
  val amount: Double = 0.0,
  val currency: String = "USD",
  @Json(name = "payment_method") val paymentMethod: String = "",
  @Json(name = "transaction_reference") val transactionReference: String = "",
  @Json(name = "proof_path") val proofPath: String = "",
  val notes: String = "",
  val status: String = "pending",
  @Json(name = "review_notes") val reviewNotes: String? = null,
  @Json(name = "created_at") val createdAt: String = "",
  @Json(name = "premium_plans") val plan: PlanSummary? = null,
)

@JsonClass(generateAdapter = true)
data class Subscription(
  val id: String,
  @Json(name = "user_telegram_id") val userTelegramId: Long,
  @Json(name = "plan_id") val planId: String? = null,
  val status: String = "active",
  @Json(name = "starts_at") val startsAt: String = "",
  @Json(name = "expires_at") val expiresAt: String = "",
  @Json(name = "premium_plans") val plan: PlanSummary? = null,
)

@JsonClass(generateAdapter = true)
data class ReferralRecord(
  val id: String,
  @Json(name = "referrer_telegram_id") val referrerTelegramId: Long,
  @Json(name = "referred_telegram_id") val referredTelegramId: Long,
  @Json(name = "referral_code") val referralCode: String = "",
  @Json(name = "reward_status") val rewardStatus: String = "pending",
  @Json(name = "reward_days") val rewardDays: Int = 0,
  @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class Announcement(
  val id: String,
  val title: String,
  val body: String,
  val severity: String = "info",
  @Json(name = "is_active") val isActive: Boolean = true,
  @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class SupportRequest(
  val id: String,
  @Json(name = "user_telegram_id") val userTelegramId: Long,
  val subject: String,
  val message: String,
  val category: String = "general",
  val status: String = "open",
  @Json(name = "admin_reply") val adminReply: String? = null,
  @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class AppSetting(
  val key: String,
  val value: Map<String, Any?> = emptyMap(),
  val description: String = "",
)

@JsonClass(generateAdapter = true)
data class ActivityLog(
  val id: String,
  @Json(name = "actor_telegram_id") val actorTelegramId: Long,
  val action: String,
  @Json(name = "target_type") val targetType: String,
  @Json(name = "target_id") val targetId: String? = null,
  @Json(name = "created_at") val createdAt: String = "",
)

@JsonClass(generateAdapter = true)
data class ContentTitleSummary(val title: String = "")

@JsonClass(generateAdapter = true)
data class ContentReport(
  val id: String,
  @Json(name = "reporter_telegram_id") val reporterTelegramId: Long,
  @Json(name = "content_id") val contentId: String,
  val reason: String,
  val details: String,
  @Json(name = "removal_requested") val removalRequested: Boolean = false,
  val status: String = "pending",
  @Json(name = "resolution_notes") val resolutionNotes: String? = null,
  @Json(name = "created_at") val createdAt: String = "",
  val content: ContentTitleSummary? = null,
)

@JsonClass(generateAdapter = true)
data class DashboardStats(
  @Json(name = "total_users") val totalUsers: Int = 0,
  @Json(name = "blocked_users") val blockedUsers: Int = 0,
  @Json(name = "total_content") val totalContent: Int = 0,
  @Json(name = "published_content") val publishedContent: Int = 0,
  @Json(name = "pending_payments") val pendingPayments: Int = 0,
  @Json(name = "active_subscriptions") val activeSubscriptions: Int = 0,
  @Json(name = "open_reports") val openReports: Int = 0,
  @Json(name = "open_support") val openSupport: Int = 0,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
  @Json(name = "access_token") val accessToken: String,
  val profile: Profile,
  @Json(name = "is_owner") val isOwner: Boolean = false,
  @Json(name = "active_subscription") val activeSubscription: Subscription? = null,
)

@JsonClass(generateAdapter = true)
data class PlaybackResponse(
  @Json(name = "playback_url") val playbackUrl: String,
  @Json(name = "expires_in_seconds") val expiresInSeconds: Int = 300,
  @Json(name = "media_type") val mediaType: String = "video",
)

@JsonClass(generateAdapter = true)
data class UploadSignResponse(
  val bucket: String? = null,
  val path: String = "",
  @Json(name = "public_or_storage_path") val publicOrStoragePath: String? = null,
  @Json(name = "signed_url") val signedUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class BookmarkToggleResponse(val bookmarked: Boolean)

@JsonClass(generateAdapter = true)
data class ProfileResponse(val profile: Profile)

@JsonClass(generateAdapter = true)
data class AdminStatsResponse(val stats: DashboardStats)

@JsonClass(generateAdapter = true)
data class AdminUsersResponse(val users: List<Profile> = emptyList())

@JsonClass(generateAdapter = true)
data class AdminPaymentsResponse(val payments: List<PaymentRequest> = emptyList())

@JsonClass(generateAdapter = true)
data class AdminSubscriptionsResponse(val subscriptions: List<Subscription> = emptyList())

@JsonClass(generateAdapter = true)
data class AdminReportsResponse(val reports: List<ContentReport> = emptyList())

@JsonClass(generateAdapter = true)
data class AdminSupportResponse(
  @Json(name = "support_requests") val supportRequests: List<SupportRequest> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AdminLogsResponse(val logs: List<ActivityLog> = emptyList())
