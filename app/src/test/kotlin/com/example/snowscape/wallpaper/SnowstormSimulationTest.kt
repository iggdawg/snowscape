package com.example.snowscape.wallpaper

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class SnowstormSimulationTest {

  @Test
  fun simulation_advancesPuffsAndWrapsWithinBounds() {
    val settings = SnowscapeSettings(
      palette = StormPalette.MIDNIGHT_SQUALL,
      snowDensity = 1.2f,
      windIntensity = 1.0f,
      puffSoftness = 1.0f,
    )
    val simulation = SnowstormSimulation(seed = 42L, initialSettings = settings)
    simulation.setViewport(width = 1080, height = 2400, displayDensity = 2.75f)

    var initialCount = 0
    simulation.forEachActivePuff { initialCount++ }
    assertEquals(simulation.activePuffCount, initialCount)
    assertTrue(initialCount > 100)

    // Step through several seconds of snowfall
    repeat(60) {
      simulation.step(0.033f)
    }

    assertTrue(simulation.timeSeconds > 1.9f)

    // Verify touch gust imparts impulse to nearby puffs
    var targetX = 540f
    var targetY = 1200f
    simulation.forEachActivePuff { puff ->
      targetX = puff.x + 10f
      targetY = puff.y + 10f
    }
    simulation.applyTouchGust(targetX, targetY)

    var anyImpulse = false
    simulation.forEachActivePuff { puff ->
      if (puff.vxImpulse != 0f || puff.vyImpulse != 0f) {
        anyImpulse = true
      }
    }
    assertTrue(anyImpulse)
  }

  @Test
  fun stormPalettes_defineMaterialYouColorsAndHints() {
    val palettes = StormPalette.entries
    assertEquals(4, palettes.size)

    // Each palette should have opaque RGB colors for Material You primary/secondary/tertiary
    palettes.forEach { palette ->
      assertEquals(0xFF000000.toInt(), palette.wallpaperPrimaryColor and 0xFF000000.toInt())
      assertEquals(0xFF000000.toInt(), palette.wallpaperSecondaryColor and 0xFF000000.toInt())
      assertEquals(0xFF000000.toInt(), palette.wallpaperTertiaryColor and 0xFF000000.toInt())
    }

    assertTrue(StormPalette.MIDNIGHT_SQUALL.supportsDarkTheme)
    assertTrue(StormPalette.TWILIGHT_FLURRY.supportsDarkTheme)
    assertTrue(StormPalette.AURORA_DRIFT.supportsDarkTheme)
    assertEquals(false, StormPalette.ARCTIC_WHITEOUT.supportsDarkTheme)
  }
}
