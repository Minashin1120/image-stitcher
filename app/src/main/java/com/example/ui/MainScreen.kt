package com.example.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.SeamConfig
import com.example.model.StitchUiState
import com.example.ui.components.ImageItemCard
import com.example.ui.components.ResultView
import com.example.ui.components.SeamBadge
import com.example.ui.components.SeamFineTuneDialog
import com.example.ui.components.SettingsBottomSheet
import com.example.viewmodel.StitchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: StitchViewModel) {
  val context = LocalContext.current
  val images by viewModel.images.collectAsStateWithLifecycle()
  val seams by viewModel.seams.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  var showSettingsSheet by remember { mutableStateOf(false) }
  val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var activeFineTunePairIndex by remember { mutableStateOf<Int?>(null) }
  var activeEditingImageIndex by remember { mutableStateOf<Int?>(null) }
  var standaloneEditingUri by remember { mutableStateOf<android.net.Uri?>(null) }

  // Modern Android Photo Picker contract (Multiple images for stitching)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
  ) { uris ->
    if (uris.isNotEmpty()) {
      viewModel.addImages(uris)
    }
  }

  // Single Photo Picker contract (for standalone image editing)
  val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      standaloneEditingUri = uri
    }
  }

  // Toast / Snackbar notification
  LaunchedEffect(userMessage) {
    userMessage?.let { msg ->
      Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
      viewModel.clearUserMessage()
    }
  }

  // If in Success state, show full ResultView screen
  if (uiState is StitchUiState.Success) {
    val result = (uiState as StitchUiState.Success).result
    ResultView(
      result = result,
      onBack = { viewModel.resetToEdit() },
      onSaveToGallery = { viewModel.saveToGallery(result) },
      onShare = {
        val shareIntent = viewModel.createShareIntent(context, result)
        context.startActivity(
          android.content.Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_chooser_title)
          )
        )
      },
      onResultUpdated = { updated ->
        viewModel.updateStitchResult(updated)
      }
    )
    return
  }

  // If editing an arbitrary standalone image picked from the home screen
  standaloneEditingUri?.let { standAloneUri ->
    val tempFile = remember(standAloneUri) {
      val f = File(context.cacheDir, "temp_standalone_edit_${System.currentTimeMillis()}.png")
      try {
        context.contentResolver.openInputStream(standAloneUri)?.use { inp ->
          f.outputStream().use { out -> inp.copyTo(out) }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
      f
    }

    com.example.ui.components.ImageEditorScreen(
      sourceFile = tempFile,
      fullBitmapLoader = {
        com.example.engine.StitchEngine.loadFullBitmap(context, standAloneUri, maxWidth = 0)
      },
      onDismiss = { standaloneEditingUri = null },
      onEditsApplied = { outFile, newWidth, newHeight, newSizeBytes ->
        val result = com.example.model.StitchResult(
          file = outFile,
          uri = android.net.Uri.fromFile(outFile),
          width = newWidth,
          height = newHeight,
          fileSizeBytes = newSizeBytes,
          sourceCount = 1
        )
        viewModel.updateStitchResult(result)
        standaloneEditingUri = null
      }
    )
    return
  }

  // If editing an individual image item from the queue
  activeEditingImageIndex?.let { editIdx ->
    if (editIdx in images.indices) {
      val itemToEdit = images[editIdx]
      // Create a temporary cache file if URI is content:// or read directly
      val tempFile = remember(itemToEdit.id) {
        val f = File(context.cacheDir, "temp_edit_${itemToEdit.id}.png")
        try {
          context.contentResolver.openInputStream(itemToEdit.uri)?.use { inp ->
            f.outputStream().use { out -> inp.copyTo(out) }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
        f
      }

      com.example.ui.components.ImageEditorScreen(
        sourceFile = tempFile,
        fullBitmapLoader = {
          com.example.engine.StitchEngine.loadFullBitmap(context, itemToEdit.uri, maxWidth = 0)
        },
        onDismiss = { activeEditingImageIndex = null },
        onEditsApplied = { outFile, newWidth, newHeight, newSizeBytes ->
          val newThumb = android.graphics.BitmapFactory.decodeFile(outFile.absolutePath)
          viewModel.updateImageItem(
            index = editIdx,
            newUri = android.net.Uri.fromFile(outFile),
            newWidth = newWidth,
            newHeight = newHeight,
            newSizeBytes = newSizeBytes,
            newThumbnail = newThumb
          )
          activeEditingImageIndex = null
        }
      )
      return
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Layers,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = stringResource(R.string.title_app),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              singlePhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.testTag("btn_home_edit_image")
          ) {
            Icon(
              Icons.Default.Edit,
              contentDescription = stringResource(R.string.cd_edit_single_image)
            )
          }
          if (images.isNotEmpty()) {
            IconButton(
              onClick = { viewModel.autoDetectAllSeams() },
              modifier = Modifier.testTag("btn_redetect")
            ) {
              Icon(
                Icons.Default.AutoAwesome,
                contentDescription = stringResource(R.string.cd_auto_detect)
              )
            }
            IconButton(
              onClick = { viewModel.clearImages() },
              modifier = Modifier.testTag("btn_clear_all")
            ) {
              Icon(
                Icons.Default.Clear,
                contentDescription = stringResource(R.string.cd_clear_all)
              )
            }
          }
          IconButton(
            onClick = { showSettingsSheet = true },
            modifier = Modifier.testTag("btn_open_settings")
          ) {
            Icon(
              Icons.Default.Settings,
              contentDescription = stringResource(R.string.cd_settings)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      if (images.size >= 2) {
        Surface(
          tonalElevation = 6.dp,
          color = MaterialTheme.colorScheme.surface
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .testTag("btn_add_more_bottom"),
              shape = RoundedCornerShape(14.dp)
            ) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.btn_add_more))
            }

            Button(
              onClick = { viewModel.stitchNow() },
              modifier = Modifier
                .weight(1.6f)
                .height(52.dp)
                .testTag("btn_stitch_now"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
              )
            ) {
              Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                stringResource(R.string.btn_stitch_now, images.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
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
    ) {
      if (images.isEmpty()) {
        // Empty State Screen
        EmptyStateView(
          onPickImages = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onPickSingleImageToEdit = {
            singlePhotoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          }
        )
      } else {
        // Image List with Seam Connectors
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          contentPadding = PaddingValues(vertical = 12.dp)
        ) {
          // Status Header
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
              ),
              shape = RoundedCornerShape(14.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    Icons.Default.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = stringResource(R.string.header_queued_count, images.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                  )
                }

                TextButton(
                  onClick = {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  modifier = Modifier.testTag("btn_add_more_header")
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(stringResource(R.string.btn_add))
                }
              }
            }
          }

          itemsIndexed(images, key = { _, item -> item.id }) { index, item ->
            ImageItemCard(
              index = index,
              totalCount = images.size,
              item = item,
              onEdit = { activeEditingImageIndex = index },
              onMoveUp = { viewModel.moveImage(index, index - 1) },
              onMoveDown = { viewModel.moveImage(index, index + 1) },
              onRemove = { viewModel.removeImage(index) }
            )

            // Show Seam Badge connector between pairs
            if (index < images.size - 1) {
              val seam = seams.getOrNull(index) ?: SeamConfig()
              SeamBadge(
                index = index,
                seam = seam,
                onOpenFineTune = { activeFineTunePairIndex = index }
              )
            }
          }

          // Single image hint if user only picked 1
          if (images.size == 1) {
            item {
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
              ) {
                Row(
                  modifier = Modifier.padding(16.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text = stringResource(R.string.hint_single_image),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                  )
                }
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(80.dp))
          }
        }
      }

      // Processing / Detecting Dialog
      when (val state = uiState) {
        is StitchUiState.Detecting -> {
          Surface(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 90.dp, start = 20.dp, end = 20.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            tonalElevation = 6.dp
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.inversePrimary,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(14.dp))
              Text(
                text = stringResource(
                  R.string.detecting_pair_progress,
                  state.currentPair,
                  state.totalPairs
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface
              )
            }
          }
        }

        is StitchUiState.Stitching -> {
          AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
              Text(
                stringResource(R.string.stitching_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            },
            text = {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                LinearProgressIndicator(
                  progress = { state.progress },
                  modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = state.statusMessage,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          )
        }

        is StitchUiState.Error -> {
          AlertDialog(
            onDismissRequest = { viewModel.resetToEdit() },
            title = { Text(stringResource(R.string.stitch_error_title)) },
            text = { Text(state.message) },
            confirmButton = {
              Button(onClick = { viewModel.resetToEdit() }) {
                Text(stringResource(R.string.btn_ok))
              }
            }
          )
        }

        else -> {}
      }
    }
  }

  // Fine-Tune Seam Dialog
  activeFineTunePairIndex?.let { pairIdx ->
    if (pairIdx in 0 until images.size - 1) {
      val topImg = images[pairIdx]
      val bottomImg = images[pairIdx + 1]
      val seam = seams.getOrNull(pairIdx) ?: SeamConfig()

      SeamFineTuneDialog(
        pairIndex = pairIdx,
        topImage = topImg,
        bottomImage = bottomImg,
        seam = seam,
        onDismiss = { activeFineTunePairIndex = null },
        onSaveSeam = { updated ->
          viewModel.updateSeam(pairIdx, updated)
        }
      )
    }
  }

  // Settings Bottom Sheet
  if (showSettingsSheet) {
    SettingsBottomSheet(
      sheetState = settingsSheetState,
      settings = settings,
      onUpdateSettings = { viewModel.updateSettings(it) },
      onDismiss = { showSettingsSheet = false }
    )
  }
}

@Composable
private fun EmptyStateView(
  onPickImages: () -> Unit,
  onPickSingleImageToEdit: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // Hero Illustration Icon Box
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(100.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          Icons.Default.AddPhotoAlternate,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(48.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.empty_title),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = stringResource(R.string.empty_description),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    Button(
      onClick = onPickImages,
      modifier = Modifier
        .fillMaxWidth()
        .height(54.dp)
        .testTag("btn_pick_screenshots_main"),
      shape = RoundedCornerShape(16.dp)
    ) {
      Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        stringResource(R.string.btn_select_screenshots),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = onPickSingleImageToEdit,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("btn_empty_edit_single_image"),
      shape = RoundedCornerShape(16.dp)
    ) {
      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        stringResource(R.string.btn_select_single_image_to_edit),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Feature Highlights Row
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      ),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FeatureHintItem(
          icon = Icons.Default.AutoAwesome,
          text = stringResource(R.string.feature_auto_detect)
        )
        FeatureHintItem(
          icon = Icons.Default.AutoFixHigh,
          text = stringResource(R.string.feature_trim_bars)
        )
        FeatureHintItem(
          icon = Icons.Default.TouchApp,
          text = stringResource(R.string.feature_fine_tune)
        )
      }
    }
  }
}

@Composable
private fun FeatureHintItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(18.dp)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
