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

  // Active gesture drawing states
  var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
  var currentMosaicRectStart by remember { mutableStateOf<Offset?>(null) }
  var currentMosaicRectEnd by remember { mutableStateOf<Offset?>(null) }

  // Cropping handle drag states
  var activeCropHandle by remember { mutableStateOf<CropHandle?>(null) }

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
              BrushSizeRow(
                currentWidthDp = strokeWidthDp,
                onWidthSelected = { strokeWidthDp = it }
              )
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
              onClick = { selectedTool = EditTool.MARKER }
            )
            ToolButton(
              tool = EditTool.HIGHLIGHTER,
              icon = Icons.Default.Highlight,
              label = stringResource(R.string.tool_highlighter),
              isSelected = selectedTool == EditTool.HIGHLIGHTER,
              onClick = { selectedTool = EditTool.HIGHLIGHTER }
            )
            ToolButton(
              tool = EditTool.MOSAIC,
              icon = Icons.Default.GridOn,
              label = stringResource(R.string.tool_mosaic),
              isSelected = selectedTool == EditTool.MOSAIC,
              onClick = { selectedTool = EditTool.MOSAIC }
            )
            ToolButton(
              tool = EditTool.CROP,
              icon = Icons.Default.Crop,
              label = stringResource(R.string.tool_crop),
              isSelected = selectedTool == EditTool.CROP,
              onClick = { selectedTool = EditTool.CROP }
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

            Canvas(
              modifier = Modifier
                .fillMaxSize()
                .pointerInput(selectedTool, mosaicMode, selectedColor, selectedHighlighterColor, strokeWidthDp, cropBounds) {
                  detectDragGestures(
                    onDragStart = { offset ->
                      val relX = (offset.x / size.width).coerceIn(0f, 1f)
                      val relY = (offset.y / size.height).coerceIn(0f, 1f)
                      val relPt = Offset(relX, relY)

                      when (selectedTool) {
                        EditTool.MARKER, EditTool.HIGHLIGHTER -> {
                          currentStrokePoints = listOf(relPt)
                        }
                        EditTool.MOSAIC -> {
                          if (mosaicMode == MosaicMode.PEN) {
                            currentStrokePoints = listOf(relPt)
                          } else {
                            currentMosaicRectStart = relPt
                            currentMosaicRectEnd = relPt
                          }
                        }
                        EditTool.CROP -> {
                          activeCropHandle = findTouchedCropHandle(relX, relY, cropBounds)
                        }
                      }
                    },
                    onDrag = { change, _ ->
                      change.consume()
                      val relX = (change.position.x / size.width).coerceIn(0f, 1f)
                      val relY = (change.position.y / size.height).coerceIn(0f, 1f)
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
                            cropBounds = updateCropBounds(cropBounds, handle, relX, relY)
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
                    }
                  )
                }
            ) {
              val w = size.width
              val h = size.height

              for (action in actionHistory) {
                drawEditActionItem(action, w, h)
              }

              if (currentStrokePoints.size >= 2) {
                when (selectedTool) {
                  EditTool.MARKER -> {
                    drawStrokePathLine(
                      points = currentStrokePoints,
                      color = selectedColor,
                      widthPx = strokeWidthDp * density,
                      canvasWidth = w,
                      canvasHeight = h,
                      isHighlighter = false
                    )
                  }
                  EditTool.HIGHLIGHTER -> {
                    drawStrokePathLine(
                      points = currentStrokePoints,
                      color = selectedHighlighterColor.copy(alpha = 0.42f),
                      widthPx = strokeWidthDp * 1.6f * density,
                      canvasWidth = w,
                      canvasHeight = h,
                      isHighlighter = true
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
                drawMosaicRectPreviewBox(Rect(l, t, r, b))
              }

              if (selectedTool == EditTool.CROP || !cropBounds.isDefault) {
                drawCropOverlayBox(cropBounds, w, h, selectedTool == EditTool.CROP, density)
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

private fun DrawScope.drawEditActionItem(action: EditAction, canvasWidth: Float, canvasHeight: Float) {
  when (action) {
    is EditAction.MarkerStroke -> {
      drawStrokePathLine(
        points = action.points,
        color = action.color,
        widthPx = action.strokeWidthRelative * canvasWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        isHighlighter = false
      )
    }

    is EditAction.HighlighterStroke -> {
      drawStrokePathLine(
        points = action.points,
        color = action.color.copy(alpha = 0.42f),
        widthPx = action.strokeWidthRelative * canvasWidth,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        isHighlighter = true
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
      drawMosaicRectPreviewBox(Rect(l, t, r, b))
    }
  }
}

private fun DrawScope.drawStrokePathLine(
  points: List<Offset>,
  color: Color,
  widthPx: Float,
  canvasWidth: Float,
  canvasHeight: Float,
  isHighlighter: Boolean
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

private fun DrawScope.drawMosaicRectPreviewBox(rect: Rect) {
  if (rect.width <= 0 || rect.height <= 0) return

  drawRect(
    color = Color(0x77707070),
    topLeft = rect.topLeft,
    size = rect.size
  )

  drawRect(
    color = Color.White,
    topLeft = rect.topLeft,
    size = rect.size,
    style = Stroke(
      width = 4f,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
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

  if (isInteractive) {
    val cropRect = Rect(cropL, cropT, cropR, cropB)
    drawRect(
      color = Color.White,
      topLeft = cropRect.topLeft,
      size = cropRect.size,
      style = Stroke(width = 2f * density)
    )

    val thirdW = cropRect.width / 3f
    val thirdH = cropRect.height / 3f
    val gridColor = Color(0x66FFFFFF)

    drawLine(gridColor, Offset(cropL + thirdW, cropT), Offset(cropL + thirdW, cropB), 1f * density)
    drawLine(gridColor, Offset(cropL + thirdW * 2, cropT), Offset(cropL + thirdW * 2, cropB), 1f * density)
    drawLine(gridColor, Offset(cropL, cropT + thirdH), Offset(cropR, cropT + thirdH), 1f * density)
    drawLine(gridColor, Offset(cropL, cropT + thirdH * 2), Offset(cropR, cropT + thirdH * 2), 1f * density)

    val handleLen = 18f * density
    val handleThick = 4f * density
    val handleColor = Color.White

    drawLine(handleColor, Offset(cropL - 2f, cropT), Offset(cropL + handleLen, cropT), handleThick)
    drawLine(handleColor, Offset(cropL, cropT - 2f), Offset(cropL + handleLen, cropT), handleThick)

    drawLine(handleColor, Offset(cropR + 2f, cropT), Offset(cropR - handleLen, cropT), handleThick)
    drawLine(handleColor, Offset(cropR, cropT - 2f), Offset(cropR, cropT + handleLen), handleThick)

    drawLine(handleColor, Offset(cropL - 2f, cropB), Offset(cropL + handleLen, cropB), handleThick)
    drawLine(handleColor, Offset(cropL, cropB + 2f), Offset(cropL + handleLen, cropB), handleThick)

    drawLine(handleColor, Offset(cropR + 2f, cropB), Offset(cropR - handleLen, cropB), handleThick)
    drawLine(handleColor, Offset(cropR, cropB + 2f), Offset(cropR, cropB - handleLen), handleThick)
  }
}

private fun findTouchedCropHandle(relX: Float, relY: Float, bounds: CropBounds): CropHandle {
  val threshold = 0.07f
  val isNearLeft = abs(relX - bounds.left) < threshold
  val isNearRight = abs(relX - bounds.right) < threshold
  val isNearTop = abs(relY - bounds.top) < threshold
  val isNearBottom = abs(relY - bounds.bottom) < threshold

  return when {
    isNearLeft && isNearTop -> CropHandle.TOP_LEFT
    isNearRight && isNearTop -> CropHandle.TOP_RIGHT
    isNearLeft && isNearBottom -> CropHandle.BOTTOM_LEFT
    isNearRight && isNearBottom -> CropHandle.BOTTOM_RIGHT
    isNearTop && relX in bounds.left..bounds.right -> CropHandle.TOP
    isNearBottom && relX in bounds.left..bounds.right -> CropHandle.BOTTOM
    isNearLeft && relY in bounds.top..bounds.bottom -> CropHandle.LEFT
    isNearRight && relY in bounds.top..bounds.bottom -> CropHandle.RIGHT
    relX in bounds.left..bounds.right && relY in bounds.top..bounds.bottom -> CropHandle.INSIDE
    else -> CropHandle.BOTTOM_RIGHT
  }
}

private fun updateCropBounds(
  current: CropBounds,
  handle: CropHandle,
  targetX: Float,
  targetY: Float
): CropBounds {
  val minSpan = 0.05f
  return when (handle) {
    CropHandle.TOP_LEFT -> current.copy(
      left = targetX.coerceIn(0f, current.right - minSpan),
      top = targetY.coerceIn(0f, current.bottom - minSpan)
    )
    CropHandle.TOP_RIGHT -> current.copy(
      right = targetX.coerceIn(current.left + minSpan, 1f),
      top = targetY.coerceIn(0f, current.bottom - minSpan)
    )
    CropHandle.BOTTOM_LEFT -> current.copy(
      left = targetX.coerceIn(0f, current.right - minSpan),
      bottom = targetY.coerceIn(current.top + minSpan, 1f)
    )
    CropHandle.BOTTOM_RIGHT -> current.copy(
      right = targetX.coerceIn(current.left + minSpan, 1f),
      bottom = targetY.coerceIn(current.top + minSpan, 1f)
    )
    CropHandle.TOP -> current.copy(top = targetY.coerceIn(0f, current.bottom - minSpan))
    CropHandle.BOTTOM -> current.copy(bottom = targetY.coerceIn(current.top + minSpan, 1f))
    CropHandle.LEFT -> current.copy(left = targetX.coerceIn(0f, current.right - minSpan))
    CropHandle.RIGHT -> current.copy(right = targetX.coerceIn(current.left + minSpan, 1f))
    CropHandle.INSIDE -> current
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
private fun BrushSizeRow(
  currentWidthDp: Float,
  onWidthSelected: (Float) -> Unit
) {
  val sizes = listOf(6f, 12f, 20f, 32f)
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = stringResource(R.string.stroke_width_label, currentWidthDp.roundToInt()),
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
