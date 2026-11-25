package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.AppConfig
import com.example.data.model.AppSetting
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.AdminUiState
import com.example.ui.viewmodel.AdminViewModel

fun LazyListScope.adminStatsSection(state: AdminUiState) {
  val s = state.stats
  if (s == null) {
    item { EmptyStateCard("Loading Live Stats", "Fetching counts from Supabase PostgreSQL...") }
    return
  }
  item {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Users", "${s.totalUsers}", "${s.blockedUsers} blocked", Modifier.weight(1f))
        StatTile("Content", "${s.publishedContent}/${s.totalContent}", "Published", Modifier.weight(1f))
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Payments", "${s.pendingPayments}", "Pending review", Modifier.weight(1f))
        StatTile("Subscriptions", "${s.activeSubscriptions}", "Active Pro", Modifier.weight(1f))
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Reports", "${s.openReports}", "Pending moderation", Modifier.weight(1f))
        StatTile("Support", "${s.openSupport}", "Open tickets", Modifier.weight(1f))
      }
    }
  }
}

@Composable
private fun StatTile(label: String, value: String, subtitle: String, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

fun LazyListScope.adminPaymentsAndSubsSection(state: AdminUiState, vm: AdminViewModel) {
  item {
    var grantTid by remember { mutableStateOf("") }
    var grantDays by remember { mutableStateOf("30") }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Grant Premium Membership", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = grantTid,
            onValueChange = { grantTid = it },
            label = { Text("Telegram ID") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = grantDays,
            onValueChange = { grantDays = it },
            label = { Text("Days") },
            singleLine = true,
            modifier = Modifier.width(96.dp),
          )
        }
        GradientButton(
          text = "Grant Active Membership",
          enabled = grantTid.toLongOrNull() != null && (grantDays.toIntOrNull() ?: 0) > 0,
          onClick = {
            vm.grantSubscription(grantTid.toLong(), grantDays.toInt())
            grantTid = ""
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  item { Text("Payment Requests (${state.payments.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.payments, key = { "pay_${it.id}" }) { pay ->
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("TG #${pay.userTelegramId} • ${pay.amount} ${pay.currency}", style = MaterialTheme.typography.titleMedium)
          StatusPill(
            text = pay.status.uppercase(),
            color = when (pay.status) {
              "approved" -> SuccessEmerald
              "rejected" -> DangerCrimson
              else -> WarningAmber
            },
          )
        }
        Text("Method: ${pay.paymentMethod} • Ref: ${pay.transactionReference}", style = MaterialTheme.typography.bodyMedium)
        if (pay.status == "pending") {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { vm.reviewPayment(pay.id, "approved", "Verified by Owner") },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Approve") }
            Button(
              onClick = { vm.reviewPayment(pay.id, "rejected", "Verification failed") },
              colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Reject") }
          }
        }
      }
    }
  }

  item { Text("Subscriptions (${state.subscriptions.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.subscriptions, key = { "sub_${it.id}" }) { sub ->
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("TG #${sub.userTelegramId} • ${sub.status.uppercase()}", style = MaterialTheme.typography.titleMedium)
          Text("Expires: ${sub.expiresAt.take(10)}", style = MaterialTheme.typography.bodyMedium)
        }
        if (sub.status == "active") {
          OutlinedButton(
            onClick = { vm.revokeSubscription(sub.id) },
            modifier = Modifier.heightIn(min = 48.dp),
          ) { Text("Revoke", color = DangerCrimson) }
        }
      }
    }
  }
}

