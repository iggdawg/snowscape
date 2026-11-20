package com.example.snowscape.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Hardware-accelerated [Canvas] renderer for the Snowscape live wallpaper and Compose preview.
 *
 * Renders:
 * 1. A rich multi-stop winter storm sky gradient.
 * 2. Slowly drifting abstract storm fog eddies and horizon snow mist.
 * 3. Undulating diagonal wind & snow-squall ribbons that capture the motion of a snowstorm.
 * 4. Multi-layered, soft-feathered abstract snow "puffs" with depth-of-field bokeh and parallax.
 */
class SnowstormRenderer {

  private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val eddyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
  }
  private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

  private val ribbonPath = Path()
  private val dstRect = RectF()

  private var cachedWidth: Int = -1
  private var cachedHeight: Int = -1
  private var cachedPalette: StormPalette? = null

  private var puffSprites: Array<Bitmap>? = null

  /**
   * Draws the current state of [simulation] onto [canvas].
   */
  fun draw(canvas: Canvas, simulation: SnowstormSimulation) {
    val width = canvas.width.takeIf { it > 0 } ?: simulation.widthPx
    val height = canvas.height.takeIf { it > 0 } ?: simulation.heightPx
    if (width <= 0 || height <= 0) return

    val settings = simulation.settings
    val palette = settings.palette

    ensureResources(width, height, palette)

    val time = simulation.timeSeconds
    val parallaxNormalized = simulation.parallaxOffsetX - 0.5f // -0.5f..+0.5f
    val maxParallaxPx = width * 0.14f

    // 1. Base abstract winter storm sky gradient
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

    // 2. Soft drifting atmospheric storm cloud eddies
    drawStormEddies(canvas, width.toFloat(), height.toFloat(), time, palette, parallaxNormalized)

    // 3. Sweeping abstract wind & snow-squall ribbons
    drawStormRibbons(
      canvas = canvas,
      width = width.toFloat(),
      height = height.toFloat(),
      time = time,
      windIntensity = settings.windIntensity,
      palette = palette,
      ribbons = simulation.stormRibbons,
      parallaxOffsetPx = -parallaxNormalized * maxParallaxPx * 0.35f,
    )

    // 4. Horizon snow-mist glow at the bottom
    canvas.drawRect(0f, height * 0.45f, width.toFloat(), height.toFloat(), mistPaint)

    // 5. Gently falling abstract snow puffs (back-to-front depth order)
    val sprites = puffSprites ?: return
    val puffScale = settings.puffSoftness
    val density = simulation.density

    simulation.forEachActivePuff { puff ->
      val breathe = 1f + 0.08f * sin(time * puff.breathingSpeed + puff.breathingPhase)
      val radiusPx = puff.baseRadiusDp * puffScale * density * breathe

      // Parallax shift: closer puffs shift more when swiping home screen pages
      val parallaxShiftX = -parallaxNormalized * maxParallaxPx * (0.25f + 0.95f * puff.depth)
      val drawX = wrapCoordinate(puff.x + parallaxShiftX, width.toFloat(), radiusPx * 2f)
      val drawY = puff.y

      val rx = radiusPx * puff.squashRatio
      val ry = radiusPx / puff.squashRatio

      val shimmer = 0.90f + 0.10f * cos(time * (puff.breathingSpeed * 0.8f) + puff.swayPhase)
      val alphaInt = (puff.baseAlpha * shimmer * 255f).roundToInt().coerceIn(10, 255)
      puffPaint.alpha = alphaInt

      val sprite = sprites[puff.spriteVariant.coerceIn(0, sprites.lastIndex)]
      dstRect.set(drawX - rx, drawY - ry, drawX + rx, drawY + ry)
      canvas.drawBitmap(sprite, null, dstRect, puffPaint)
    }
  }

  private fun drawStormEddies(
    canvas: Canvas,
    width: Float,
    height: Float,
    time: Float,
    palette: StormPalette,
    parallaxNormalized: Float,
  ) {
    // Three large, soft radial mist clouds drifting slowly in the upper, mid, and lower sky
    val eddies = 3
    for (i in 0 until eddies) {
      val phase = i * 2.1f
      val cx = width * (0.5f + 0.32f * sin(time * 0.11f + phase)) - parallaxNormalized * width * 0.05f
      val cy = height * (0.22f + i * 0.27f + 0.06f * cos(time * 0.09f + phase))
      val radius = maxOf(width, height) * (0.48f + 0.08f * sin(time * 0.15f + i))

      val baseColor = if (i % 2 == 0) palette.ribbonPrimaryColor else palette.ribbonSecondaryColor
      val centerColor = withAlpha(baseColor, 38)
      val midColor = withAlpha(baseColor, 16)

      eddyPaint.shader = RadialGradient(
        cx,
        cy,
        radius,
        intArrayOf(centerColor, midColor, Color.TRANSPARENT),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
      )
      canvas.drawCircle(cx, cy, radius, eddyPaint)
    }
  }

  private fun drawStormRibbons(
    canvas: Canvas,
    width: Float,
    height: Float,
    time: Float,
    windIntensity: Float,
    palette: StormPalette,
    ribbons: List<StormRibbon>,
    parallaxOffsetPx: Float,
  ) {
    val steps = 16
    val extraSpan = width * 0.25f
    val startX = -extraSpan
    val endX = width + extraSpan
    val totalSpan = endX - startX

    for (ribbon in ribbons) {
      val baseY = height * ribbon.verticalCenterRatio
      val halfThickness = height * ribbon.thicknessRatio * 0.5f
      val tiltTotal = height * ribbon.tiltRatio * (0.6f + 0.4f * windIntensity)
      val waveAmp = height * ribbon.waveAmplitudeRatio * (0.75f + 0.25f * windIntensity)
      val phase = time * ribbon.speed * (0.65f + 0.35f * windIntensity) + ribbon.phaseOffset

      ribbonPath.reset()

      // Top edge of the abstract storm ribbon
      for (step in 0..steps) {
        val fraction = step.toFloat() / steps.toFloat()
        val x = startX + fraction * totalSpan + parallaxOffsetPx
        val wave =
          sin(fraction * ribbon.waveFrequency * PI_F * 2f - phase) * waveAmp +
            cos(fraction * PI_F - phase * 0.6f) * (waveAmp * 0.45f)
        val thicknessPulse = 1f + 0.22f * sin(fraction * PI_F * 2f + phase * 0.8f)
        val centerY = baseY + (fraction - 0.5f) * tiltTotal + wave
        val topY = centerY - halfThickness * thicknessPulse
        if (step == 0) {
          ribbonPath.moveTo(x, topY)
        } else {
          ribbonPath.lineTo(x, topY)
        }
      }

      // Bottom edge of the abstract storm ribbon (reverse direction)
      for (step in steps downTo 0) {
        val fraction = step.toFloat() / steps.toFloat()
        val x = startX + fraction * totalSpan + parallaxOffsetPx
        val wave =
          sin(fraction * ribbon.waveFrequency * PI_F * 2f - phase) * waveAmp +
            cos(fraction * PI_F - phase * 0.6f) * (waveAmp * 0.45f)
        val thicknessPulse = 1f + 0.22f * cos(fraction * PI_F * 2f - phase * 0.5f)
        val centerY = baseY + (fraction - 0.5f) * tiltTotal + wave
        val bottomY = centerY + halfThickness * thicknessPulse
        ribbonPath.lineTo(x, bottomY)
      }
      ribbonPath.close()

      val rawColor =
        if (ribbon.useSecondaryColor) palette.ribbonSecondaryColor else palette.ribbonPrimaryColor
      val peakAlpha = (ribbon.alpha * 255f).roundToInt().coerceIn(12, 95)
      val midColor = withAlpha(rawColor, peakAlpha)
      val softColor = withAlpha(rawColor, peakAlpha / 2)

      val shaderTopY = baseY - halfThickness * 1.6f - kotlin.math.abs(tiltTotal) * 0.5f
      val shaderBottomY = baseY + halfThickness * 1.6f + kotlin.math.abs(tiltTotal) * 0.5f

      ribbonPaint.shader = LinearGradient(
        0f,
        shaderTopY,
        0f,
        shaderBottomY,
        intArrayOf(
          Color.TRANSPARENT,
          softColor,
          midColor,
          softColor,
          Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
      )

      canvas.drawPath(ribbonPath, ribbonPaint)
    }
  }

  private fun ensureResources(width: Int, height: Int, palette: StormPalette) {
    if (width != cachedWidth || height != cachedHeight || palette != cachedPalette) {
      cachedWidth = width
      cachedHeight = height

      skyPaint.shader = LinearGradient(
        0f,
        0f,
        width * 0.18f,
        height.toFloat(),
        intArrayOf(
          palette.skyTopColor,
          palette.skyUpperMidColor,
          palette.skyLowerMidColor,
          palette.skyBottomColor,
        ),
        floatArrayOf(0f, 0.34f, 0.70f, 1f),
        Shader.TileMode.CLAMP,
      )

      mistPaint.shader = LinearGradient(
        0f,
        height * 0.45f,
        0f,
        height.toFloat(),
        intArrayOf(
          Color.TRANSPARENT,
          withAlpha(palette.horizonMistColor, 28),
          withAlpha(palette.horizonMistColor, 78),
        ),
        floatArrayOf(0f, 0.55f, 1f),
        Shader.TileMode.CLAMP,
      )
    }

    if (palette != cachedPalette || puffSprites == null) {
      cachedPalette = palette
      puffSprites?.forEach { it.recycle() }
      puffSprites = arrayOf(
        createFeatheredPuffBitmap(palette, SPRITE_SIZE_PX),
        createCloudBillowPuffBitmap(palette, SPRITE_SIZE_PX),
        createBokehPuffBitmap(palette, SPRITE_SIZE_PX),
      )
    }
  }

  /**
   * Sprite 0: Soft Gaussian-like feathered snow puff with a luminous core and airy halo.
   */
  private fun createFeatheredPuffBitmap(palette: StormPalette, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size * 0.5f
    val radius = size * 0.48f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        center,
        center,
        radius,
        intArrayOf(
          withAlpha(palette.puffCoreColor, 250),
          withAlpha(palette.puffCoreColor, 210),
          withAlpha(palette.puffHaloColor, 115),
          withAlpha(palette.puffHaloColor, 34),
          Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.22f, 0.52f, 0.78f, 1f),
        Shader.TileMode.CLAMP,
      )
    }
    canvas.drawCircle(center, center, radius, paint)
    return bitmap
  }

  /**
   * Sprite 1: Organic, billowy multi-lobed snow puff built from overlapping soft radial puffs.
   */
  private fun createCloudBillowPuffBitmap(palette: StormPalette, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val lobes = arrayOf(
      Triple(size * 0.45f, size * 0.48f, size * 0.39f),
      Triple(size * 0.58f, size * 0.46f, size * 0.34f),
      Triple(size * 0.50f, size * 0.57f, size * 0.33f),
    )

    for ((cx, cy, r) in lobes) {
      paint.shader = RadialGradient(
        cx,
        cy,
        r,
        intArrayOf(
          withAlpha(palette.puffCoreColor, 175),
          withAlpha(palette.puffHaloColor, 95),
          withAlpha(palette.puffHaloColor, 24),
          Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.45f, 0.78f, 1f),
        Shader.TileMode.CLAMP,
      )
      canvas.drawCircle(cx, cy, r, paint)
    }
    return bitmap
  }

  /**
   * Sprite 2: Dreamy, wide-diffused out-of-focus bokeh snow puff.
   */
  private fun createBokehPuffBitmap(palette: StormPalette, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size * 0.5f
    val radius = size * 0.49f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(
        center,
        center,
        radius,
        intArrayOf(
          withAlpha(palette.puffCoreColor, 190),
          withAlpha(palette.puffHaloColor, 155),
          withAlpha(palette.puffHaloColor, 85),
          withAlpha(palette.puffHaloColor, 22),
          Color.TRANSPARENT,
        ),
        floatArrayOf(0f, 0.35f, 0.65f, 0.86f, 1f),
        Shader.TileMode.CLAMP,
      )
    }
    canvas.drawCircle(center, center, radius, paint)
    return bitmap
  }

  private fun wrapCoordinate(value: Float, span: Float, margin: Float): Float {
    val total = span + margin * 2f
    if (total <= 0f) return value
    var shifted = (value + margin) % total
    if (shifted < 0f) shifted += total
    return shifted - margin
  }

  private fun withAlpha(color: Int, alpha: Int): Int {
    val clamped = alpha.coerceIn(0, 255)
    return (color and 0x00FFFFFF) or (clamped shl 24)
  }

  companion object {
    private const val SPRITE_SIZE_PX = 128
    private const val PI_F = Math.PI.toFloat()
  }
}
