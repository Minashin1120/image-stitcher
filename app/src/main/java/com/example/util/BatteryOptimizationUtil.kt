package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationUtil {
  private const val PREFS_NAME = "battery_prefs"
  private const val KEY_PROMPTED = "has_prompted_battery_optimization"

  /**
   * Checks whether this app is currently exempted from battery optimizations.
   */
  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    return true
  }

  /**
   * Requests battery optimization exemption via system intent dialog, with fallbacks.
   */
  fun requestIgnoreBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
          data = Uri.parse("package:${context.packageName}")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (e: Exception) {
        try {
          val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
          try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
          } catch (e3: Exception) {
            e3.printStackTrace()
          }
        }
      }
    }
  }

  /**
   * Opens the device battery optimization settings page directly.
   */
  fun openBatterySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
      } catch (e: Exception) {
        try {
          val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
          }
          context.startActivity(appDetailsIntent)
        } catch (e2: Exception) {
          e2.printStackTrace()
        }
      }
    }
  }

  /**
   * Checks if user has already been prompted for battery optimization on launch.
   */
  fun hasPrompted(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(KEY_PROMPTED, false)
  }

  /**
   * Marks that the user has been prompted for battery optimization.
   */
  fun setPrompted(context: Context, prompted: Boolean = true) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(KEY_PROMPTED, prompted).apply()
  }
}
