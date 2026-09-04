package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.MotionEvent
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.service.AutoScrollAccessibilityService
import com.example.service.CaptureSessionState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberOverlayDragModifier(
  onGetPosition: () -> Pair<Int, Int>,
  onSetPosition: (x: Int, y: Int) -> Unit,
  onTap: (() -> Unit)? = null
): Modifier {
  var initialX by remember { mutableIntStateOf(0) }
  var initialY by remember { mutableIntStateOf(0) }
  var initialTouchX by remember { mutableFloatStateOf(0f) }
  var initialTouchY by remember { mutableFloatStateOf(0f) }
  var isDragging by remember { mutableStateOf(false) }
  val touchSlopPx = with(LocalDensity.current) { 10.dp.toPx() }

  return Modifier.pointerInteropFilter { motionEvent ->
    when (motionEvent.action) {
      MotionEvent.ACTION_DOWN -> {
        val (curX, curY) = onGetPosition()
        initialX = curX
        initialY = curY
        initialTouchX = motionEvent.rawX
        initialTouchY = motionEvent.rawY
        isDragging = false
        true
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = motionEvent.rawX - initialTouchX
        val dy = motionEvent.rawY - initialTouchY
        if (!isDragging && (kotlin.math.abs(dx) > touchSlopPx || kotlin.math.abs(dy) > touchSlopPx)) {
          isDragging = true
        }
        if (isDragging) {
          onSetPosition((initialX + dx).toInt(), (initialY + dy).toInt())
        }
        true
      }
      MotionEvent.ACTION_UP -> {
        if (!isDragging) {
          onTap?.invoke()
        }
        isDragging = false
        true
      }
      MotionEvent.ACTION_CANCEL -> {
        isDragging = false
        true
      }
      else -> false
    }
  }
}

@Composable
fun OverlayContentView(
  sessionState: CaptureSessionState,
  isExpanded: Boolean,
  onGetPosition: () -> Pair<Int, Int>,
  onSetPosition: (x: Int, y: Int) -> Unit,
  onToggleExpand: () -> Unit,
  onStartCapture: (intervalMs: Long, deduplicate: Boolean, autoScroll: Boolean, scrollSpeed: Float) -> Unit,
  onCaptureNow: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleAutoScroll: () -> Unit,
  onFinishAndStitch: () -> Unit,
  onCancelCapture: () -> Unit,
  onCloseOverlay: () -> Unit
) {
  val context = LocalContext.current

  if (isExpanded && !sessionState.isRunning) {
    // 1. Expanded Settings & Setup Panel
    OverlaySettingsCard(
      sessionState = sessionState,
      onGetPosition = onGetPosition,
      onSetPosition = onSetPosition,
      onCollapse = onToggleExpand,
      onStartCapture = onStartCapture,
      onCaptureNow = onCaptureNow,
      onClose = onCloseOverlay
    )
  } else {
    // 2. Compact Active / Quick Floating Pill
    OverlayFloatingPill(
      sessionState = sessionState,
      onGetPosition = onGetPosition,
      onSetPosition = onSetPosition,
      onToggleExpand = onToggleExpand,
      onCaptureNow = onCaptureNow,
      onTogglePause = onTogglePause,
      onToggleAutoScroll = onToggleAutoScroll,
      onFinishAndStitch = onFinishAndStitch,
      onCancelCapture = onCancelCapture,
      onCloseOverlay = onCloseOverlay
    )
  }
}

