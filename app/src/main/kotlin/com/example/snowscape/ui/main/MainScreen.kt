package com.example.snowscape.ui.main

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.snowscape.ui.theme.SnowscapeTheme
import com.example.snowscape.wallpaper.SnowscapePreferences
import com.example.snowscape.wallpaper.SnowscapeSettings
import com.example.snowscape.wallpaper.SnowscapeWallpaperService
import com.example.snowscape.wallpaper.SnowstormRenderer
import com.example.snowscape.wallpaper.SnowstormSimulation
import com.example.snowscape.wallpaper.StormPalette
import kotlin.math.roundToInt

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isPreview = LocalInspectionMode.current
  val preferences = remember(context, isPreview) {
    if (isPreview) null else SnowscapePreferences(context)
  }

  var settings by remember {
    mutableStateOf(preferences?.load() ?: SnowscapeSettings())
  }

  SnowscapeWallpaperScreen(
    settings = settings,
    onSettingsChange = { updated ->
      settings = updated
      preferences?.save(updated)
    },
    onApplyWallpaper = { launchLiveWallpaperPicker(context) },
    modifier = modifier,
  )
}

/**
 * Legacy overload kept for existing instrumentation test compatibility.
 */
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  SnowscapeWallpaperScreen(
    settings = SnowscapeSettings(),
    onSettingsChange = {},
    onApplyWallpaper = {},
    modifier = modifier,
  )
}

@Composable
fun SnowscapeWallpaperScreen(
  settings: SnowscapeSettings,
  onSettingsChange: (SnowscapeSettings) -> Unit,
  onApplyWallpaper: () -> Unit,
  modifier: Modifier = Modifier,
  initialShowControls: Boolean = true,
) {
  var showControls by remember { mutableStateOf(initialShowControls) }
  val density = LocalDensity.current.density
  val isInspectionMode = LocalInspectionMode.current

  val simulation = remember {
    SnowstormSimulation(
      seed = 20261120L,
      initialSettings = settings,
    ).apply {
      setViewport(1080, 2400, density)
      // Advance to an atmospheric mid-storm moment for immediate visual richness
      repeat(30) { step(0.05f) }
    }
  }
  val renderer = remember { SnowstormRenderer() }

  // Keep simulation settings synchronized with UI sliders & palette selection
  simulation.updateSettings(settings)

  var frameTick by remember { mutableLongStateOf(0L) }

  if (!isInspectionMode) {
    LaunchedEffect(simulation) {
      var lastNanos = 0L
      while (true) {
        withFrameNanos { frameNanos ->
          val dt = if (lastNanos == 0L) {
            0.016f
          } else {
            ((frameNanos - lastNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.050f)
          }
          lastNanos = frameNanos
          simulation.step(dt)
          frameTick = frameNanos
        }
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // Full-screen interactive snowstorm live preview canvas
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(simulation) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            simulation.applyTouchGust(down.position.x, down.position.y)
            do {
              val event = awaitPointerEvent()
              event.changes.forEach { change ->
                if (change.pressed) {
                  simulation.applyTouchGust(change.position.x, change.position.y)
                }
              }
            } while (event.changes.any { it.pressed })
          }
        },
    ) {
      // Read frameTick so Compose invalidates the Canvas on each animation frame
      val unusedTick = frameTick
      val w = size.width.roundToInt().coerceAtLeast(1)
      val h = size.height.roundToInt().coerceAtLeast(1)
      simulation.setViewport(w, h, density)
      renderer.draw(drawContext.canvas.nativeCanvas, simulation)
    }

    // Top floating header bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = "Snowscape",
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
        )
        Text(
          text = settings.palette.displayName + " • " + settings.palette.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.78f),
        )
      }

      OutlinedButton(
        onClick = { showControls = !showControls },
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = Color(0x550B1524),
          contentColor = Color.White,
        ),
      ) {
        Text(text = if (showControls) "Hide Controls" else "Customize")
      }
    }

    // Bottom frosted-glass customization & wallpaper launcher sheet
    AnimatedVisibility(
      visible = showControls,
      enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xCC0C1624),
        tonalElevation = 8.dp,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .border(
              width = 1.dp,
              color = Color.White.copy(alpha = 0.14f),
              shape = RoundedCornerShape(28.dp),
            )
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          // Storm Vibe / Atmosphere Palette Selector
          Text(
            text = "Storm Atmosphere",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.92f),
          )

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            StormPalette.entries.forEach { palette ->
              PaletteChip(
                palette = palette,
                selected = settings.palette == palette,
                onClick = { onSettingsChange(settings.copy(palette = palette)) },
              )
            }
          }

          // Snowfall Density Slider
          SettingSlider(
            label = "Snowfall Density",
            valueText = when {
              settings.snowDensity < 0.75f -> "Light Flurry"
              settings.snowDensity < 1.35f -> "Steady Snow"
              else -> "Heavy Squall"
            },
            value = settings.snowDensity,
            valueRange = 0.4f..2.0f,
            onValueChange = { onSettingsChange(settings.copy(snowDensity = it)) },
          )

          // Wind & Squall Drift Slider
          SettingSlider(
            label = "Wind & Storm Drift",
            valueText = when {
              settings.windIntensity < 0.65f -> "Calm Drift"
              settings.windIntensity < 1.35f -> "Winter Breeze"
              else -> "Sweeping Gale"
            },
            value = settings.windIntensity,
            valueRange = 0.2f..2.2f,
            onValueChange = { onSettingsChange(settings.copy(windIntensity = it)) },
          )

          // Abstract Puff Softness & Scale Slider
          SettingSlider(
            label = "Abstract Puff Size",
            valueText = when {
              settings.puffSoftness < 0.85f -> "Fine Puffs"
              settings.puffSoftness < 1.30f -> "Soft Puffs"
              else -> "Dreamy Bokeh"
            },
            value = settings.puffSoftness,
            valueRange = 0.6f..1.8f,
            onValueChange = { onSettingsChange(settings.copy(puffSoftness = it)) },
          )

          // Touch hint & Reset defaults row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Touch & drag sky to swirl snow puffs",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.65f),
            )
            TextButton(
              onClick = { onSettingsChange(SnowscapeSettings()) },
            ) {
              Text(
                text = "Reset",
                color = Color(0xFF9AC8F5),
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }

          // Primary CTA to apply the Live Wallpaper
          Button(
            onClick = onApplyWallpaper,
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF87CFF9),
              contentColor = Color(0xFF062338),
            ),
          ) {
            Text(
              text = "Set Live Wallpaper",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PaletteChip(
  palette: StormPalette,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val borderColor = if (selected) Color(0xFF87CFF9) else Color.White.copy(alpha = 0.18f)
  val containerColor = if (selected) Color(0x443A6B9C) else Color(0x22FFFFFF)

  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(containerColor)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Miniature storm gradient orb with a bright puff core dot
    Box(
      modifier = Modifier
        .size(26.dp)
        .clip(CircleShape)
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(palette.skyTopColor),
              Color(palette.skyLowerMidColor),
              Color(palette.horizonMistColor),
            ),
          ),
        )
        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .size(9.dp)
          .clip(CircleShape)
          .background(Color(palette.puffCoreColor).copy(alpha = 0.9f)),
      )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = palette.displayName,
      style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      ),
      color = Color.White,
    )
  }
}

