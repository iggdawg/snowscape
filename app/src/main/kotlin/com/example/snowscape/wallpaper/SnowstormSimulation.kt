package com.example.snowscape.wallpaper

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Depth layers for the abstract snow puffs, providing parallax and depth-of-field.
 */
enum class PuffLayer {
  DISTANT,
  MIDGROUND,
  FOREGROUND_BOKEH,
}

/**
 * State of a single abstract snow puff particle.
 */
class SnowPuff(
  var x: Float,
  var y: Float,
  val layer: PuffLayer,
  val depth: Float,
  val baseRadiusDp: Float,
  val baseAlpha: Float,
  val fallSpeedDpPerSec: Float,
  val swayAmplitudeDpPerSec: Float,
  val swayFrequency: Float,
  val swayPhase: Float,
  val breathingPhase: Float,
  val breathingSpeed: Float,
  val spriteVariant: Int,
  val squashRatio: Float,
  var vxImpulse: Float = 0f,
  var vyImpulse: Float = 0f,
)

/**
 * Parameters for an abstract background storm ribbon (sweeping bands of wind, fog, and snow squalls).
 */
data class StormRibbon(
  val verticalCenterRatio: Float,
  val thicknessRatio: Float,
  val tiltRatio: Float,
  val waveAmplitudeRatio: Float,
  val waveFrequency: Float,
  val speed: Float,
  val phaseOffset: Float,
  val alpha: Float,
  val useSecondaryColor: Boolean,
)

/**
 * Pure Kotlin simulation of gently falling abstract snow puffs and sweeping background storm currents.
 */
