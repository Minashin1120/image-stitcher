package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.engine.ImageEditEngine
import com.example.model.CropBounds
import com.example.model.EditAction
import com.example.model.EditTool
import com.example.model.MosaicMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PRESET_COLORS = listOf(
  Color(0xFFE53935), // Red
  Color(0xFFFFEB3B), // Yellow
  Color(0xFF4CAF50), // Green
  Color(0xFF2196F3), // Blue
  Color(0xFFFF4081), // Pink
  Color(0xFFFF9800), // Orange
  Color(0xFF9C27B0), // Purple
  Color(0xFF00E5FF), // Cyan
  Color(0xFFFFFFFF), // White
  Color(0xFF212121)  // Dark
)

private val HIGHLIGHTER_COLORS = listOf(
  Color(0xFFFFFF00), // Fluorescent Yellow
  Color(0xFF00FF66), // Fluorescent Green
  Color(0xFFFF3399), // Fluorescent Pink
  Color(0xFF00E5FF), // Fluorescent Cyan
  Color(0xFFFF9900)  // Fluorescent Orange
)

private enum class CropHandle {
  TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
  TOP, BOTTOM, LEFT, RIGHT, INSIDE
}

private data class PixelBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class MosaicIntensityOption(
  val labelRes: Int,
  val pixelSizeDp: Float
)

