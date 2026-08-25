package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ImageItem

@Composable
fun ImageItemCard(
  index: Int,
  totalCount: Int,
  item: ImageItem,
  onEdit: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRemove: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("image_item_card_$index"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Index Badge & Thumbnail
      Box(
        modifier = Modifier
          .size(64.dp, 84.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
      ) {
        if (item.thumbnail != null) {
          Image(
            bitmap = item.thumbnail.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_screenshot_item, index + 1),
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
          )
        } else {
          Icon(
            Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
          )
        }

        // Overlay index badge
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(4.dp)
            .size(22.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = "${index + 1}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      // Metadata Info
      Column(
        modifier = Modifier.weight(1f)
      ) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = if (item.width > 0 && item.height > 0) {
            "${item.width} × ${item.height} px"
          } else {
            stringResource(R.string.screenshot_default_name)
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (item.fileSizeBytes > 0) {
          Text(
            text = formatFileSize(item.fileSizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
          )
        }
      }

      // Re-order, edit & delete actions
      Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onEdit,
          modifier = Modifier.size(36.dp).testTag("btn_edit_$index")
        ) {
          Icon(
            Icons.Default.Edit,
            contentDescription = stringResource(R.string.cd_edit_image),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }

        if (index > 0) {
          IconButton(
            onClick = onMoveUp,
            modifier = Modifier.size(36.dp).testTag("btn_move_up_$index")
          ) {
            Icon(
              Icons.Default.ArrowUpward,
              contentDescription = stringResource(R.string.cd_move_up),
              modifier = Modifier.size(20.dp)
            )
          }
        }

        if (index < totalCount - 1) {
          IconButton(
            onClick = onMoveDown,
            modifier = Modifier.size(36.dp).testTag("btn_move_down_$index")
          ) {
            Icon(
              Icons.Default.ArrowDownward,
              contentDescription = stringResource(R.string.cd_move_down),
              modifier = Modifier.size(20.dp)
            )
          }
        }

        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(36.dp).testTag("btn_remove_$index")
        ) {
          Icon(
            Icons.Default.DeleteOutline,
            contentDescription = stringResource(R.string.cd_remove),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  return when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> String.format("%.0f KB", bytes.toDouble() / 1024)
    else -> "$bytes B"
  }
}
