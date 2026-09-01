package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.engine.StitchEngine
import com.example.model.ImageItem
import com.example.model.SeamConfig
import com.example.model.StitchGlobalSettings
import com.example.model.StitchResult
import com.example.model.StitchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import kotlin.math.roundToInt

class StitchViewModel(application: Application) : AndroidViewModel(application) {

  private val _images = MutableStateFlow<List<ImageItem>>(emptyList())
  val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

  private val _seams = MutableStateFlow<List<SeamConfig>>(emptyList())
  val seams: StateFlow<List<SeamConfig>> = _seams.asStateFlow()

  private val _settings = MutableStateFlow(StitchGlobalSettings())
  val settings: StateFlow<StitchGlobalSettings> = _settings.asStateFlow()

  private val _uiState = MutableStateFlow<StitchUiState>(StitchUiState.Idle)
  val uiState: StateFlow<StitchUiState> = _uiState.asStateFlow()

  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  private val _incomingSharedUris = MutableStateFlow<List<Uri>?>(null)
  val incomingSharedUris: StateFlow<List<Uri>?> = _incomingSharedUris.asStateFlow()

  private var seamDetectionJob: Job? = null

  fun clearUserMessage() {
    _userMessage.value = null
  }

  fun handleIncomingSharedUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
      val context = getApplication<Application>()
      val cachedUris = mutableListOf<Uri>()
      for ((index, uri) in uris.withIndex()) {
        try {
          val tempFile = java.io.File(context.cacheDir, "shared_import_${System.currentTimeMillis()}_$index.png")
          context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }
          if (tempFile.exists() && tempFile.length() > 0) {
            cachedUris.add(Uri.fromFile(tempFile))
          } else {
            cachedUris.add(uri)
          }
        } catch (e: Exception) {
          e.printStackTrace()
          cachedUris.add(uri)
        }
      }
      _incomingSharedUris.value = cachedUris
    }
  }

  fun clearIncomingSharedUris() {
    _incomingSharedUris.value = null
  }

  fun addImages(newUris: List<Uri>) {
    if (newUris.isEmpty()) return
    viewModelScope.launch {
      val context = getApplication<Application>()
      val currentList = _images.value.toMutableList()

      for (uri in newUris) {
        val meta = StitchEngine.getImageMetadata(context, uri)
        val thumb = StitchEngine.loadThumbnail(context, uri, 360)
        currentList.add(meta.copy(thumbnail = thumb))
      }

      _images.value = currentList
      autoDetectAllSeams()
    }
  }

  fun removeImage(index: Int) {
    val current = _images.value.toMutableList()
    if (index in current.indices) {
      current.removeAt(index)
      _images.value = current
      autoDetectAllSeams()
    }
  }

  fun moveImage(fromIndex: Int, toIndex: Int) {
    val current = _images.value.toMutableList()
    if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
      val item = current.removeAt(fromIndex)
      current.add(toIndex, item)
      _images.value = current
      autoDetectAllSeams()
    }
  }

  fun clearImages() {
    _images.value = emptyList()
    _seams.value = emptyList()
    _uiState.value = StitchUiState.Idle
  }

  fun updateSettings(newSettings: StitchGlobalSettings) {
    _settings.value = newSettings
    com.example.service.QuickLaunchNotificationManager.updatePersistentNotification(
      getApplication(),
      newSettings.persistentNotification
    )
    if (newSettings.autoDetectOverlap) {
      autoDetectAllSeams()
    }
  }

  fun updateSeam(index: Int, config: SeamConfig) {
    val currentSeams = _seams.value.toMutableList()
    while (currentSeams.size <= index) {
      currentSeams.add(SeamConfig())
    }
    currentSeams[index] = config
    _seams.value = currentSeams
  }

  fun adjustManualOffset(index: Int, delta: Int) {
    val currentSeams = _seams.value.toMutableList()
    while (currentSeams.size <= index) {
      currentSeams.add(SeamConfig())
    }
    val existing = currentSeams[index]
    val updated = existing.copy(
      manualOffset = existing.manualOffset + delta
    )
    currentSeams[index] = updated
    _seams.value = currentSeams
  }

  fun autoDetectAllSeams() {
    val list = _images.value
    if (list.size < 2) {
      seamDetectionJob?.cancel()
      _seams.value = emptyList()
      _uiState.value = StitchUiState.Idle
      return
    }

    seamDetectionJob?.cancel()
    seamDetectionJob = viewModelScope.launch {
      val context = getApplication<Application>()
      val newSeams = mutableListOf<SeamConfig>()
      val currentSettings = _settings.value

      for (i in 0 until list.size - 1) {
        if (!isActive) return@launch
        _uiState.value = StitchUiState.Detecting(currentPair = i + 1, totalPairs = list.size - 1)

        val topItem = list[i]
        val bottomItem = list[i + 1]

        if (currentSettings.autoDetectOverlap) {
          val detected = StitchEngine.detectOverlap(context, topItem, bottomItem, currentSettings)
          newSeams.add(detected)
        } else {
          newSeams.add(
            SeamConfig(
              autoOverlap = 0,
              confidence = 1f,
              isAutoDetected = false,
              topTrim = if (currentSettings.removeStatusBar) currentSettings.statusBarHeightPx else 0,
              bottomTrim = if (currentSettings.removeNavBar) currentSettings.navBarHeightPx else 0
            )
          )
        }
      }

      if (isActive) {
        _seams.value = newSeams
        _uiState.value = StitchUiState.Idle
      }
    }
  }

  fun stitchNow() {
    val context = getApplication<Application>()
    val currentImages = _images.value
    if (currentImages.size < 2) {
      _userMessage.value = context.getString(R.string.msg_need_at_least_2_images)
      return
    }

    viewModelScope.launch {
      _uiState.value = StitchUiState.Stitching(
        progress = 0f,
        statusMessage = context.getString(R.string.progress_starting)
      )

      val result = StitchEngine.stitchImages(
        context = context,
        images = currentImages,
        seams = _seams.value,
        settings = _settings.value
      ) { progress, message ->
        _uiState.value = StitchUiState.Stitching(progress = progress, statusMessage = message)
      }

      result.onSuccess { stitchResult ->
        _uiState.value = StitchUiState.Success(stitchResult)
      }.onFailure { error ->
        _uiState.value = StitchUiState.Error(
          error.localizedMessage ?: context.getString(R.string.msg_stitch_failed)
        )
      }
    }
  }

  fun resetToEdit() {
    _uiState.value = StitchUiState.Idle
  }

  fun updateStitchResult(updatedResult: StitchResult) {
    _uiState.value = StitchUiState.Success(updatedResult)
    val context = getApplication<Application>()
    _userMessage.value = context.getString(R.string.msg_edit_saved)
  }

  fun updateImageItem(index: Int, newUri: Uri, newWidth: Int, newHeight: Int, newSizeBytes: Long, newThumbnail: android.graphics.Bitmap?) {
    val current = _images.value.toMutableList()
    if (index in current.indices) {
      val old = current[index]
      current[index] = old.copy(
        uri = newUri,
        width = newWidth,
        height = newHeight,
        fileSizeBytes = newSizeBytes,
        thumbnail = newThumbnail ?: old.thumbnail
      )
      _images.value = current
      autoDetectAllSeams()
      val context = getApplication<Application>()
      _userMessage.value = context.getString(R.string.msg_edit_saved)
    }
  }

  fun saveToGallery(result: StitchResult) {
    viewModelScope.launch {
      val context = getApplication<Application>()
      val success = withContext(Dispatchers.IO) {
        try {
          val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Stitched_${System.currentTimeMillis()}.${_settings.value.outputFormat.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, _settings.value.outputFormat.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenshotStitcher")
              put(MediaStore.Images.Media.IS_PENDING, 1)
            }
          }

          val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false

          context.contentResolver.openOutputStream(uri)?.use { out ->
            FileInputStream(result.file).use { input ->
              input.copyTo(out)
            }
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
          }
          true
        } catch (e: Exception) {
          e.printStackTrace()
          false
        }
      }

      if (success) {
        _userMessage.value = context.getString(R.string.msg_saved_to_gallery)
      } else {
        _userMessage.value = context.getString(R.string.msg_save_failed)
      }
    }
  }

  fun createShareIntent(context: Context, result: StitchResult): Intent {
    val authority = "${context.packageName}.fileprovider"
    val contentUri = FileProvider.getUriForFile(context, authority, result.file)

    return Intent(Intent.ACTION_SEND).apply {
      type = _settings.value.outputFormat.mimeType
      putExtra(Intent.EXTRA_STREAM, contentUri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }
}