private val MOSAIC_INTENSITY_OPTIONS = listOf(
  MosaicIntensityOption(R.string.mosaic_intensity_light, 12f),
  MosaicIntensityOption(R.string.mosaic_intensity_medium, 24f),
  MosaicIntensityOption(R.string.mosaic_intensity_strong, 42f),
  MosaicIntensityOption(R.string.mosaic_intensity_extra, 68f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
  sourceFile: File,
  fullBitmapLoader: suspend () -> Bitmap?,
  onDismiss: () -> Unit,
  onEditsApplied: (File, Int, Int, Long) -> Unit
) {
  val context = LocalContext.current
  val density = LocalDensity.current.density
  val coroutineScope = rememberCoroutineScope()

  var isProcessing by remember { mutableStateOf(false) }
  var showDiscardDialog by remember { mutableStateOf(false) }

  // Tool state
  var selectedTool by remember { mutableStateOf(EditTool.MARKER) }
  var mosaicMode by remember { mutableStateOf(MosaicMode.PEN) }
  var selectedColor by remember { mutableStateOf(Color(0xFFE53935)) }
  var selectedHighlighterColor by remember { mutableStateOf(Color(0xFFFFFF00)) }
  var strokeWidthDp by remember { mutableFloatStateOf(14f) }
  var mosaicPixelSizeDp by remember { mutableFloatStateOf(24f) }

  // Action history
  val actionHistory = remember { mutableStateListOf<EditAction>() }
  val redoStack = remember { mutableStateListOf<EditAction>() }
  var cropBounds by remember { mutableStateOf(CropBounds()) }

  // Selected mosaic for tap-to-delete & intensity modification
  var selectedMosaicId by remember { mutableStateOf<String?>(null) }

  // Active gesture drawing states
  var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
  var currentMosaicRectStart by remember { mutableStateOf<Offset?>(null) }
  var currentMosaicRectEnd by remember { mutableStateOf<Offset?>(null) }

  // Cropping handle drag states
  var activeCropHandle by remember { mutableStateOf<CropHandle?>(null) }
  var cropDragStartBounds by remember { mutableStateOf(CropBounds()) }
  var cropDragStartPoint by remember { mutableStateOf(Offset.Zero) }

  // Loaded full Bitmap reference for rendering & saving
  var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(sourceFile) {
    withContext(Dispatchers.IO) {
      loadedBitmap = fullBitmapLoader()
    }
  }

  val hasEdits = actionHistory.isNotEmpty() || !cropBounds.isDefault

  BackHandler(enabled = true) {
    if (showDiscardDialog) {
      showDiscardDialog = false
    } else if (hasEdits) {
      showDiscardDialog = true
    } else {
      onDismiss()
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(R.string.editor_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              if (hasEdits) {
                showDiscardDialog = true
              } else {
                onDismiss()
              }
            },
            modifier = Modifier.testTag("btn_editor_close")
          ) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(R.string.cd_close)
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              if (actionHistory.isNotEmpty()) {
                val last = actionHistory.removeAt(actionHistory.size - 1)
                redoStack.add(last)
                if (selectedMosaicId == last.id) {
                  selectedMosaicId = null
                }
              }
            },
            enabled = actionHistory.isNotEmpty(),
            modifier = Modifier.testTag("btn_editor_undo")
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Undo,
              contentDescription = stringResource(R.string.btn_undo)
            )
          }

          IconButton(
            onClick = {
              if (redoStack.isNotEmpty()) {
                val next = redoStack.removeAt(redoStack.size - 1)
                actionHistory.add(next)
              }
            },
            enabled = redoStack.isNotEmpty(),
            modifier = Modifier.testTag("btn_editor_redo")
          ) {
            Icon(
              Icons.AutoMirrored.Filled.Redo,
              contentDescription = stringResource(R.string.btn_redo)
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          Button(
            onClick = {
              val bmp = loadedBitmap
              if (bmp == null) {
                onDismiss()
                return@Button
              }
              isProcessing = true
              coroutineScope.launch {
                val editedBitmap = ImageEditEngine.applyEdits(
                  sourceBitmap = bmp,
                  actions = actionHistory.toList(),
                  cropBounds = cropBounds
                )

                val editDir = File(context.cacheDir, "edited").apply { mkdirs() }
                val outFile = File(editDir, "edited_${System.currentTimeMillis()}.png")

                val success = ImageEditEngine.saveBitmapToFile(
                  bitmap = editedBitmap,
                  outputFile = outFile,
                  format = Bitmap.CompressFormat.PNG,
                  quality = 100
                )

                isProcessing = false
                if (success) {
                  onEditsApplied(
                    outFile,
                    editedBitmap.width,
                    editedBitmap.height,
                    outFile.length()
                  )
                } else {
                  onDismiss()
                }
              }
            },
            enabled = !isProcessing,
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("btn_editor_done"),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.btn_done))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    bottomBar = {
      Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
          when (selectedTool) {
            EditTool.MARKER -> {
              ColorPaletteRow(
                colors = PRESET_COLORS,
                selectedColor = selectedColor,
                onColorSelected = { selectedColor = it }
              )
              Spacer(modifier = Modifier.height(8.dp))
              BrushSizeRow(
                currentWidthDp = strokeWidthDp,
                onWidthSelected = { strokeWidthDp = it }
              )
            }

            EditTool.HIGHLIGHTER -> {
              ColorPaletteRow(
                colors = HIGHLIGHTER_COLORS,
                selectedColor = selectedHighlighterColor,
                onColorSelected = { selectedHighlighterColor = it }
              )
              Spacer(modifier = Modifier.height(8.dp))
              BrushSizeRow(
                currentWidthDp = strokeWidthDp,
                onWidthSelected = { strokeWidthDp = it }
              )
            }

            EditTool.MOSAIC -> {
              val selectedMosaicAction = actionHistory.find { it.id == selectedMosaicId }
              if (selectedMosaicAction != null) {
                // When an existing mosaic is selected, allow deleting it or updating its intensity & pen size
                Column(modifier = Modifier.fillMaxWidth()) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(
                        Icons.Default.GridOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                      )
                      Text(
                        text = stringResource(R.string.hint_mosaic_selected),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      OutlinedButton(
                        onClick = { selectedMosaicId = null },
                        shape = RoundedCornerShape(8.dp)
                      ) {
                        Text(stringResource(R.string.cd_close))
                      }
                      Button(
                        onClick = {
                          val idx = actionHistory.indexOfFirst { it.id == selectedMosaicId }
                          if (idx != -1) {
                            val removed = actionHistory.removeAt(idx)
                            redoStack.add(removed)
                          }
                          selectedMosaicId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_delete_selected_mosaic_bar")
                      ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_delete_mosaic))
                      }
                    }
                  }

                  // Determine current mosaic intensity from the action
                  val currentActionPixelSizeRel = when (selectedMosaicAction) {
                    is EditAction.MosaicRect -> selectedMosaicAction.pixelSizeRelative
                    is EditAction.MosaicPen -> selectedMosaicAction.pixelSizeRelative
                    else -> 0.05f
                  }

                  MosaicIntensitySelectorRow(
                    currentPixelSizeRel = currentActionPixelSizeRel,
                    onIntensitySelected = { newPixelSizeDp ->
                      mosaicPixelSizeDp = newPixelSizeDp
                      val idx = actionHistory.indexOfFirst { it.id == selectedMosaicId }
                      if (idx != -1) {
                        val act = actionHistory[idx]
                        val bmpW = (loadedBitmap?.width ?: 1000).toFloat()
                        val newRel = (newPixelSizeDp * density) / bmpW
                        val updated = when (act) {
                          is EditAction.MosaicRect -> act.copy(pixelSizeRelative = newRel)
                          is EditAction.MosaicPen -> act.copy(pixelSizeRelative = newRel)
                          else -> act
                        }
                        actionHistory[idx] = updated
                      }
                    }
                  )

                  if (selectedMosaicAction is EditAction.MosaicPen) {
                    Spacer(modifier = Modifier.height(6.dp))
                    BrushSizeRow(
                      currentWidthDp = strokeWidthDp,
                      label = stringResource(R.string.label_pen_size),
                      onWidthSelected = { newWidthDp ->
                        strokeWidthDp = newWidthDp
                        val idx = actionHistory.indexOfFirst { it.id == selectedMosaicId }
                        if (idx != -1) {
                          val act = actionHistory[idx]
                          if (act is EditAction.MosaicPen) {
                            val bmpW = (loadedBitmap?.width ?: 1000).toFloat()
                            val newRel = (newWidthDp * 2.0f * density) / bmpW
                            actionHistory[idx] = act.copy(strokeWidthRelative = newRel)
                          }
                        }
                      }
                    )
                  }
                }
              } else {
                // Drawing configuration mode
                Column(modifier = Modifier.fillMaxWidth()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                      FilterChip(
                        selected = mosaicMode == MosaicMode.PEN,
                        onClick = { mosaicMode = MosaicMode.PEN },
                        label = { Text(stringResource(R.string.mosaic_mode_pen)) },
                        leadingIcon = {
                          Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                      )
                      FilterChip(
                        selected = mosaicMode == MosaicMode.RECTANGLE,
                        onClick = { mosaicMode = MosaicMode.RECTANGLE },
                        label = { Text(stringResource(R.string.mosaic_mode_rect)) },
                        leadingIcon = {
                          Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                      )
                    }

                    Text(
                      text = if (mosaicMode == MosaicMode.PEN) {
                        stringResource(R.string.hint_draw_mosaic_pen)
                      } else {
                        stringResource(R.string.hint_drag_mosaic_rect)
                      },
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(start = 8.dp)
                    )
                  }

                  Spacer(modifier = Modifier.height(6.dp))

                  MosaicIntensitySelectorRow(
                    currentPixelSizeDp = mosaicPixelSizeDp,
                    onIntensitySelected = { mosaicPixelSizeDp = it }
                  )

                  if (mosaicMode == MosaicMode.PEN) {
                    Spacer(modifier = Modifier.height(6.dp))
                    BrushSizeRow(
                      currentWidthDp = strokeWidthDp,
                      label = stringResource(R.string.label_pen_size),
                      onWidthSelected = { strokeWidthDp = it }
                    )
                  }
                }
              }
            }

            EditTool.CROP -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(R.string.hint_crop_handles),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                  onClick = { cropBounds = CropBounds() },
                  enabled = !cropBounds.isDefault,
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(stringResource(R.string.btn_reset_crop))
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            ToolButton(
              tool = EditTool.MARKER,
              icon = Icons.Default.Brush,
              label = stringResource(R.string.tool_marker),
              isSelected = selectedTool == EditTool.MARKER,
              onClick = {
                selectedTool = EditTool.MARKER
                selectedMosaicId = null
              }
            )
            ToolButton(
              tool = EditTool.HIGHLIGHTER,
              icon = Icons.Default.Highlight,
              label = stringResource(R.string.tool_highlighter),
              isSelected = selectedTool == EditTool.HIGHLIGHTER,
              onClick = {
                selectedTool = EditTool.HIGHLIGHTER
                selectedMosaicId = null
              }
            )
            ToolButton(
              tool = EditTool.MOSAIC,
              icon = Icons.Default.GridOn,
              label = stringResource(R.string.tool_mosaic),
              isSelected = selectedTool == EditTool.MOSAIC,
              onClick = {
                selectedTool = EditTool.MOSAIC
              }
            )
            ToolButton(
              tool = EditTool.CROP,
              icon = Icons.Default.Crop,
              label = stringResource(R.string.tool_crop),
              isSelected = selectedTool == EditTool.CROP,
              onClick = {
                selectedTool = EditTool.CROP
                selectedMosaicId = null
              }
            )
          }
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
      contentAlignment = Alignment.Center
    ) {
      val bmp = loadedBitmap
      if (bmp != null) {
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
          contentAlignment = Alignment.Center
        ) {
          val canvasWidth = constraints.maxWidth.toFloat()
          val canvasHeight = constraints.maxHeight.toFloat()

          val imgW = bmp.width.toFloat()
          val imgH = bmp.height.toFloat()

          val fitScale = min(canvasWidth / imgW, canvasHeight / imgH)
          val renderW = imgW * fitScale
          val renderH = imgH * fitScale

          Box(
            modifier = Modifier
              .size(
                width = (renderW / density).dp,
                height = (renderH / density).dp
              )
              .clipToBounds()
          ) {
            AsyncImage(
              model = sourceFile,
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.FillBounds
            )

            // Drawing and gesture canvas
            Canvas(
              modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedTool, mosaicMode) {
                  detectTapGestures(
                    onTap = { offset ->
                      val w = size.width.toFloat()
                      val h = size.height.toFloat()
                      val relX = (offset.x / w).coerceIn(0f, 1f)
                      val relY = (offset.y / h).coerceIn(0f, 1f)

                      if (selectedTool == EditTool.MOSAIC) {
                        val hit = findMosaicAt(relX, relY, actionHistory, w, h)
                        selectedMosaicId = hit?.id
                      } else {
                        selectedMosaicId = null
                      }
                    }
                  )
                }
                .pointerInput(selectedTool, mosaicMode) {
                  detectDragGestures(
                    onDragStart = { offset ->
                      val w = size.width.toFloat()
                      val h = size.height.toFloat()
                      val relX = (offset.x / w).coerceIn(0f, 1f)
                      val relY = (offset.y / h).coerceIn(0f, 1f)
                      val relPt = Offset(relX, relY)

                      when (selectedTool) {
                        EditTool.MARKER, EditTool.HIGHLIGHTER -> {
                          selectedMosaicId = null
                          currentStrokePoints = listOf(relPt)
                        }
                        EditTool.MOSAIC -> {
                          selectedMosaicId = null
                          if (mosaicMode == MosaicMode.PEN) {
                            currentStrokePoints = listOf(relPt)
                          } else {
                            currentMosaicRectStart = relPt
                            currentMosaicRectEnd = relPt
                          }
                        }
                        EditTool.CROP -> {
                          cropDragStartBounds = cropBounds
                          cropDragStartPoint = relPt
                          activeCropHandle = findTouchedCropHandle(relX, relY, cropBounds, w, h)
                        }
                      }
                    },
                    onDrag = { change, _ ->
                      change.consume()
                      val w = size.width.toFloat()
                      val h = size.height.toFloat()
                      val relX = (change.position.x / w).coerceIn(0f, 1f)
                      val relY = (change.position.y / h).coerceIn(0f, 1f)
                      val relPt = Offset(relX, relY)

                      when (selectedTool) {
                        EditTool.MARKER, EditTool.HIGHLIGHTER -> {
                          currentStrokePoints = currentStrokePoints + relPt
                        }
                        EditTool.MOSAIC -> {
                          if (mosaicMode == MosaicMode.PEN) {
                            currentStrokePoints = currentStrokePoints + relPt
                          } else {
                            currentMosaicRectEnd = relPt
                          }
                        }
                        EditTool.CROP -> {
                          activeCropHandle?.let { handle ->
                            val deltaX = relX - cropDragStartPoint.x
                            val deltaY = relY - cropDragStartPoint.y
                            cropBounds = updateCropBoundsFromDelta(cropDragStartBounds, handle, deltaX, deltaY)
                          }
                        }
                      }
                    },
                    onDragEnd = {
                      when (selectedTool) {
                        EditTool.MARKER -> {
                          if (currentStrokePoints.size >= 2) {
                            val action = EditAction.MarkerStroke(
                              points = currentStrokePoints,
                              color = selectedColor,
                              strokeWidthRelative = (strokeWidthDp * density) / renderW
                            )
                            actionHistory.add(action)
                            redoStack.clear()
                          }
                          currentStrokePoints = emptyList()
                        }

                        EditTool.HIGHLIGHTER -> {
                          if (currentStrokePoints.size >= 2) {
                            val action = EditAction.HighlighterStroke(
                              points = currentStrokePoints,
                              color = selectedHighlighterColor,
                              strokeWidthRelative = (strokeWidthDp * 1.6f * density) / renderW
                            )
                            actionHistory.add(action)
                            redoStack.clear()
                          }
                          currentStrokePoints = emptyList()
                        }

                        EditTool.MOSAIC -> {
                          if (mosaicMode == MosaicMode.PEN) {
                            if (currentStrokePoints.size >= 2) {
                              val action = EditAction.MosaicPen(
                                points = currentStrokePoints,
                                strokeWidthRelative = (strokeWidthDp * 2.0f * density) / renderW,
                                pixelSizeRelative = (mosaicPixelSizeDp * density) / renderW
                              )
                              actionHistory.add(action)
                              redoStack.clear()
                            }
                            currentStrokePoints = emptyList()
                          } else {
                            val s = currentMosaicRectStart
                            val e = currentMosaicRectEnd
                            if (s != null && e != null && abs(s.x - e.x) > 0.01f && abs(s.y - e.y) > 0.01f) {
                              val l = min(s.x, e.x)
                              val t = min(s.y, e.y)
                              val r = max(s.x, e.x)
                              val b = max(s.y, e.y)
                              val action = EditAction.MosaicRect(
                                rectRelative = Rect(l, t, r, b),
                                pixelSizeRelative = (mosaicPixelSizeDp * density) / renderW
                              )
                              actionHistory.add(action)
                              redoStack.clear()
                            }
                            currentMosaicRectStart = null
                            currentMosaicRectEnd = null
                          }
                        }

                        EditTool.CROP -> {
                          activeCropHandle = null
                        }
                      }
                    },
                    onDragCancel = {
                      currentStrokePoints = emptyList()
                      currentMosaicRectStart = null
                      currentMosaicRectEnd = null
                      activeCropHandle = null
                    }
                  )
                }
            ) {
              val w = size.width
              val h = size.height

              // Draw committed edit actions
              for (action in actionHistory) {
                drawEditActionItem(action, w, h, density)
                if (action.id == selectedMosaicId && selectedTool == EditTool.MOSAIC) {
                  drawSelectedMosaicHighlight(action, w, h, density)
                }
              }

              // Active stroke previews
              if (currentStrokePoints.size >= 2) {
                when (selectedTool) {
                  EditTool.MARKER -> {
                    drawStrokePathLine(
                      points = currentStrokePoints,
                      color = selectedColor,
                      widthPx = strokeWidthDp * density,
                      canvasWidth = w,
                      canvasHeight = h
                    )
                  }
                  EditTool.HIGHLIGHTER -> {
                    drawStrokePathLine(
                      points = currentStrokePoints,
                      color = selectedHighlighterColor.copy(alpha = 0.42f),
                      widthPx = strokeWidthDp * 1.6f * density,
                      canvasWidth = w,
                      canvasHeight = h
                    )
                  }
                  EditTool.MOSAIC -> {
                    if (mosaicMode == MosaicMode.PEN) {
                      drawMosaicPenPreviewLine(
                        points = currentStrokePoints,
                        widthPx = strokeWidthDp * 2.0f * density,
                        canvasWidth = w,
                        canvasHeight = h
                      )
                    }
                  }
                  else -> {}
                }
              }

              if (currentMosaicRectStart != null && currentMosaicRectEnd != null) {
                val s = currentMosaicRectStart!!
                val e = currentMosaicRectEnd!!
                val l = min(s.x, e.x) * w
                val t = min(s.y, e.y) * h
                val r = max(s.x, e.x) * w
                val b = max(s.y, e.y) * h
                drawMosaicRectPreviewBox(Rect(l, t, r, b), mosaicPixelSizeDp * density, density)
              }

              // Crop mask & interactive handles
              if (selectedTool == EditTool.CROP || !cropBounds.isDefault) {
                drawCropOverlayBox(cropBounds, w, h, selectedTool == EditTool.CROP, density)
              }
            }

            // Floating Delete button positioned directly above selected mosaic
            val selectedAction = actionHistory.find { it.id == selectedMosaicId }
            if (selectedAction != null && selectedTool == EditTool.MOSAIC) {
              val bounds = computeActionPixelBounds(selectedAction, renderW, renderH)
              val centerX = ((bounds.left + bounds.right) / 2f)
              val pillY = if (bounds.top > 54f * density) {
                bounds.top - 46f * density
              } else {
                (bounds.bottom + 8f * density).coerceAtMost(renderH - 46f * density)
              }

              val pillWidthPx = 110f * density
              val pillXPx = (centerX - (pillWidthPx / 2f)).coerceIn(8f * density, (renderW - pillWidthPx - 8f * density).coerceAtLeast(0f))

              Box(
                modifier = Modifier
                  .offset { IntOffset(pillXPx.roundToInt(), pillY.roundToInt()) }
              ) {
                Surface(
                  shape = RoundedCornerShape(20.dp),
                  color = MaterialTheme.colorScheme.errorContainer,
                  shadowElevation = 6.dp,
                  tonalElevation = 4.dp,
                  modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                      val idx = actionHistory.indexOfFirst { it.id == selectedMosaicId }
                      if (idx != -1) {
                        val removed = actionHistory.removeAt(idx)
                        redoStack.add(removed)
                      }
                      selectedMosaicId = null
                    }
                    .testTag("btn_delete_mosaic_overlay")
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      Icons.Default.Delete,
                      contentDescription = stringResource(R.string.cd_delete_mosaic),
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      text = stringResource(R.string.btn_delete_mosaic),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onErrorContainer
                    )
                  }
                }
              }
            }
          }
        }
      } else {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      }

      AnimatedVisibility(
        visible = isProcessing,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.inversePrimary,
              strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
              text = stringResource(R.string.progress_saving_screenshot),
              color = MaterialTheme.colorScheme.inverseOnSurface,
              style = MaterialTheme.typography.bodyMedium
            )
          }
        }
      }
    }
  }

  if (showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { showDiscardDialog = false },
      title = { Text(stringResource(R.string.discard_changes_title)) },
      text = { Text(stringResource(R.string.discard_changes_message)) },
      confirmButton = {
        Button(
          onClick = {
            showDiscardDialog = false
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text(stringResource(R.string.btn_discard))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDiscardDialog = false }) {
          Text(stringResource(R.string.btn_keep_editing))
        }
      }
    )
  }
}

