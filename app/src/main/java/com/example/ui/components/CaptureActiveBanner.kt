package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.service.CaptureSessionState

@Composable
fun CaptureActiveBanner(
  sessionState: CaptureSessionState,
  onClickBanner: () -> Unit,
  onCaptureNow: () -> Unit,
  onTogglePause: () -> Unit,
  onToggleAutoScroll: () -> Unit = {},
  onFinishAndStitch: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "banner_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "banner_pulse"
  )

  AnimatedVisibility(
    visible = sessionState.isRunning,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .clickable { onClickBanner() }
        .testTag("banner_capture_active"),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          // Pulse dot
          Box(
            modifier = Modifier
              .size(12.dp)
              .scale(if (!sessionState.isPaused) pulseScale else 1f)
              .background(
                color = if (!sessionState.isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                shape = CircleShape
              )
          )
          Spacer(modifier = Modifier.width(10.dp))

          sessionState.lastCapturedThumbnail?.let { thumb ->
            Image(
              bitmap = thumb.asImageBitmap(),
              contentDescription = null,
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp)),
              contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
          }

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = if (sessionState.isPaused) {
                  stringResource(R.string.capture_state_paused)
                } else {
                  stringResource(R.string.banner_capturing_title, sessionState.intervalSeconds)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
              )
              if (sessionState.autoScrollEnabled) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  Icons.Default.SwipeVertical,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.onErrorContainer
                )
              }
            }
            Text(
              text = stringResource(R.string.banner_capturing_count, sessionState.capturedCount),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onCaptureNow,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.CameraAlt,
              contentDescription = stringResource(R.string.btn_capture_now_short),
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(20.dp)
            )
          }

          IconButton(
            onClick = onToggleAutoScroll,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              Icons.Default.SwipeVertical,
              contentDescription = null,
              tint = if (sessionState.autoScrollEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f),
              modifier = Modifier.size(20.dp)
            )
          }

          IconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              if (sessionState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          Button(
            onClick = onFinishAndStitch,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.btn_stitch_action_short),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }
  }
}
