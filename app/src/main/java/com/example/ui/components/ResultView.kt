package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.StitchResult
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultView(
  result: StitchResult,
  onBack: () -> Unit,
  onSaveToGallery: () -> Unit,
  onShare: () -> Unit,
  onResultUpdated: (StitchResult) -> Unit = {}
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }
  var isEditing by remember { mutableStateOf(false) }

  if (isEditing) {
    ImageEditorScreen(
      sourceFile = result.file,
      fullBitmapLoader = {
        try {
          FileInputStream(result.file).use { stream ->
            BitmapFactory.decodeStream(stream)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          null
        }
      },
      onDismiss = { isEditing = false },
      onEditsApplied = { outFile, newWidth, newHeight, newSizeBytes ->
        val updatedResult = result.copy(
          uri = Uri.fromFile(outFile),
          file = outFile,
          width = newWidth,
          height = newHeight,
          fileSizeBytes = newSizeBytes
        )
        onResultUpdated(updatedResult)
        isEditing = false
      }
    )
    return
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              if (result.sourceCount > 1) stringResource(R.string.result_title) else stringResource(R.string.editor_title),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              if (result.sourceCount > 1) {
                stringResource(
                  R.string.result_subtitle,
                  result.width,
                  result.height,
                  result.sourceCount
                )
              } else {
                stringResource(
                  R.string.single_image_edit_result_subtitle,
                  result.width,
                  result.height
                )
              },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("result_back_button")) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.cd_back_to_editor)
            )
          }
        },
        actions = {
          IconButton(
            onClick = { isEditing = true },
            modifier = Modifier.testTag("btn_open_image_editor")
          ) {
            Icon(
              Icons.Default.Edit,
              contentDescription = stringResource(R.string.cd_edit_image)
            )
          }
          IconButton(
            onClick = {
              scale = 1f
              offset = Offset.Zero
            },
            modifier = Modifier.testTag("btn_reset_zoom")
          ) {
            Icon(
              Icons.Default.RestartAlt,
              contentDescription = stringResource(R.string.cd_reset_zoom)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          // Info Chips Row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Text(
                text = "${result.width} × ${result.height} px",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.secondaryContainer
            ) {
              Text(
                text = formatFileSize(result.fileSizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
            if (result.totalOverlapRemoved > 0) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
              ) {
                Text(
                  text = stringResource(R.string.result_overlap_merged, result.totalOverlapRemoved),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = { isEditing = true },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("btn_bottom_edit_result"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.btn_edit))
            }

            FilledTonalButton(
              onClick = onShare,
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("btn_share_result"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.btn_share))
            }

            Button(
              onClick = onSaveToGallery,
              modifier = Modifier
                .weight(1.3f)
                .height(48.dp)
                .testTag("btn_save_gallery"),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(R.string.btn_save_to_gallery))
            }
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .pointerInput(Unit) {
          detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(0.5f, 6f)
            offset = Offset(
              x = offset.x + pan.x,
              y = offset.y + pan.y
            )
          }
        },
      contentAlignment = Alignment.Center
    ) {
      AsyncImage(
        model = result.file,
        contentDescription = stringResource(R.string.cd_stitched_long_screenshot),
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y
          ),
        contentScale = ContentScale.Fit
      )
    }
  }
}

private fun formatFileSize(bytes: Long): String {
  return when {
    bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> String.format("%.0f KB", bytes.toDouble() / 1024)
    else -> "$bytes B"
  }
}