private fun DrawScope.drawEditActionItem(action: EditAction, canvasWidth: Float, canvasHeight: Float, density: Float) {
  when (action) {
    is EditAction.MarkerStroke -> {
      drawStrokePathLine(
        points = action.points,
        color = action.color,
        widthPx = action.strokeWidthRelative * canvasWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
      )
    }

    is EditAction.HighlighterStroke -> {
      drawStrokePathLine(
        points = action.points,
        color = action.color.copy(alpha = 0.42f),
        widthPx = action.strokeWidthRelative * canvasWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
      )
    }

    is EditAction.MosaicPen -> {
      drawMosaicPenPreviewLine(
        points = action.points,
        widthPx = action.strokeWidthRelative * canvasWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight
      )
    }

    is EditAction.MosaicRect -> {
      val l = action.rectRelative.left * canvasWidth
      val t = action.rectRelative.top * canvasHeight
      val r = action.rectRelative.right * canvasWidth
      val b = action.rectRelative.bottom * canvasHeight
      drawMosaicRectPreviewBox(
        rect = Rect(l, t, r, b),
        pixelSizePx = action.pixelSizeRelative * canvasWidth,
        density = density
      )
    }
  }
}

private fun DrawScope.drawSelectedMosaicHighlight(
  action: EditAction,
  canvasWidth: Float,
  canvasHeight: Float,
  density: Float
) {
  val bounds = computeActionPixelBounds(action, canvasWidth, canvasHeight)
  val rect = Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
  if (rect.width <= 0 || rect.height <= 0) return

  // Highlight fill
  drawRect(
    color = Color(0x33FF3D00),
    topLeft = rect.topLeft,
    size = rect.size
  )

  // Animated-style dashed red-orange selection border
  drawRect(
    color = Color(0xFFFF3D00),
    topLeft = rect.topLeft,
    size = rect.size,
    style = Stroke(
      width = 2.5f * density,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
    )
  )

  // 4 Corner brackets
  val cornerLen = min(16f * density, min(rect.width, rect.height) / 2.5f)
  val cornerThick = 3.5f * density
  val cornerColor = Color(0xFFFF3D00)

  // Top-Left
  drawLine(cornerColor, rect.topLeft, Offset(rect.left + cornerLen, rect.top), cornerThick)
  drawLine(cornerColor, rect.topLeft, Offset(rect.left, rect.top + cornerLen), cornerThick)

  // Top-Right
  drawLine(cornerColor, rect.topRight, Offset(rect.right - cornerLen, rect.top), cornerThick)
  drawLine(cornerColor, rect.topRight, Offset(rect.right, rect.top + cornerLen), cornerThick)

  // Bottom-Left
  drawLine(cornerColor, rect.bottomLeft, Offset(rect.left + cornerLen, rect.bottom), cornerThick)
  drawLine(cornerColor, rect.bottomLeft, Offset(rect.left, rect.bottom - cornerLen), cornerThick)

  // Bottom-Right
  drawLine(cornerColor, rect.bottomRight, Offset(rect.right - cornerLen, rect.bottom), cornerThick)
  drawLine(cornerColor, rect.bottomRight, Offset(rect.right, rect.bottom - cornerLen), cornerThick)
}