@Composable
private fun SettingSlider(
  label: String,
  valueText: String,
  value: Float,
  valueRange: ClosedFloatingPointRange<Float>,
  onValueChange: (Float) -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = Color.White.copy(alpha = 0.90f),
      )
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF9AC8F5),
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = valueRange,
      modifier = Modifier.height(28.dp),
      colors = SliderDefaults.colors(
        thumbColor = Color(0xFF87CFF9),
        activeTrackColor = Color(0xFF87CFF9),
        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
      ),
    )
  }
}

/**
 * Launches Android's system Live Wallpaper preview/picker targeting [SnowscapeWallpaperService].
 */
private fun launchLiveWallpaperPicker(context: Context) {
  val component = ComponentName(context, SnowscapeWallpaperService::class.java)
  val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(directIntent)
  } catch (_: ActivityNotFoundException) {
    val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
      context.startActivity(fallbackIntent)
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(
        context,
        "Live Wallpaper picker is not available on this device.",
        Toast.LENGTH_LONG,
      ).show()
    }
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun MainScreenPreview() {
  SnowscapeTheme(darkTheme = true) {
    SnowscapeWallpaperScreen(
      settings = SnowscapeSettings(
        palette = StormPalette.MIDNIGHT_SQUALL,
        snowDensity = 1.15f,
        windIntensity = 0.9f,
        puffSoftness = 1.1f,
      ),
      onSettingsChange = {},
      onApplyWallpaper = {},
      initialShowControls = true,
    )
  }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun FullscreenSnowstormPreview() {
  SnowscapeTheme(darkTheme = true) {
    SnowscapeWallpaperScreen(
      settings = SnowscapeSettings(
        palette = StormPalette.TWILIGHT_FLURRY,
        snowDensity = 1.3f,
        windIntensity = 1.1f,
        puffSoftness = 1.25f,
      ),
      onSettingsChange = {},
      onApplyWallpaper = {},
      initialShowControls = false,
    )
  }
}
