package com.example.snowscape.wallpaper

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder

/**
 * Android Live Wallpaper service that renders gently falling abstract snow puffs
 * against an atmospheric snowstorm background.
 */
class SnowscapeWallpaperService : WallpaperService() {

  override fun onCreateEngine(): Engine = SnowscapeEngine()

  private inner class SnowscapeEngine :
    Engine(),
    Choreographer.FrameCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var preferences: SnowscapePreferences
    private lateinit var simulation: SnowstormSimulation
    private val renderer = SnowstormRenderer()
    private val choreographer = Choreographer.getInstance()

    private var hasSurface = false
    private var isLoopScheduled = false
    private var lastFrameNanos = 0L
    private var lastReportedPalette: StormPalette? = null

    override fun onCreate(surfaceHolder: SurfaceHolder) {
      super.onCreate(surfaceHolder)
      setTouchEventsEnabled(true)

      preferences = SnowscapePreferences(applicationContext)
      val initialSettings = preferences.load()
      lastReportedPalette = initialSettings.palette

      val displayMetrics = resources.displayMetrics
      simulation = SnowstormSimulation(
        seed = System.nanoTime(),
        initialSettings = initialSettings,
      ).apply {
        setViewport(
          width = displayMetrics.widthPixels.coerceAtLeast(1080),
          height = displayMetrics.heightPixels.coerceAtLeast(1920),
          displayDensity = displayMetrics.density,
        )
        // Pre-warm the simulation so puffs are naturally distributed on first frame
        step(0.016f)
      }
      preferences.registerListener(this)
    }

    override fun onDestroy() {
      stopRenderLoop()
      if (::preferences.isInitialized) {
        preferences.unregisterListener(this)
      }
      super.onDestroy()
    }

    override fun onComputeColors(): WallpaperColors? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
      val palette = when {
        ::simulation.isInitialized -> simulation.settings.palette
        ::preferences.isInitialized -> preferences.load().palette
        else -> SnowscapePreferences(applicationContext).load().palette
      }
      lastReportedPalette = palette
      return palette.toWallpaperColors()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
      syncSettingsAndNotifyColors()
    }

    override fun onVisibilityChanged(visible: Boolean) {
      super.onVisibilityChanged(visible)
      if (visible) {
        syncSettingsAndNotifyColors()
        startRenderLoop()
      } else {
        stopRenderLoop()
      }
    }

    private fun syncSettingsAndNotifyColors() {
      if (!::preferences.isInitialized || !::simulation.isInitialized) return
      val updated = preferences.load()
      simulation.updateSettings(updated)
      if (updated.palette != lastReportedPalette) {
        lastReportedPalette = updated.palette
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
          notifyColorsChanged()
        }
      }
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      hasSurface = true
      if (isVisible) {
        startRenderLoop()
      }
    }

    override fun onSurfaceChanged(
      holder: SurfaceHolder,
      format: Int,
      width: Int,
      height: Int,
    ) {
      super.onSurfaceChanged(holder, format, width, height)
      hasSurface = width > 0 && height > 0
      simulation.setViewport(width, height, resources.displayMetrics.density)
      if (isVisible && hasSurface) {
        drawFrame(0.016f)
        startRenderLoop()
      }
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
      hasSurface = false
      stopRenderLoop()
      super.onSurfaceDestroyed(holder)
    }

    override fun onOffsetsChanged(
      xOffset: Float,
      yOffset: Float,
      xOffsetStep: Float,
      yOffsetStep: Float,
      xPixelOffset: Int,
      yPixelOffset: Int,
    ) {
      super.onOffsetsChanged(
        xOffset,
        yOffset,
        xOffsetStep,
        yOffsetStep,
        xPixelOffset,
        yPixelOffset,
      )
      simulation.setParallaxOffset(xOffset)
    }

    override fun onTouchEvent(event: MotionEvent) {
      super.onTouchEvent(event)
      if (event.actionMasked == MotionEvent.ACTION_DOWN ||
        event.actionMasked == MotionEvent.ACTION_MOVE
      ) {
        simulation.applyTouchGust(event.x, event.y)
      }
    }

    override fun doFrame(frameTimeNanos: Long) {
      isLoopScheduled = false
      if (!isVisible || !hasSurface) return

      val dtSeconds = if (lastFrameNanos == 0L) {
        0.016f
      } else {
        ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.050f)
      }
      lastFrameNanos = frameTimeNanos

      drawFrame(dtSeconds)
      scheduleNextFrame()
    }

    private fun drawFrame(dtSeconds: Float) {
      if (!hasSurface) return
      simulation.step(dtSeconds)

      val holder = surfaceHolder ?: return
      var canvas: Canvas? = null
      try {
        canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          try {
            holder.lockHardwareCanvas()
          } catch (_: Exception) {
            holder.lockCanvas()
          }
        } else {
          holder.lockCanvas()
        }
        if (canvas != null) {
          renderer.draw(canvas, simulation)
        }
      } catch (_: Exception) {
        // Surface may have been released concurrently during wallpaper switch
      } finally {
        if (canvas != null) {
          try {
            holder.unlockCanvasAndPost(canvas)
          } catch (_: Exception) {
            // Ignore unlock failure if surface was destroyed
          }
        }
      }
    }

    private fun startRenderLoop() {
      if (!isVisible || !hasSurface) return
      lastFrameNanos = 0L
      scheduleNextFrame()
    }

    private fun scheduleNextFrame() {
      if (!isLoopScheduled && isVisible && hasSurface) {
        isLoopScheduled = true
        choreographer.postFrameCallback(this)
      }
    }

    private fun stopRenderLoop() {
      if (isLoopScheduled) {
        choreographer.removeFrameCallback(this)
        isLoopScheduled = false
      }
      lastFrameNanos = 0L
    }
  }
}