private fun computeActionPixelBounds(action: EditAction, canvasWidth: Float, canvasHeight: Float): PixelBounds {
  return when (action) {
    is EditAction.MosaicRect -> {
      PixelBounds(
        left = action.rectRelative.left * canvasWidth,
        top = action.rectRelative.top * canvasHeight,
        right = action.rectRelative.right * canvasWidth,
        bottom = action.rectRelative.bottom * canvasHeight
      )
    }
    is EditAction.MosaicPen -> {
      if (action.points.isEmpty()) return PixelBounds(0f, 0f, 0f, 0f)
      var minX = 1f
      var minY = 1f
      var maxX = 0f
      var maxY = 0f
      for (p in action.points) {
        minX = min(minX, p.x)
        minY = min(minY, p.y)
        maxX = max(maxX, p.x)
        maxY = max(maxY, p.y)
      }
      val pad = action.strokeWidthRelative / 2f
      PixelBounds(
        left = (minX - pad).coerceAtLeast(0f) * canvasWidth,
        top = (minY - pad).coerceAtLeast(0f) * canvasHeight,
        right = (maxX + pad).coerceAtMost(1f) * canvasWidth,
        bottom = (maxY + pad).coerceAtMost(1f) * canvasHeight
      )
    }
    else -> PixelBounds(0f, 0f, 0f, 0f)
  }
}

