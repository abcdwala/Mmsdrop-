package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PremiumPlan
import com.example.ui.components.ConfigRequiredBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GradientButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MainUiState

@Composable
fun PremiumScreen(
  state: MainUiState,
  onSubmitPayment: (planId: String, method: String, reference: String, notes: String, proofUri: Uri?) -> Unit,
  onOpenSetup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedPlanForPayment by remember { mutableStateOf<PremiumPlan?>(null) }
  val instrMap = state.settings["payment_instructions"]?.value.orEmpty()
  val instrTitle = instrMap["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Payment Instructions"
  val instrBody = instrMap["instructions"]?.toString()?.takeIf { it.isNotBlank() }
    ?: "Owner-configured payment instructions load from app_settings once connected."

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Premium Membership", style = MaterialTheme.typography.headlineMedium)
      }
    }

    if (!state.isBackendConfigured) {
      item {
        ConfigRequiredBanner(missingItems = state.missingConfigItems, onOpenDiagnostics = onOpenSetup)
      }
    }

    // Current subscription card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val sub = state.activeSubscription
          StatusPill(
            text = if (sub != null) "ACTIVE PRO" else "STANDARD ACCESS",
            color = if (sub != null) SuccessEmerald else MaterialTheme.colorScheme.secondary,
          )
          Text(
            text = if (sub != null) "${sub.plan?.name ?: "Premium"} expires ${sub.expiresAt.take(10)}" else "Upgrade to unlock Premium vault releases",
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }

    // Owner Payment Instructions
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(instrTitle, style = MaterialTheme.typography.titleLarge)
          Text(instrBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Plans from Database
    item {
      Text("Membership Plans & Comparison", style = MaterialTheme.typography.titleLarge)
    }

    if (!state.isAuthenticated) {
      item {
        EmptyStateCard(
          title = "Authenticate to View & Purchase Plans",
          description = "Sign in with Telegram on Home to load live membership plans and track your payment requests.",
        )
      }
    } else if (state.plans.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Active Plans Found",
          description = "No rows exist in `premium_plans` yet. The Owner can create membership plans in the Owner Admin panel.",
        )
      }
    } else {
      items(state.plans, key = { it.id }) { plan ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(plan.name, style = MaterialTheme.typography.titleLarge)
              StatusPill(
                text = "${plan.priceAmount} ${plan.currency} / ${plan.durationDays}d",
                color = MaterialTheme.colorScheme.primary,
              )
            }
            if (plan.description.isNotBlank()) {
              Text(plan.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            plan.features.forEach { feat ->
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessEmerald)
                Text(feat, style = MaterialTheme.typography.bodyMedium)
              }
            }
            GradientButton(
              text = "Submit Payment for ${plan.name}",
              onClick = { selectedPlanForPayment = plan },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("select_plan_${plan.id}"),
            )
          }
        }
      }
    }

    // Payment Status Tracking
    if (state.isAuthenticated) {
      item {
        Text("Payment Request Tracking", style = MaterialTheme.typography.titleLarge)
      }
      if (state.myPayments.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Payment Submissions Yet",
            description = "Submitted transaction references appear here with real pending, approved, or rejected status from the database.",
          )
        }
      } else {
        items(state.myPayments, key = { it.id }) { pay ->
          val statusColor = when (pay.status) {
            "approved" -> SuccessEmerald
            "rejected" -> DangerCrimson
            else -> WarningAmber
          }
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("${pay.plan?.name ?: "Plan"} • ${pay.amount} ${pay.currency}", style = MaterialTheme.typography.titleMedium)
                StatusPill(text = pay.status.uppercase(), color = statusColor)
              }
              Text("Method: ${pay.paymentMethod} • Ref: ${pay.transactionReference}", style = MaterialTheme.typography.bodyMedium)
              if (!pay.reviewNotes.isNullOrBlank()) {
                Text("Owner note: ${pay.reviewNotes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }

  selectedPlanForPayment?.let { plan ->
    PaymentSubmissionDialog(
      plan = plan,
      onDismiss = { selectedPlanForPayment = null },
      onSubmit = { method, ref, notes, uri ->
        onSubmitPayment(plan.id, method, ref, notes, uri)
        selectedPlanForPayment = null
      },
    )
  }
}

@Composable
private fun PaymentSubmissionDialog(
  plan: PremiumPlan,
  onDismiss: () -> Unit,
  onSubmit: (method: String, ref: String, notes: String, proofUri: Uri?) -> Unit,
) {
  var method by remember { mutableStateOf("") }
  var reference by remember { mutableStateOf("") }
  var notes by remember { mutableStateOf("") }
  var proofUri by remember { mutableStateOf<Uri?>(null) }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> proofUri = uri }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Submit Payment — ${plan.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Amount due: ${plan.priceAmount} ${plan.currency} (${plan.durationDays} days)",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = method,
          onValueChange = { method = it },
          label = { Text("Payment Method (e.g. USDT TRC20, Bank, UPI)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = reference,
          onValueChange = { reference = it },
          label = { Text("Transaction ID / Hash") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = notes,
          onValueChange = { notes = it },
          label = { Text("Notes (optional)") },
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
          onClick = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        ) {
          Icon(Icons.Default.AttachFile, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(if (proofUri != null) "Payment Proof Attached" else "Attach Receipt Screenshot (Optional)")
        }
      }
    },
    confirmButton = {
      GradientButton(
        text = "Submit Verification",
        enabled = method.trim().length >= 2 && reference.trim().length >= 3,
        onClick = { onSubmit(method.trim(), reference.trim(), notes.trim(), proofUri) },
      )
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
