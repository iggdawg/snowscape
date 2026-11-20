package com.example.snowscape.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Abstract color palettes capturing different snowstorm atmospheres.
 */
enum class StormPalette(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val skyTopColor: Int,
  val skyUpperMidColor: Int,
  val skyLowerMidColor: Int,
  val skyBottomColor: Int,
  val horizonMistColor: Int,
  val ribbonPrimaryColor: Int,
  val ribbonSecondaryColor: Int,
  val puffCoreColor: Int,
  val puffHaloColor: Int,
  val wallpaperPrimaryColor: Int,
  val wallpaperSecondaryColor: Int,
  val wallpaperTertiaryColor: Int,
  val supportsDarkTheme: Boolean,
  val supportsDarkText: Boolean,
) {
  MIDNIGHT_SQUALL(
    id = "midnight_squall",
    displayName = "Midnight Squall",
    subtitle = "Deep winter night & icy wind",
    skyTopColor = 0xFF070D19.toInt(),
    skyUpperMidColor = 0xFF112036.toInt(),
    skyLowerMidColor = 0xFF213954.toInt(),
    skyBottomColor = 0xFF3A5878.toInt(),
    horizonMistColor = 0xFF7CA7CC.toInt(),
    ribbonPrimaryColor = 0xFF5A87B0.toInt(),
    ribbonSecondaryColor = 0xFF9AC5E8.toInt(),
    puffCoreColor = 0xFFFFFFFF.toInt(),
    puffHaloColor = 0xFFD2EAFF.toInt(),
    wallpaperPrimaryColor = 0xFF213954.toInt(),
    wallpaperSecondaryColor = 0xFF5A87B0.toInt(),
    wallpaperTertiaryColor = 0xFF7CA7CC.toInt(),
    supportsDarkTheme = true,
    supportsDarkText = false,
  ),
  TWILIGHT_FLURRY(
    id = "twilight_flurry",
    displayName = "Twilight Flurry",
    subtitle = "Dusk violet clouds & soft hush",
    skyTopColor = 0xFF110E20.toInt(),
    skyUpperMidColor = 0xFF221E3D.toInt(),
    skyLowerMidColor = 0xFF3E3866.toInt(),
    skyBottomColor = 0xFF695E8F.toInt(),
    horizonMistColor = 0xFFB5A6DC.toInt(),
    ribbonPrimaryColor = 0xFF8474B5.toInt(),
    ribbonSecondaryColor = 0xFFD4C5F5.toInt(),
    puffCoreColor = 0xFFFFFAFF.toInt(),
    puffHaloColor = 0xFFE8DCFF.toInt(),
    wallpaperPrimaryColor = 0xFF3E3866.toInt(),
    wallpaperSecondaryColor = 0xFF8474B5.toInt(),
    wallpaperTertiaryColor = 0xFFB5A6DC.toInt(),
    supportsDarkTheme = true,
    supportsDarkText = false,
  ),
  ARCTIC_WHITEOUT(
    id = "arctic_whiteout",
    displayName = "Arctic Whiteout",
    subtitle = "Overcast silver gale & dense mist",
    skyTopColor = 0xFF263340.toInt(),
    skyUpperMidColor = 0xFF3E5163.toInt(),
    skyLowerMidColor = 0xFF637A8E.toInt(),
    skyBottomColor = 0xFF94ACBF.toInt(),
    horizonMistColor = 0xFFDCE9F2.toInt(),
    ribbonPrimaryColor = 0xFFA7BFD1.toInt(),
    ribbonSecondaryColor = 0xFFE6F1F7.toInt(),
    puffCoreColor = 0xFFFFFFFF.toInt(),
    puffHaloColor = 0xFFEBF5FC.toInt(),
    wallpaperPrimaryColor = 0xFF637A8E.toInt(),
    wallpaperSecondaryColor = 0xFF94ACBF.toInt(),
    wallpaperTertiaryColor = 0xFFDCE9F2.toInt(),
    supportsDarkTheme = false,
    supportsDarkText = false,
  ),
  AURORA_DRIFT(
    id = "aurora_drift",
    displayName = "Aurora Drift",
    subtitle = "Boreal currents under polar snow",
    skyTopColor = 0xFF050F17.toInt(),
    skyUpperMidColor = 0xFF0B212E.toInt(),
    skyLowerMidColor = 0xFF143C45.toInt(),
    skyBottomColor = 0xFF255E5E.toInt(),
    horizonMistColor = 0xFF6CE3CC.toInt(),
    ribbonPrimaryColor = 0xFF32968D.toInt(),
    ribbonSecondaryColor = 0xFF7CF0D8.toInt(),
    puffCoreColor = 0xFFF5FFFF.toInt(),
    puffHaloColor = 0xFFCCFBF2.toInt(),
    wallpaperPrimaryColor = 0xFF143C45.toInt(),
    wallpaperSecondaryColor = 0xFF32968D.toInt(),
    wallpaperTertiaryColor = 0xFF6CE3CC.toInt(),
    supportsDarkTheme = true,
    supportsDarkText = false,
  );

  /**
   * Builds the Material You [WallpaperColors] for this storm palette.
   */
  @RequiresApi(Build.VERSION_CODES.O_MR1)
  fun toWallpaperColors(): WallpaperColors {
    val primary = Color.valueOf(wallpaperPrimaryColor)
    val secondary = Color.valueOf(wallpaperSecondaryColor)
    val tertiary = Color.valueOf(wallpaperTertiaryColor)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      var hints = 0
      if (supportsDarkTheme) {
        hints = hints or WallpaperColors.HINT_SUPPORTS_DARK_THEME
      }
      if (supportsDarkText) {
        hints = hints or WallpaperColors.HINT_SUPPORTS_DARK_TEXT
      }
      WallpaperColors(primary, secondary, tertiary, hints)
    } else {
      WallpaperColors(primary, secondary, tertiary)
    }
  }

  companion object {
    fun fromId(id: String?): StormPalette =
      entries.firstOrNull { it.id == id } ?: MIDNIGHT_SQUALL
  }
}