private fun findMosaicAt(
  relX: Float,
  relY: Float,
  actions: List<EditAction>,
  canvasWidth: Float,
  canvasHeight: Float
): EditAction? {
  val touchPadX = 24f / canvasWidth.coerceAtLeast(1f)
  val touchPadY = 24f / canvasHeight.coerceAtLeast(1f)

  for (i in actions.indices.reversed()) {
    val action = actions[i]
    when (action) {
      is EditAction.MosaicRect -> {
        val r = action.rectRelative
        if (relX >= (r.left - touchPadX) && relX <= (r.right + touchPadX) &&
          relY >= (r.top - touchPadY) && relY <= (r.bottom + touchPadY)
        ) {
          return action
        }
      }
      is EditAction.MosaicPen -> {
        val strokePad = (action.strokeWidthRelative / 2f) + touchPadX
        for (pt in action.points) {
          val dx = pt.x - relX
          val dy = pt.y - relY
          if ((dx * dx + dy * dy) <= strokePad * strokePad) {
            return action
          }
        }
      }
      else -> {}
    }
  }
  return null
}

private fun DrawScope.drawStrokePathLine(
  points: List<Offset>,
  color: Color,
  widthPx: Float,
  canvasWidth: Float,
  canvasHeight: Float
) {
  if (points.size < 2) return
  val path = Path()
  val first = points[0]
  path.moveTo(first.x * canvasWidth, first.y * canvasHeight)
  for (i in 1 until points.size) {
    val pt = points[i]
    path.lineTo(pt.x * canvasWidth, pt.y * canvasHeight)
  }

  drawPath(
    path = path,
    color = color,
    style = Stroke(
      width = widthPx,
      cap = StrokeCap.Round,
      join = StrokeJoin.Round
    )
  )
}

