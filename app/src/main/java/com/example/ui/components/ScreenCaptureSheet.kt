package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.AutoScrollAccessibilityService
import com.example.service.CaptureSessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenCaptureSheet(
  sheetState: SheetState,
  sessionState: CaptureSessionState,
  onStartCapture: (intervalMs: Long, deduplicate: Boolean, autoScroll: Boolean, scrollSpeed: Float) -> Unit,
  onCaptureNow: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleAutoScroll: () -> Unit = {},
  onFinishAndStitch: () -> Unit,
  onCancelCapture: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  var selectedIntervalSec by remember { mutableFloatStateOf(0.5f) }
  var autoDeduplicate by remember { mutableStateOf(true) }
  var autoScrollEnabled by remember { mutableStateOf(false) }
  var scrollSpeedRatio by remember { mutableFloatStateOf(0.40f) }

  val intervalOptions = listOf(0.2f, 0.3f, 0.4f, 0.5f)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp)
        .verticalScroll(rememberScrollState())
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = CircleShape,
            color = if (sessionState.isRunning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(42.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (sessionState.isRunning) Icons.Default.CameraAlt else Icons.Default.ScreenShare,
                contentDescription = null,
                tint = if (sessionState.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
            }
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = stringResource(R.string.capture_sheet_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = if (sessionState.isRunning) {
                stringResource(R.string.capture_sheet_subtitle_running, sessionState.capturedCount)
              } else {
                stringResource(R.string.capture_sheet_subtitle_idle)
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (sessionState.isRunning) {
        // Active Session Controls
        ActiveCaptureControls(
          sessionState = sessionState,
          onCaptureNow = onCaptureNow,
          onTogglePause = onTogglePause,
          onToggleAutoScroll = onToggleAutoScroll,
          onFinishAndStitch = onFinishAndStitch,
          onCancelCapture = onCancelCapture
        )
      } else {
        // Configuration & Start
        CaptureConfigurationView(
          selectedIntervalSec = selectedIntervalSec,
          intervalOptions = intervalOptions,
          onSelectInterval = { selectedIntervalSec = it },
          autoDeduplicate = autoDeduplicate,
          onToggleDeduplicate = { autoDeduplicate = it },
          autoScrollEnabled = autoScrollEnabled,
          onToggleAutoScroll = { autoScrollEnabled = it },
          scrollSpeedRatio = scrollSpeedRatio,
          onSelectScrollSpeed = { scrollSpeedRatio = it },
          onStart = {
            val ms = (selectedIntervalSec * 1000).toLong()
            onStartCapture(ms, autoDeduplicate, autoScrollEnabled, scrollSpeedRatio)
          }
        )
      }
    }
  }
}

@Composable
private fun ActiveCaptureControls(
  sessionState: CaptureSessionState,
  onCaptureNow: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleAutoScroll: () -> Unit,
  onFinishAndStitch: () -> Unit,
  onCancelCapture: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    shape = RoundedCornerShape(16.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Recording Pulse Indicator
          Box(
            modifier = Modifier
              .size(14.dp)
              .scale(if (!sessionState.isPaused) pulseScale else 1f)
              .background(
                color = if (!sessionState.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                shape = CircleShape
              )
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (sessionState.isPaused) {
              stringResource(R.string.capture_state_paused)
            } else {
              stringResource(R.string.capture_state_recording, sessionState.intervalSeconds)
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (sessionState.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
          )
        }

        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Text(
            text = stringResource(R.string.capture_count_badge, sessionState.capturedCount),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }

      // Auto-Scroll Status Chip
      if (sessionState.autoScrollEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.SwipeVertical, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.capture_autoscroll_title),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Thumbnail Preview if available
      sessionState.lastCapturedThumbnail?.let { thumb ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Image(
            bitmap = thumb.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
              .size(54.dp)
              .clip(RoundedCornerShape(8.dp))
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = stringResource(R.string.capture_latest_shot_captured),
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = stringResource(R.string.capture_notification_tip),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Quick Control Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = onCaptureNow,
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.btn_capture_now_short), style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(
          onClick = onToggleAutoScroll,
          modifier = Modifier
            .weight(1.2f)
            .height(44.dp),
          shape = RoundedCornerShape(12.dp),
          colors = if (sessionState.autoScrollEnabled) {
            ButtonDefaults.outlinedButtonColors(
              containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
              contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
          } else {
            ButtonDefaults.outlinedButtonColors()
          }
        ) {
          Icon(Icons.Default.SwipeVertical, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            if (sessionState.autoScrollEnabled) stringResource(R.string.capture_action_autoscroll_on) else stringResource(R.string.capture_action_autoscroll_off),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
          )
        }

        OutlinedButton(
          onClick = onTogglePause,
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(
            if (sessionState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (sessionState.isPaused) stringResource(R.string.capture_action_resume) else stringResource(R.string.capture_action_pause),
            style = MaterialTheme.typography.bodySmall
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Primary Action: Finish & Stitch
      Button(
        onClick = onFinishAndStitch,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("btn_finish_and_stitch"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          stringResource(R.string.btn_finish_and_stitch, sessionState.capturedCount),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedButton(
        onClick = onCancelCapture,
        modifier = Modifier
          .fillMaxWidth()
          .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.error
        )
      ) {
        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.btn_discard_capture))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureConfigurationView(
  selectedIntervalSec: Float,
  intervalOptions: List<Float>,
  onSelectInterval: (Float) -> Unit,
  autoDeduplicate: Boolean,
  onToggleDeduplicate: (Boolean) -> Unit,
  autoScrollEnabled: Boolean,
  onToggleAutoScroll: (Boolean) -> Unit,
  scrollSpeedRatio: Float,
  onSelectScrollSpeed: (Float) -> Unit,
  onStart: () -> Unit
) {
  val context = LocalContext.current
  val isAccessibilityOn = remember(autoScrollEnabled) {
    AutoScrollAccessibilityService.isAccessibilityServiceEnabled(context)
  }

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // Auto-Scroll Feature Card (Hands-Free Mode)
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = if (autoScrollEnabled) {
          MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        } else {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        }
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.SwipeVertical,
              contentDescription = null,
              tint = if (autoScrollEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = stringResource(R.string.capture_autoscroll_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = stringResource(R.string.capture_autoscroll_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Switch(
            checked = autoScrollEnabled,
            onCheckedChange = onToggleAutoScroll
          )
        }

        // If auto scroll is enabled but accessibility is not turned on in system settings
        AnimatedVisibility(visible = autoScrollEnabled && !isAccessibilityOn) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp)
              .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Text(
              text = stringResource(R.string.accessibility_needed_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.accessibility_needed_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
              onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                  flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.btn_open_accessibility_settings))
            }
          }
        }

        // Scroll Distance Selection (when auto-scroll is on)
        AnimatedVisibility(visible = autoScrollEnabled) {
          Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
              text = stringResource(R.string.capture_scroll_distance_title),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(0.20f to "20%", 0.30f to "30%", 0.40f to "40% ★").forEach { (ratio, label) ->
                val isSelected = scrollSpeedRatio == ratio
                FilterChip(
                  selected = isSelected,
                  onClick = { onSelectScrollSpeed(ratio) },
                  label = {
                    Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                  },
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }
      }
    }

    // Interval Selection Section
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Default.Timer,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.capture_interval_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = stringResource(R.string.capture_interval_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          intervalOptions.forEach { sec ->
            val isSelected = selectedIntervalSec == sec
            FilterChip(
              selected = isSelected,
              onClick = { onSelectInterval(sec) },
              label = {
                Text(
                  text = "${sec}s" + if (sec == 0.5f) " ★" else "",
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              modifier = Modifier.weight(1f),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
              )
            )
          }
        }
      }
    }

    // Smart Duplicate Prevention
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.FilterNone,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.capture_deduplicate_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.capture_deduplicate_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
          checked = autoDeduplicate,
          onCheckedChange = onToggleDeduplicate
        )
      }
    }

    // Floating Overlay Controls Card
    val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(context)
    } else true

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.Layers,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.settings_overlay_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }

        Text(
          text = stringResource(R.string.settings_overlay_desc),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!hasOverlayPermission) {
          FilledTonalButton(
            onClick = {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}")
                ).apply {
                  flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
              }
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_grant_overlay_permission))
          }
        } else {
          OutlinedButton(
            onClick = {
              com.example.service.OverlayService.showOverlay(context, expandSettings = true)
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.btn_launch_overlay))
          }
        }
      }
    }

    // Instructions Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = stringResource(R.string.capture_guide_title),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer
        )

        StepItem(
          stepNumber = "1",
          title = stringResource(R.string.capture_guide_step1_title),
          description = stringResource(R.string.capture_guide_step1_desc)
        )
        StepItem(
          stepNumber = "2",
          title = stringResource(R.string.capture_guide_step2_title),
          description = stringResource(R.string.capture_guide_step2_desc)
        )
        StepItem(
          stepNumber = "3",
          title = stringResource(R.string.capture_guide_step3_title),
          description = stringResource(R.string.capture_guide_step3_desc)
        )
      }
    }

    // Start Capture Button
    Button(
      onClick = onStart,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("btn_start_screen_capture"),
      shape = RoundedCornerShape(16.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
      )
    ) {
      Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        stringResource(R.string.btn_start_capture),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun StepItem(
  stepNumber: String,
  title: String,
  description: String
) {
  Row(verticalAlignment = Alignment.Top) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = stepNumber,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimary,
          fontWeight = FontWeight.Bold
        )
      }
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