fun LazyListScope.adminContentSection(state: AdminUiState, vm: AdminViewModel) {
  item {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var mediaType by remember { mutableStateOf("video") }
    var mediaPath by remember { mutableStateOf("") }
    var thumbUrl by remember { mutableStateOf("") }
    var thumbUri by remember { mutableStateOf<Uri?>(null) }
    var isFeatured by remember { mutableStateOf(false) }
    var isPremium by remember { mutableStateOf(false) }
    var isPublished by remember { mutableStateOf(true) }
    var rightsVerified by remember { mutableStateOf(false) }
    var consentDocumented by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      thumbUri = uri
    }

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Publish / Upload Content", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = mediaPath, onValueChange = { mediaPath = it }, label = { Text("Storage Path or HTTPS Media URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
          onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
          Icon(Icons.Default.AttachFile, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(if (thumbUri != null) "Thumbnail Selected for Upload" else "Pick Thumbnail Image")
        }
        if (state.categories.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.categories.take(4).forEach { cat ->
              FilterChip(
                selected = categoryId == cat.id,
                onClick = { categoryId = if (categoryId == cat.id) null else cat.id },
                label = { Text(cat.name) },
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("video", "audio", "image").forEach { mt ->
            FilterChip(selected = mediaType == mt, onClick = { mediaType = mt }, label = { Text(mt.uppercase()) })
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = isFeatured, onCheckedChange = { isFeatured = it })
          Text("Featured")
          Spacer(Modifier.width(12.dp))
          Checkbox(checked = isPremium, onCheckedChange = { isPremium = it })
          Text("Premium Only")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = rightsVerified, onCheckedChange = { rightsVerified = it })
          Text("Rights Verified")
          Spacer(Modifier.width(12.dp))
          Checkbox(checked = consentDocumented, onCheckedChange = { consentDocumented = it })
          Text("Consent Documented")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = isPublished, onCheckedChange = { isPublished = it })
          Spacer(Modifier.width(8.dp))
          Text("Published")
        }
        GradientButton(
          text = "Save Content Record",
          enabled = title.trim().length >= 2,
          onClick = {
            vm.upsertContent(
              id = null,
              title = title.trim(),
              description = description.trim(),
              categoryId = categoryId,
              mediaType = mediaType,
              thumbnailUrl = thumbUrl.trim(),
              thumbnailUri = thumbUri,
              mediaPath = mediaPath.trim(),
              isFeatured = isFeatured,
              isPremium = isPremium,
              isPublished = isPublished,
              rightsVerified = rightsVerified,
              consentDocumented = consentDocumented,
            )
            title = ""
            description = ""
            mediaPath = ""
            thumbUri = null
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  items(state.content, key = { "adm_c_${it.id}" }) { item ->
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(item.title, style = MaterialTheme.typography.titleMedium)
          Text(
            "${if (item.isPublished) "Published" else "Draft"} • ${if (item.isPremium) "Premium" else "Open"} • ${item.viewCount} views",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { vm.deleteContent(item.id) }) {
          Icon(Icons.Default.Delete, contentDescription = "Delete content", tint = DangerCrimson)
        }
      }
    }
  }
}

fun LazyListScope.adminCategoriesAndPlansSection(state: AdminUiState, vm: AdminViewModel) {
  item {
    var catName by remember { mutableStateOf("") }
    var catDesc by remember { mutableStateOf("") }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Create Category", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = catName, onValueChange = { catName = it }, label = { Text("Category Name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = catDesc, onValueChange = { catDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        GradientButton(
          text = "Save Category",
          enabled = catName.trim().length >= 2,
          onClick = {
            vm.upsertCategory(null, catName.trim(), catDesc.trim())
            catName = ""
            catDesc = ""
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  items(state.categories, key = { "adm_cat_${it.id}" }) { cat ->
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("${cat.name} (${cat.slug})", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.deleteCategory(cat.id) }) {
          Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = DangerCrimson)
        }
      }
    }
  }

  item {
    var planName by remember { mutableStateOf("") }
    var planPrice by remember { mutableStateOf("9.99") }
    var planCurrency by remember { mutableStateOf("USD") }
    var planDays by remember { mutableStateOf("30") }
    var planDesc by remember { mutableStateOf("") }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Create Premium Plan", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = planName, onValueChange = { planName = it }, label = { Text("Plan Name") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = planPrice, onValueChange = { planPrice = it }, label = { Text("Price") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = planCurrency, onValueChange = { planCurrency = it }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
          OutlinedTextField(value = planDays, onValueChange = { planDays = it }, label = { Text("Days") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = planDesc, onValueChange = { planDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        GradientButton(
          text = "Save Premium Plan",
          enabled = planName.trim().length >= 2 && planPrice.toDoubleOrNull() != null && (planDays.toIntOrNull() ?: 0) > 0,
          onClick = {
            vm.upsertPlan(null, planName.trim(), planPrice.toDouble(), planCurrency.trim(), planDays.toInt(), planDesc.trim())
            planName = ""
            planDesc = ""
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  items(state.plans, key = { "adm_plan_${it.id}" }) { p ->
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("${p.name} — ${p.priceAmount} ${p.currency} / ${p.durationDays}d", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.deletePlan(p.id) }) {
          Icon(Icons.Default.Delete, contentDescription = "Delete plan", tint = DangerCrimson)
        }
      }
    }
  }
}

fun LazyListScope.adminUsersAndReportsSection(state: AdminUiState, vm: AdminViewModel) {
  item { Text("Registered Users (${state.users.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.users, key = { "adm_u_${it.telegramId}" }) { user ->
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("${user.firstName} (@${user.username ?: "n/a"})", style = MaterialTheme.typography.titleMedium)
          Text("TG #${user.telegramId} • ${if (user.isBlocked) "BLOCKED" else "ACTIVE"}", style = MaterialTheme.typography.bodyMedium)
        }
        if (user.telegramId != AppConfig.OWNER_TELEGRAM_ID) {
          OutlinedButton(
            onClick = { vm.toggleUserBlocked(user.telegramId, !user.isBlocked, "Owner moderation action") },
            modifier = Modifier.heightIn(min = 48.dp),
          ) {
            Text(if (user.isBlocked) "Unblock" else "Block", color = if (user.isBlocked) SuccessEmerald else DangerCrimson)
          }
        }
      }
    }
  }

  item { Text("Content Reports (${state.reports.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.reports, key = { "adm_rep_${it.id}" }) { rep ->
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${rep.content?.title ?: rep.contentId} • ${rep.reason}", style = MaterialTheme.typography.titleMedium)
        Text(rep.details, style = MaterialTheme.typography.bodyMedium)
        StatusPill(text = rep.status.uppercase(), color = WarningAmber)
        if (rep.status == "pending") {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { vm.resolveReport(rep.id, "resolved_removed", "Unpublished by Owner") },
              colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Remove Content") }
            OutlinedButton(
              onClick = { vm.resolveReport(rep.id, "dismissed", "Verified authorized") },
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Dismiss") }
          }
        }
      }
    }
  }
}