private fun DrawScope.drawMosaicPenPreviewLine(
  points: List<Offset>,
  widthPx: Float,
  canvasWidth: Float,
  canvasHeight: Float
) {
  if (points.size < 2) return
  val path = Path()
  val first = points[0]
  path.moveTo(first.x * canvasWidth, first.y * canvasHeight)
  for (i in 1 until points.size) {
    val pt = points[i]
    path.lineTo(pt.x * canvasWidth, pt.y * canvasHeight)
  }

  drawPath(
    path = path,
    color = Color(0x99A0A0A0),
    style = Stroke(
      width = widthPx,
      cap = StrokeCap.Round,
      join = StrokeJoin.Round,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    )
  )
}

private fun DrawScope.drawMosaicRectPreviewBox(
  rect: Rect,
  pixelSizePx: Float = 24f,
  density: Float = 1f
) {
  if (rect.width <= 0 || rect.height <= 0) return

  // Base translucent blur tone
  drawRect(
    color = Color(0x55444444),
    topLeft = rect.topLeft,
    size = rect.size
  )

  // Checkerboard grid pattern reflecting mosaic pixel size
  val block = pixelSizePx.coerceIn(8f * density, 80f * density)
  var y = rect.top
  var rowIndex = 0
  while (y < rect.bottom) {
    val h = min(block, rect.bottom - y)
    var x = rect.left
    var colIndex = 0
    while (x < rect.right) {
      val w = min(block, rect.right - x)
      if ((rowIndex + colIndex) % 2 == 0) {
        drawRect(
          color = Color(0x28FFFFFF),
          topLeft = Offset(x, y),
          size = Size(w, h)
        )
      } else {
        drawRect(
          color = Color(0x28000000),
          topLeft = Offset(x, y),
          size = Size(w, h)
        )
      }
      x += block
      colIndex++
    }
    y += block
    rowIndex++
  }

  // Border outline
  drawRect(
    color = Color(0xDDFFFFFF),
    topLeft = rect.topLeft,
    size = rect.size,
    style = Stroke(
      width = 1.8f * density,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    )
  )
}

