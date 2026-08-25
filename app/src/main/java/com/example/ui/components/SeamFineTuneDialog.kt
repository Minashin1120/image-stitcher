package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ImageItem
import com.example.model.SeamConfig
import kotlin.math.roundToInt

@Composable
fun SeamFineTuneDialog(
  pairIndex: Int,
  topImage: ImageItem,
  bottomImage: ImageItem,
  seam: SeamConfig,
  onDismiss: () -> Unit,
  onSaveSeam: (SeamConfig) -> Unit
) {
  var manualOffset by remember { mutableIntStateOf(seam.manualOffset) }
  var blendOpacity by remember { mutableFloatStateOf(0.5f) }
  var isOverlayMode by remember { mutableStateOf(true) }

  val totalOverlap = (seam.autoOverlap + manualOffset).coerceAtLeast(0)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column {
          Text(
            text = stringResource(R.string.fine_tune_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = stringResource(
              R.string.fine_tune_pair_subtitle,
              pairIndex + 1,
              topImage.name,
              bottomImage.name
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_close_button")) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.cd_close)
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Visual Seam Preview Box
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
          color = MaterialTheme.colorScheme.surfaceVariant
        ) {
          Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // Top Image slice
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = stringResource(R.string.fine_tune_top_slice),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (topImage.thumbnail != null) {
                  Image(
                    bitmap = topImage.thumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_top_preview),
                    modifier = Modifier
                      .height(140.dp)
                      .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .height(140.dp)
                      .fillMaxWidth()
                      .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      stringResource(R.string.no_preview),
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
              }

              // Overlap Connector Indicator
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.CompareArrows,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primaryContainer
                ) {
                  Text(
                    text = "${totalOverlap}px",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              // Bottom Image slice
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
              ) {
                Text(
                  text = stringResource(R.string.fine_tune_bottom_slice),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.secondary,
                  fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (bottomImage.thumbnail != null) {
                  Image(
                    bitmap = bottomImage.thumbnail.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_bottom_preview),
                    modifier = Modifier
                      .height(140.dp)
                      .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .height(140.dp)
                      .fillMaxWidth()
                      .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      stringResource(R.string.no_preview),
                      style = MaterialTheme.typography.bodySmall
                    )
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Info Badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(
                R.string.fine_tune_auto_detected,
                seam.autoOverlap,
                (seam.confidence * 100).roundToInt()
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Text(
            text = stringResource(
              R.string.fine_tune_offset_label,
              if (manualOffset >= 0) "+$manualOffset" else "$manualOffset"
            ),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (manualOffset != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pixel Nudge Controls
        Text(
          text = stringResource(R.string.fine_tune_adjust_heading),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledTonalButton(
            onClick = { manualOffset -= 10 },
            modifier = Modifier.testTag("btn_minus_10")
          ) {
            Text("-10")
          }
          FilledTonalButton(
            onClick = { manualOffset -= 1 },
            modifier = Modifier.testTag("btn_minus_1")
          ) {
            Text("-1")
          }
          IconButton(
            onClick = { manualOffset = 0 },
            modifier = Modifier.testTag("btn_reset_offset")
          ) {
            Icon(
              Icons.Default.RestartAlt,
              contentDescription = stringResource(R.string.cd_reset_offset)
            )
          }
          FilledTonalButton(
            onClick = { manualOffset += 1 },
            modifier = Modifier.testTag("btn_plus_1")
          ) {
            Text("+1")
          }
          FilledTonalButton(
            onClick = { manualOffset += 10 },
            modifier = Modifier.testTag("btn_plus_10")
          ) {
            Text("+10")
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSaveSeam(seam.copy(manualOffset = manualOffset))
          onDismiss()
        },
        modifier = Modifier.testTag("btn_save_seam")
      ) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.btn_apply))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.testTag("btn_cancel_seam")) {
        Text(stringResource(R.string.btn_cancel))
      }
    }
  )
}