fun LazyListScope.adminSettingsAndLogsSection(
  state: AdminUiState,
  liveSettings: Map<String, AppSetting>,
  vm: AdminViewModel,
) {
  item {
    val savedPay = liveSettings["payment_instructions"]?.value.orEmpty()
    val savedRef = liveSettings["referral_settings"]?.value.orEmpty()
    var instrTitle by remember(savedPay) {
      mutableStateOf(savedPay["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Payment Instructions")
    }
    var instrBody by remember(savedPay) {
      mutableStateOf(savedPay["instructions"]?.toString().orEmpty())
    }
    var refDays by remember(savedRef) {
      val raw = savedRef["reward_days_referrer"]
      val num = (raw as? Number)?.toInt() ?: raw?.toString()?.toDoubleOrNull()?.toInt() ?: 3
      mutableStateOf(num.toString())
    }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App, Payment & Referral Settings", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = instrTitle, onValueChange = { instrTitle = it }, label = { Text("Payment Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = instrBody, onValueChange = { instrBody = it }, label = { Text("Payment Instructions for Users") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        GradientButton(
          text = "Save Payment Instructions",
          enabled = instrBody.trim().isNotBlank(),
          onClick = {
            vm.updateSetting("payment_instructions", mapOf("title" to instrTitle.trim(), "instructions" to instrBody.trim()))
          },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(value = refDays, onValueChange = { refDays = it }, label = { Text("Referral Reward Days (Referrer)") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
          onClick = {
            vm.updateSetting(
              "referral_settings",
              mapOf("enabled" to true, "reward_days_referrer" to (refDays.toIntOrNull() ?: 3)),
            )
          },
          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Save Referral Settings") }
      }
    }
  }

  item {
    var annTitle by remember { mutableStateOf("") }
    var annBody by remember { mutableStateOf("") }
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Publish Announcement", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = annTitle, onValueChange = { annTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = annBody, onValueChange = { annBody = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth())
        GradientButton(
          text = "Post Announcement",
          enabled = annTitle.trim().length >= 2 && annBody.trim().isNotBlank(),
          onClick = {
            vm.upsertAnnouncement(annTitle.trim(), annBody.trim(), "info")
            annTitle = ""
            annBody = ""
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }

  item { Text("Support Tickets (${state.supportTickets.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.supportTickets, key = { "adm_sup_${it.id}" }) { t ->
    var replyText by remember(t.id) { mutableStateOf(t.adminReply.orEmpty()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TG #${t.userTelegramId} • ${t.subject} (${t.status})", style = MaterialTheme.typography.titleMedium)
        Text(t.message, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Owner Reply") }, modifier = Modifier.fillMaxWidth())
        Button(
          onClick = { vm.replySupport(t.id, "resolved", replyText.trim()) },
          enabled = replyText.trim().isNotBlank(),
          modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Send Reply & Mark Resolved") }
      }
    }
  }

  item { Text("Activity Logs (${state.logs.size})", style = MaterialTheme.typography.titleLarge) }
  items(state.logs.take(30), key = { "log_${it.id}" }) { log ->
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
      Column(modifier = Modifier.padding(12.dp)) {
        Text("${log.action} • ${log.targetType} (${log.targetId ?: "-"})", style = MaterialTheme.typography.labelLarge)
        Text("Actor: ${log.actorTelegramId} • ${log.createdAt.take(19)}", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}
