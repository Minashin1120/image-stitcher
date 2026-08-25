package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SeamConfig
import kotlin.math.roundToInt

@Composable
fun SeamBadge(
  index: Int,
  seam: SeamConfig,
  onOpenFineTune: () -> Unit
) {
  val hasConfidence = seam.confidence >= 0.70f
  val hasOverlap = seam.totalOverlap > 0

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Vertical connection line
    Box(
      modifier = Modifier
        .width(2.dp)
        .height(10.dp)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    )

    Surface(
      shape = RoundedCornerShape(20.dp),
      color = if (hasOverlap && hasConfidence) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
      } else if (hasOverlap) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
      } else {
        MaterialTheme.colorScheme.surfaceVariant
      },
      tonalElevation = 1.dp,
      modifier = Modifier.testTag("seam_badge_$index")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          if (hasConfidence) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
          contentDescription = null,
          tint = if (hasConfidence) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.size(16.dp)
        )

        Column {
          Text(
            text = if (hasOverlap) {
              stringResource(R.string.seam_overlap_px, seam.totalOverlap)
            } else {
              stringResource(R.string.seam_no_overlap)
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          if (seam.isAutoDetected && hasOverlap) {
            val confidenceText = stringResource(
              R.string.seam_match_confidence,
              (seam.confidence * 100).roundToInt()
            )
            val manualOffsetSuffix = if (seam.manualOffset != 0) {
              val offsetStr = if (seam.manualOffset > 0) "+${seam.manualOffset}" else "${seam.manualOffset}"
              stringResource(R.string.seam_manual_offset_suffix, offsetStr)
            } else ""
            Text(
              text = "$confidenceText$manualOffsetSuffix",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        FilledTonalButton(
          onClick = onOpenFineTune,
          modifier = Modifier.height(32.dp).testTag("btn_adjust_seam_$index"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(stringResource(R.string.btn_adjust), style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Bottom vertical connection line
    Box(
      modifier = Modifier
        .width(2.dp)
        .height(10.dp)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    )
  }
}