@Composable
private fun OverlayFloatingPill(
  sessionState: CaptureSessionState,
  onGetPosition: () -> Pair<Int, Int>,
  onSetPosition: (x: Int, y: Int) -> Unit,
  onToggleExpand: () -> Unit,
  onCaptureNow: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleAutoScroll: () -> Unit,
  onFinishAndStitch: () -> Unit,
  onCancelCapture: () -> Unit,
  onCloseOverlay: () -> Unit
) {
  val dragModifier = rememberOverlayDragModifier(onGetPosition, onSetPosition)
  val infiniteTransition = rememberInfiniteTransition(label = "pill_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
    tonalElevation = 10.dp,
    shadowElevation = 8.dp,
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (sessionState.isRunning) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = Modifier.padding(6.dp)
  ) {
    Row(
      modifier = Modifier
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Drag Handle / Grip Indicator (Draggable knob on the far left)
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
          .then(dragModifier),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.DragHandle,
          contentDescription = stringResource(R.string.overlay_drag_hint),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
      }

      if (sessionState.isRunning) {
        // Live Capture Indicator Badge
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (sessionState.isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .scale(if (!sessionState.isPaused) pulseScale else 1f)
                .background(
                  color = if (!sessionState.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                  shape = CircleShape
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.capture_count_badge, sessionState.capturedCount),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = if (sessionState.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }

        // Snap Now Button
        IconButton(
          onClick = onCaptureNow,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            Icons.Default.CameraAlt,
            contentDescription = stringResource(R.string.btn_capture_now_short),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }

        // Auto-Scroll Toggle Button
        IconButton(
          onClick = onToggleAutoScroll,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            Icons.Default.SwipeVertical,
            contentDescription = stringResource(R.string.capture_autoscroll_title),
            tint = if (sessionState.autoScrollEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }

        // Pause / Resume Button
        IconButton(
          onClick = onTogglePause,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            if (sessionState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            contentDescription = null,
            tint = if (sessionState.isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }

        // Finish & Stitch Button (Stop)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onFinishAndStitch() }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.Stop,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.btn_stitch_action_short),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }

        // Cancel / Discard Button
        IconButton(
          onClick = onCancelCapture,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.btn_discard_capture),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
          )
        }
      } else {
        // Idle State: Start Capture & Open Settings Buttons
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() }
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.ScreenShare,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.btn_start_capture),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }

        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            Icons.Default.Settings,
            contentDescription = stringResource(R.string.cd_settings),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }

        IconButton(
          onClick = onCloseOverlay,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.cd_close),
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun OverlaySettingsCard(
  sessionState: CaptureSessionState,
  onGetPosition: () -> Pair<Int, Int>,
  onSetPosition: (x: Int, y: Int) -> Unit,
  onCollapse: () -> Unit,
  onStartCapture: (intervalMs: Long, deduplicate: Boolean, autoScroll: Boolean, scrollSpeed: Float) -> Unit,
  onCaptureNow: () -> Unit,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val dragAndCollapseModifier = rememberOverlayDragModifier(onGetPosition, onSetPosition, onTap = onCollapse)
  var selectedIntervalSec by remember { mutableFloatStateOf(sessionState.intervalSeconds) }
  var autoDeduplicate by remember { mutableStateOf(sessionState.autoDeduplicate) }
  var autoScrollEnabled by remember { mutableStateOf(sessionState.autoScrollEnabled) }
  var scrollSpeedRatio by remember { mutableFloatStateOf(sessionState.scrollSpeedRatio) }

  val isAccessibilityOn = remember(autoScrollEnabled) {
    AutoScrollAccessibilityService.isAccessibilityServiceEnabled(context)
  }
  val intervalOptions = listOf(0.2f, 0.3f, 0.4f, 0.5f)

  ElevatedCard(
    modifier = Modifier
      .widthIn(max = 360.dp)
      .padding(10.dp),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.elevatedCardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top Drag Handle & Tap to Collapse Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .then(dragAndCollapseModifier)
          .padding(top = 2.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
      ) {
        Box(
          modifier = Modifier
            .width(40.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(2.5.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
      }

      // Header with Title (draggable & tappable to collapse) and Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Tappable and Draggable Title Area
        Row(
          modifier = Modifier
            .weight(1f)
            .then(dragAndCollapseModifier)
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Default.ScreenShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = stringResource(R.string.capture_sheet_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = stringResource(R.string.overlay_mode_badge),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        // Action Buttons: Collapse and Close
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onCollapse, modifier = Modifier.size(36.dp)) {
            Icon(
              Icons.Default.ExpandLess,
              contentDescription = stringResource(R.string.overlay_minimize),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(R.string.cd_close),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Auto-Scroll Switch
      Card(
        colors = CardDefaults.cardColors(
          containerColor = if (autoScrollEnabled) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Default.SwipeVertical,
                contentDescription = null,
                tint = if (autoScrollEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = stringResource(R.string.capture_autoscroll_title),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold
                )
                Text(
                  text = stringResource(R.string.capture_autoscroll_desc),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = autoScrollEnabled,
              onCheckedChange = { autoScrollEnabled = it }
            )
          }

          AnimatedVisibility(visible = autoScrollEnabled && !isAccessibilityOn) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(8.dp)
            ) {
              Text(
                text = stringResource(R.string.accessibility_needed_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
              )
              Spacer(modifier = Modifier.height(4.dp))
              FilledTonalButton(
                onClick = {
                  val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                  context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.btn_open_accessibility_settings), style = MaterialTheme.typography.labelSmall)
              }
            }
          }

          AnimatedVisibility(visible = autoScrollEnabled) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
              Text(
                text = stringResource(R.string.capture_scroll_distance_title),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(0.20f to "20%", 0.30f to "30%", 0.40f to "40% ★").forEach { (ratio, label) ->
                  val isSelected = scrollSpeedRatio == ratio
                  FilterChip(
                    selected = isSelected,
                    onClick = { scrollSpeedRatio = ratio },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
        }
      }

      // Interval Choice
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.capture_interval_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            intervalOptions.forEach { sec ->
              val isSelected = selectedIntervalSec == sec
              FilterChip(
                selected = isSelected,
                onClick = { selectedIntervalSec = sec },
                label = {
                  Text(
                    text = "${sec}s" + if (sec == 0.5f) " ★" else "",
                    style = MaterialTheme.typography.labelSmall
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

      // Smart Duplicate Filter
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.FilterNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(stringResource(R.string.capture_deduplicate_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
              Text(stringResource(R.string.capture_deduplicate_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          Spacer(modifier = Modifier.width(8.dp))
          Switch(
            checked = autoDeduplicate,
            onCheckedChange = { autoDeduplicate = it }
          )
        }
      }

      // Start Button
      Button(
        onClick = {
          val ms = (selectedIntervalSec * 1000).toLong()
          onStartCapture(ms, autoDeduplicate, autoScrollEnabled, scrollSpeedRatio)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_start_capture), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      }
    }
  }
}