/**
 * User-configurable settings for the Snowscape live wallpaper.
 */
data class SnowscapeSettings(
  val palette: StormPalette = StormPalette.MIDNIGHT_SQUALL,
  val snowDensity: Float = 1.0f,
  val windIntensity: Float = 0.85f,
  val puffSoftness: Float = 1.05f,
)

/**
 * Persists and observes [SnowscapeSettings] via [SharedPreferences] so the companion app
 * and the running [SnowscapeWallpaperService] stay synchronized in real time.
 */
class SnowscapePreferences(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun load(): SnowscapeSettings {
    val paletteId = prefs.getString(KEY_PALETTE, StormPalette.MIDNIGHT_SQUALL.id)
    val density = prefs.getFloat(KEY_DENSITY, 1.0f).coerceIn(0.4f, 2.0f)
    val wind = prefs.getFloat(KEY_WIND, 0.85f).coerceIn(0.2f, 2.2f)
    val puffSize = prefs.getFloat(KEY_PUFF_SOFTNESS, 1.05f).coerceIn(0.6f, 1.8f)
    return SnowscapeSettings(
      palette = StormPalette.fromId(paletteId),
      snowDensity = density,
      windIntensity = wind,
      puffSoftness = puffSize,
    )
  }

  fun save(settings: SnowscapeSettings) {
    prefs.edit()
      .putString(KEY_PALETTE, settings.palette.id)
      .putFloat(KEY_DENSITY, settings.snowDensity.coerceIn(0.4f, 2.0f))
      .putFloat(KEY_WIND, settings.windIntensity.coerceIn(0.2f, 2.2f))
      .putFloat(KEY_PUFF_SOFTNESS, settings.puffSoftness.coerceIn(0.6f, 1.8f))
      .apply()
  }

  fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    prefs.unregisterOnSharedPreferenceChangeListener(listener)
  }

  companion object {
    const val PREFS_NAME = "snowscape_wallpaper_prefs"
    private const val KEY_PALETTE = "palette_id"
    private const val KEY_DENSITY = "snow_density"
    private const val KEY_WIND = "wind_intensity"
    private const val KEY_PUFF_SOFTNESS = "puff_softness"
  }
}