class SnowstormSimulation(
  seed: Long = 20261120L,
  initialSettings: SnowscapeSettings = SnowscapeSettings(),
) {
  private val random = Random(seed)

  var settings: SnowscapeSettings = initialSettings
    private set

  var widthPx: Int = 1080
    private set
  var heightPx: Int = 2400
    private set
  var density: Float = 2.75f
    private set

  var timeSeconds: Float = 0f
    private set

  /**
   * Normalized launcher home-screen horizontal scroll offset (0f..1f).
   */
  var parallaxOffsetX: Float = 0.5f
    private set

  @PublishedApi
  internal val puffs = ArrayList<SnowPuff>(MAX_PUFFS)

  /**
   * Active activePuffCount depends on [SnowscapeSettings.snowDensity].
   */
  val activePuffCount: Int
    get() = (BASE_PUFF_COUNT * settings.snowDensity).roundToInt().coerceIn(45, MAX_PUFFS)

  /**
   * Layered abstract storm ribbons that drift and billow across the background sky.
   */
  val stormRibbons: List<StormRibbon> = listOf(
    StormRibbon(
      verticalCenterRatio = 0.20f,
      thicknessRatio = 0.22f,
      tiltRatio = 0.14f,
      waveAmplitudeRatio = 0.055f,
      waveFrequency = 1.2f,
      speed = 0.28f,
      phaseOffset = 0.4f,
      alpha = 0.18f,
      useSecondaryColor = false,
    ),
    StormRibbon(
      verticalCenterRatio = 0.38f,
      thicknessRatio = 0.26f,
      tiltRatio = 0.18f,
      waveAmplitudeRatio = 0.070f,
      waveFrequency = 0.95f,
      speed = 0.36f,
      phaseOffset = 1.9f,
      alpha = 0.22f,
      useSecondaryColor = true,
    ),
    StormRibbon(
      verticalCenterRatio = 0.56f,
      thicknessRatio = 0.28f,
      tiltRatio = 0.16f,
      waveAmplitudeRatio = 0.065f,
      waveFrequency = 1.1f,
      speed = 0.44f,
      phaseOffset = 3.5f,
      alpha = 0.20f,
      useSecondaryColor = false,
    ),
    StormRibbon(
      verticalCenterRatio = 0.75f,
      thicknessRatio = 0.32f,
      tiltRatio = 0.12f,
      waveAmplitudeRatio = 0.080f,
      waveFrequency = 0.85f,
      speed = 0.52f,
      phaseOffset = 5.1f,
      alpha = 0.25f,
      useSecondaryColor = true,
    ),
    StormRibbon(
      verticalCenterRatio = 0.90f,
      thicknessRatio = 0.25f,
      tiltRatio = 0.08f,
      waveAmplitudeRatio = 0.050f,
      waveFrequency = 1.35f,
      speed = 0.32f,
      phaseOffset = 2.7f,
      alpha = 0.22f,
      useSecondaryColor = false,
    ),
  )

  init {
    populatePuffs()
  }

  fun updateSettings(newSettings: SnowscapeSettings) {
    settings = newSettings
  }

  fun setViewport(width: Int, height: Int, displayDensity: Float) {
    if (width <= 0 || height <= 0) return
    val oldWidth = widthPx
    val oldHeight = heightPx
    widthPx = width
    heightPx = height
    density = displayDensity.coerceAtLeast(1f)

    if (puffs.isEmpty()) {
      populatePuffs()
    } else if (oldWidth != width || oldHeight != height) {
      val scaleX = width.toFloat() / oldWidth.toFloat()
      val scaleY = height.toFloat() / oldHeight.toFloat()
      for (i in 0 until puffs.size) {
        val p = puffs[i]
        p.x *= scaleX
        p.y *= scaleY
      }
    }
  }

  fun setParallaxOffset(xOffset: Float) {
    parallaxOffsetX = xOffset.coerceIn(0f, 1f)
  }

  /**
   * Imparts a soft, swirling wind gust around a user's touch point.
   */
  fun applyTouchGust(touchX: Float, touchY: Float) {
    val influenceRadiusPx = 150f * density
    val maxImpulsePx = 140f * density
    val count = activePuffCount
    for (i in 0 until count) {
      val puff = puffs[i]
      val dx = puff.x - touchX
      val dy = puff.y - touchY
      val dist = hypot(dx, dy)
      if (dist < influenceRadiusPx && dist > 1f) {
        val falloff = (1f - dist / influenceRadiusPx)
        val strength = falloff * falloff * maxImpulsePx * (0.4f + 0.6f * puff.depth)
        val nx = dx / dist
        val ny = dy / dist
        // Combine radial push with a gentle upward/swirling vortex
        puff.vxImpulse = (puff.vxImpulse + (nx * 0.75f - ny * 0.35f) * strength)
          .coerceIn(-maxImpulsePx * 1.5f, maxImpulsePx * 1.5f)
        puff.vyImpulse = (puff.vyImpulse + (ny * 0.65f + nx * 0.25f - 0.25f) * strength)
          .coerceIn(-maxImpulsePx * 1.5f, maxImpulsePx * 1.5f)
      }
    }
  }

  /**
   * Steps the snowstorm simulation forward by [deltaSeconds].
   */
  fun step(deltaSeconds: Float) {
    val dt = deltaSeconds.coerceIn(0.001f, 0.064f)
    timeSeconds += dt

    val w = widthPx.toFloat()
    val h = heightPx.toFloat()
    val d = density
    val windScale = settings.windIntensity
    val puffScale = settings.puffSoftness

    // Smooth global wind gust that rises and subsides like breathing storm air
    val globalGustDpPerSec =
      (12f + 16f * sin(timeSeconds * 0.32f) + 9f * cos(timeSeconds * 0.71f)) * windScale

    val dragFactor = exp(-dt * 2.6f)
    val count = activePuffCount

    for (i in 0 until count) {
      val puff = puffs[i]
      val yNorm = (puff.y / h).coerceIn(0f, 1f)

      // Layered shear wind: varies gently with altitude and time
      val shearWindDpPerSec =
        sin(timeSeconds * 0.48f + yNorm * 3.1f + puff.swayPhase * 0.3f) * 11f * windScale

      // Individual feather/puff horizontal flutter
      val flutterDpPerSec =
        sin(timeSeconds * puff.swayFrequency + puff.swayPhase) * puff.swayAmplitudeDpPerSec

      val vxDp =
        (globalGustDpPerSec + shearWindDpPerSec) * (0.35f + 0.65f * puff.depth) + flutterDpPerSec

      // Vertical velocity slows slightly during strong lateral gusts for an airy float effect
      val updraftMod = 1f + 0.08f * sin(timeSeconds * 0.85f + puff.swayPhase)
      val vyDp = puff.fallSpeedDpPerSec * updraftMod * (0.85f + 0.15f * windScale)

      puff.x += (vxDp * d + puff.vxImpulse) * dt
      puff.y += (vyDp * d + puff.vyImpulse) * dt

      puff.vxImpulse *= dragFactor
      puff.vyImpulse *= dragFactor

      val marginPx = (puff.baseRadiusDp * puffScale * d * 2.2f).coerceAtLeast(36f * d)

      // Wrap around horizontally and vertically so the snowstorm is seamless
      if (puff.y > h + marginPx) {
        puff.y = -marginPx * random.nextFloat().coerceAtLeast(0.15f)
        puff.x = random.nextFloat() * (w + marginPx * 2f) - marginPx
        puff.vxImpulse = 0f
        puff.vyImpulse = 0f
      } else if (puff.y < -marginPx * 1.5f) {
        puff.y = -marginPx * 0.5f
      }

      if (puff.x > w + marginPx) {
        puff.x = -marginPx + (puff.x - (w + marginPx)) % (w + marginPx * 2f)
      } else if (puff.x < -marginPx) {
        puff.x = w + marginPx - ((-marginPx - puff.x) % (w + marginPx * 2f))
      }
    }
  }

  /**
   * Iterates over the currently active puffs in back-to-front depth order.
   */
  inline fun forEachActivePuff(action: (SnowPuff) -> Unit) {
    val count = activePuffCount
    // Render distant puffs first, then midground, then foreground bokeh for proper painter's order
    for (layer in PuffLayer.entries) {
      for (i in 0 until count) {
        val puff = puffs[i]
        if (puff.layer == layer) {
          action(puff)
        }
      }
    }
  }

  private fun populatePuffs() {
    puffs.clear()
    for (i in 0 until MAX_PUFFS) {
      val roll = random.nextFloat()
      val layer = when {
        roll < 0.54f -> PuffLayer.DISTANT
        roll < 0.88f -> PuffLayer.MIDGROUND
        else -> PuffLayer.FOREGROUND_BOKEH
      }
      puffs.add(createPuff(layer, i))
    }
  }

  private fun createPuff(layer: PuffLayer, index: Int): SnowPuff {
    val depth: Float
    val radiusDp: Float
    val alpha: Float
    val fallSpeedDp: Float
    val swayAmpDp: Float
    val swayFreq: Float
    val spriteVariant: Int

    when (layer) {
      PuffLayer.DISTANT -> {
        depth = nextFloatIn(0.15f, 0.38f)
        radiusDp = nextFloatIn(2.6f, 5.8f)
        alpha = nextFloatIn(0.24f, 0.52f)
        fallSpeedDp = nextFloatIn(18f, 34f)
        swayAmpDp = nextFloatIn(6f, 15f)
        swayFreq = nextFloatIn(0.7f, 1.5f)
        spriteVariant = index % 2
      }
      PuffLayer.MIDGROUND -> {
        depth = nextFloatIn(0.42f, 0.74f)
        radiusDp = nextFloatIn(6.5f, 14.5f)
        alpha = nextFloatIn(0.48f, 0.85f)
        fallSpeedDp = nextFloatIn(34f, 62f)
        swayAmpDp = nextFloatIn(12f, 26f)
        swayFreq = nextFloatIn(0.9f, 1.9f)
        spriteVariant = index % 3
      }
      PuffLayer.FOREGROUND_BOKEH -> {
        depth = nextFloatIn(0.78f, 1.0f)
        radiusDp = nextFloatIn(17f, 36f)
        alpha = nextFloatIn(0.15f, 0.34f)
        fallSpeedDp = nextFloatIn(55f, 86f)
        swayAmpDp = nextFloatIn(16f, 32f)
        swayFreq = nextFloatIn(0.6f, 1.3f)
        spriteVariant = 2 // Dreamy bokeh variant
      }
    }

    return SnowPuff(
      x = random.nextFloat() * widthPx,
      y = random.nextFloat() * heightPx,
      layer = layer,
      depth = depth,
      baseRadiusDp = radiusDp,
      baseAlpha = alpha,
      fallSpeedDpPerSec = fallSpeedDp,
      swayAmplitudeDpPerSec = swayAmpDp,
      swayFrequency = swayFreq,
      swayPhase = nextFloatIn(0f, TWO_PI),
      breathingPhase = nextFloatIn(0f, TWO_PI),
      breathingSpeed = nextFloatIn(0.8f, 2.1f),
      spriteVariant = spriteVariant,
      squashRatio = nextFloatIn(0.88f, 1.14f),
    )
  }

  private fun nextFloatIn(min: Float, max: Float): Float =
    min + random.nextFloat() * (max - min)

  companion object {
    const val BASE_PUFF_COUNT = 135
    const val MAX_PUFFS = 270
    private const val TWO_PI = (Math.PI * 2.0).toFloat()
  }
}