private fun DrawScope.drawCropOverlayBox(
  bounds: CropBounds,
  canvasWidth: Float,
  canvasHeight: Float,
  isInteractive: Boolean,
  density: Float
) {
  val cropL = bounds.left * canvasWidth
  val cropT = bounds.top * canvasHeight
  val cropR = bounds.right * canvasWidth
  val cropB = bounds.bottom * canvasHeight

  val maskColor = Color(0x99000000)

  drawRect(maskColor, Offset.Zero, Size(canvasWidth, cropT))
  drawRect(maskColor, Offset(0f, cropB), Size(canvasWidth, canvasHeight - cropB))
  drawRect(maskColor, Offset(0f, cropT), Size(cropL, cropB - cropT))
  drawRect(maskColor, Offset(cropR, cropT), Size(canvasWidth - cropR, cropB - cropT))

  val cropRect = Rect(cropL, cropT, cropR, cropB)

  drawRect(
    color = Color.White,
    topLeft = cropRect.topLeft,
    size = cropRect.size,
    style = Stroke(width = 1.5f * density)
  )

  if (isInteractive) {
    val thirdW = cropRect.width / 3f
    val thirdH = cropRect.height / 3f
    val gridColor = Color(0x66FFFFFF)

    drawLine(gridColor, Offset(cropL + thirdW, cropT), Offset(cropL + thirdW, cropB), 1f * density)
    drawLine(gridColor, Offset(cropL + thirdW * 2, cropT), Offset(cropL + thirdW * 2, cropB), 1f * density)
    drawLine(gridColor, Offset(cropL, cropT + thirdH), Offset(cropR, cropT + thirdH), 1f * density)
    drawLine(gridColor, Offset(cropL, cropT + thirdH * 2), Offset(cropR, cropT + thirdH * 2), 1f * density)

    val handleLen = 22f * density
    val handleThick = 4.5f * density
    val handleColor = Color.White

    // 4 Corners
    drawLine(handleColor, Offset(cropL - 2f, cropT), Offset(cropL + handleLen, cropT), handleThick)
    drawLine(handleColor, Offset(cropL, cropT - 2f), Offset(cropL, cropT + handleLen), handleThick)

    drawLine(handleColor, Offset(cropR + 2f, cropT), Offset(cropR - handleLen, cropT), handleThick)
    drawLine(handleColor, Offset(cropR, cropT - 2f), Offset(cropR, cropT + handleLen), handleThick)

    drawLine(handleColor, Offset(cropL - 2f, cropB), Offset(cropL + handleLen, cropB), handleThick)
    drawLine(handleColor, Offset(cropL, cropB + 2f), Offset(cropL, cropB + handleLen), handleThick)

    drawLine(handleColor, Offset(cropR + 2f, cropB), Offset(cropR - handleLen, cropB), handleThick)
    drawLine(handleColor, Offset(cropR, cropB + 2f), Offset(cropR, cropB - handleLen), handleThick)

    // Center edge handles
    val edgeBarLen = 18f * density
    val midX = (cropL + cropR) / 2f
    val midY = (cropT + cropB) / 2f

    drawLine(handleColor, Offset(midX - edgeBarLen / 2f, cropT), Offset(midX + edgeBarLen / 2f, cropT), handleThick)
    drawLine(handleColor, Offset(midX - edgeBarLen / 2f, cropB), Offset(midX + edgeBarLen / 2f, cropB), handleThick)
    drawLine(handleColor, Offset(cropL, midY - edgeBarLen / 2f), Offset(cropL, midY + edgeBarLen / 2f), handleThick)
    drawLine(handleColor, Offset(cropR, midY - edgeBarLen / 2f), Offset(cropR, midY + edgeBarLen / 2f), handleThick)
  }
}

private fun findTouchedCropHandle(
  relX: Float,
  relY: Float,
  bounds: CropBounds,
  canvasWidth: Float,
  canvasHeight: Float
): CropHandle? {
  val densityThreshold = 36f
  val threshX = densityThreshold / canvasWidth.coerceAtLeast(1f)
  val threshY = densityThreshold / canvasHeight.coerceAtLeast(1f)

  val isNearLeft = abs(relX - bounds.left) <= threshX
  val isNearRight = abs(relX - bounds.right) <= threshX
  val isNearTop = abs(relY - bounds.top) <= threshY
  val isNearBottom = abs(relY - bounds.bottom) <= threshY

  val isInsideX = relX >= (bounds.left - threshX) && relX <= (bounds.right + threshX)
  val isInsideY = relY >= (bounds.top - threshY) && relY <= (bounds.bottom + threshY)

  return when {
    isNearLeft && isNearTop -> CropHandle.TOP_LEFT
    isNearRight && isNearTop -> CropHandle.TOP_RIGHT
    isNearLeft && isNearBottom -> CropHandle.BOTTOM_LEFT
    isNearRight && isNearBottom -> CropHandle.BOTTOM_RIGHT
    isNearTop && isInsideX -> CropHandle.TOP
    isNearBottom && isInsideX -> CropHandle.BOTTOM
    isNearLeft && isInsideY -> CropHandle.LEFT
    isNearRight && isInsideY -> CropHandle.RIGHT
    relX in bounds.left..bounds.right && relY in bounds.top..bounds.bottom -> CropHandle.INSIDE
    else -> null
  }
}

