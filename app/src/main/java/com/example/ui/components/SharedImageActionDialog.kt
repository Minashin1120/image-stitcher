package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedImageActionDialog(
  sheetState: SheetState,
  sharedUris: List<Uri>,
  onAddToStitch: (List<Uri>) -> Unit,
  onEditSingle: (Uri) -> Unit,
  onDismiss: () -> Unit
) {
  if (sharedUris.isEmpty()) return

  val isSingle = sharedUris.size == 1
  var selectedIndexForEdit by remember { mutableIntStateOf(0) }
  val context = LocalContext.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 6.dp,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    dragHandle = {
      Surface(
        modifier = Modifier
          .padding(vertical = 12.dp)
          .size(width = 36.dp, height = 4.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.outlineVariant
      ) {}
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Header Section
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f)
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
              )
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = if (isSingle) {
                stringResource(R.string.shared_images_title_single)
              } else {
                stringResource(R.string.shared_images_title_multiple, sharedUris.size)
              },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = stringResource(R.string.shared_images_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("btn_close_shared_sheet")
        ) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.cd_close)
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Thumbnail Preview Area
      if (isSingle) {
        // Single Image Preview Card
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
        ) {
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(sharedUris[0])
                .crossfade(true)
                .build(),
              contentDescription = stringResource(R.string.cd_shared_image_thumb, 1),
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
            )
          }
        }
      } else {
        // Multiple Images Carousel Preview
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = stringResource(R.string.shared_tap_image_to_edit_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
          )

          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
          ) {
            itemsIndexed(sharedUris) { index, uri ->
              val isSelected = index == selectedIndexForEdit
              val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
              val borderWidth = if (isSelected) 3.dp else 1.dp

              Box(
                modifier = Modifier
                  .size(100.dp, 130.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant)
                  .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
                  .clickable { selectedIndexForEdit = index }
                  .testTag("shared_thumb_item_$index"),
                contentAlignment = Alignment.Center
              ) {
                AsyncImage(
                  model = ImageRequest.Builder(context)
                    .data(uri)
                    .crossfade(true)
                    .build(),
                  contentDescription = stringResource(R.string.cd_shared_image_thumb, index + 1),
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.matchParentSize()
                )

                // Index Badge (Top Left)
                Surface(
                  shape = CircleShape,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                  modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = "${index + 1}",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                  }
                }

                // Check indicator if selected
                if (isSelected) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(6.dp)
                      .size(22.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Action Buttons
      // Primary Stitch Action
      Button(
        onClick = { onAddToStitch(sharedUris) },
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("btn_shared_stitch_action"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.primary
        )
      ) {
        Icon(
          if (isSingle) Icons.Default.Layers else Icons.Default.AutoFixHigh,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = if (isSingle) {
            stringResource(R.string.btn_shared_action_stitch_single)
          } else {
            stringResource(R.string.btn_shared_action_stitch_all, sharedUris.size)
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Secondary Edit Action
      FilledTonalButton(
        onClick = {
          val targetUri = if (isSingle) sharedUris[0] else sharedUris[selectedIndexForEdit.coerceIn(0, sharedUris.lastIndex)]
          onEditSingle(targetUri)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("btn_shared_edit_action"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
      ) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = if (isSingle) {
            stringResource(R.string.btn_shared_action_edit)
          } else {
            stringResource(R.string.btn_shared_action_edit_index, selectedIndexForEdit + 1)
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("btn_shared_cancel")
      ) {
        Text(
          text = stringResource(R.string.btn_cancel),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