private fun updateCropBoundsFromDelta(
  start: CropBounds,
  handle: CropHandle,
  deltaX: Float,
  deltaY: Float
): CropBounds {
  val minSpan = 0.05f
  return when (handle) {
    CropHandle.TOP_LEFT -> {
      val newL = (start.left + deltaX).coerceIn(0f, start.right - minSpan)
      val newT = (start.top + deltaY).coerceIn(0f, start.bottom - minSpan)
      start.copy(left = newL, top = newT)
    }
    CropHandle.TOP_RIGHT -> {
      val newR = (start.right + deltaX).coerceIn(start.left + minSpan, 1f)
      val newT = (start.top + deltaY).coerceIn(0f, start.bottom - minSpan)
      start.copy(right = newR, top = newT)
    }
    CropHandle.BOTTOM_LEFT -> {
      val newL = (start.left + deltaX).coerceIn(0f, start.right - minSpan)
      val newB = (start.bottom + deltaY).coerceIn(start.top + minSpan, 1f)
      start.copy(left = newL, bottom = newB)
    }
    CropHandle.BOTTOM_RIGHT -> {
      val newR = (start.right + deltaX).coerceIn(start.left + minSpan, 1f)
      val newB = (start.bottom + deltaY).coerceIn(start.top + minSpan, 1f)
      start.copy(right = newR, bottom = newB)
    }
    CropHandle.TOP -> {
      val newT = (start.top + deltaY).coerceIn(0f, start.bottom - minSpan)
      start.copy(top = newT)
    }
    CropHandle.BOTTOM -> {
      val newB = (start.bottom + deltaY).coerceIn(start.top + minSpan, 1f)
      start.copy(bottom = newB)
    }
    CropHandle.LEFT -> {
      val newL = (start.left + deltaX).coerceIn(0f, start.right - minSpan)
      start.copy(left = newL)
    }
    CropHandle.RIGHT -> {
      val newR = (start.right + deltaX).coerceIn(start.left + minSpan, 1f)
      start.copy(right = newR)
    }
    CropHandle.INSIDE -> {
      val w = start.right - start.left
      val h = start.bottom - start.top
      val newL = (start.left + deltaX).coerceIn(0f, 1f - w)
      val newT = (start.top + deltaY).coerceIn(0f, 1f - h)
      CropBounds(left = newL, top = newT, right = newL + w, bottom = newT + h)
    }
  }
}

@Composable
private fun ToolButton(
  tool: EditTool,
  icon: ImageVector,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag("tool_btn_${tool.name.lowercase()}")
  ) {
    Surface(
      shape = CircleShape,
      color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
      modifier = Modifier.size(40.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          icon,
          contentDescription = label,
          tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun ColorPaletteRow(
  colors: List<Color>,
  selectedColor: Color,
  onColorSelected: (Color) -> Unit
) {
  LazyRow(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    contentPadding = PaddingValues(horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    items(colors) { color ->
      val isSelected = color == selectedColor
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(color)
          .border(
            width = if (isSelected) 3.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
            shape = CircleShape
          )
          .clickable { onColorSelected(color) },
        contentAlignment = Alignment.Center
      ) {
        if (isSelected) {
          Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = if (color == Color.White || color == Color(0xFFFFEB3B) || color == Color(0xFFFFFF00)) Color.Black else Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun MosaicIntensitySelectorRow(
  currentPixelSizeDp: Float = 24f,
  currentPixelSizeRel: Float? = null,
  onIntensitySelected: (Float) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = stringResource(R.string.label_mosaic_intensity),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      MOSAIC_INTENSITY_OPTIONS.forEach { opt ->
        val isSelected = if (currentPixelSizeRel != null) {
          // Normalize matching based on threshold
          when (opt.pixelSizeDp) {
            12f -> currentPixelSizeRel <= 0.025f
            24f -> currentPixelSizeRel > 0.025f && currentPixelSizeRel <= 0.05f
            42f -> currentPixelSizeRel > 0.05f && currentPixelSizeRel <= 0.08f
            else -> currentPixelSizeRel > 0.08f
          }
        } else {
          abs(currentPixelSizeDp - opt.pixelSizeDp) < 5f
        }

        FilterChip(
          selected = isSelected,
          onClick = { onIntensitySelected(opt.pixelSizeDp) },
          label = {
            Text(
              text = stringResource(opt.labelRes),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
          },
          shape = RoundedCornerShape(8.dp)
        )
      }
    }
  }
}

@Composable
private fun BrushSizeRow(
  currentWidthDp: Float,
  label: String? = null,
  onWidthSelected: (Float) -> Unit
) {
  val sizes = listOf(6f, 12f, 20f, 32f)
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label ?: stringResource(R.string.stroke_width_label, currentWidthDp.roundToInt()),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.weight(1f))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      sizes.forEach { sizeDp ->
        val isSelected = abs(currentWidthDp - sizeDp) < 1f
        Surface(
          shape = CircleShape,
          color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .size(36.dp)
            .clickable { onWidthSelected(sizeDp) }
        ) {
          Box(contentAlignment = Alignment.Center) {
            Box(
              modifier = Modifier
                .size((sizeDp / 2.2f).coerceIn(4f, 20f).dp)
                .clip(CircleShape)
                .background(
                  if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
          }
        }
      }
    }
  }
}
