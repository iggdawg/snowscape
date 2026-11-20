# Snowscape — Android Snowstorm Live Wallpaper

**Snowscape** is a battery-friendly Android live wallpaper featuring gently falling abstract snow "puffs" drifting across an atmospheric, moody snowstorm background.

## Features

- **Abstract Snowstorm Atmosphere**: Multi-stop winter sky gradients layered with slowly drifting radial storm cloud eddies, undulating diagonal wind & snow-squall ribbons, and a soft horizon snow-mist glow.
- **Multi-Layered Abstract Snow "Puffs"**: Pre-baked soft radial-gradient puff sprites (feathered Gaussian puffs, billowy multi-lobed cloud puffs, and dreamy out-of-focus foreground bokeh puffs) across three depth layers (`DISTANT`, `MIDGROUND`, `FOREGROUND_BOKEH`).
- **Organic Motion & Interactivity**:
  - Gentle vertical drift, rolling wind shear waves, sinusoidal pendulum flutter, and subtle opacity/size breathing.
  - **Touch Wind Gusts**: Touching or dragging across the screen creates a soft swirling wind eddy that gently parts and swirls nearby snow puffs.
  - **Home Screen Parallax**: Swiping across Android launcher pages shifts foreground puffs more than distant layers.
- **Four Selectable Storm Palettes**:
  - **Midnight Squall** — Deep winter night & icy blue wind
  - **Twilight Flurry** — Dusk violet clouds & soft lavender hush
  - **Arctic Whiteout** — Overcast silver gale & dense mist
  - **Aurora Drift** — Boreal emerald-teal currents under polar snow
- **Material You Dynamic Color Support ("Wallpaper colors")**:
  - Implements `WallpaperService.Engine.onComputeColors()` (API 27+) with tailored primary, secondary, and tertiary `WallpaperColors` for every storm palette, plus `HINT_SUPPORTS_DARK_THEME` / `HINT_SUPPORTS_DARK_TEXT` hints on Android 12+ (API 31+).
  - Automatically calls `notifyColorsChanged()` whenever the active palette changes in real time or syncs on visibility changes.
- **Interactive Companion App**:
  - Full-screen live preview with collapsible frosted controls to switch storm palettes, tune snowfall density, wind & storm drift, and puff size/softness, and launch the Android Live Wallpaper picker with one tap.
- **Battery-Efficient Rendering**:
  - Uses `Choreographer` and `SurfaceHolder.lockHardwareCanvas()` (API 26+) and pauses immediately whenever the wallpaper is not visible.

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/example/snowscape/
│   ├── MainActivity.kt
│   ├── ui/main/MainScreen.kt                 # Live preview & customization companion UI
│   └── wallpaper/
│       ├── SnowscapeConfig.kt                # StormPalette presets, Material You colors & SharedPreferences
│       ├── SnowstormSimulation.kt            # 3-layer puff physics, wind waves & touch swirls
│       ├── SnowstormRenderer.kt              # Hardware-accelerated Canvas renderer & puff sprites
│       └── SnowscapeWallpaperService.kt      # WallpaperService & Engine lifecycle + onComputeColors()
└── res/
    ├── drawable/ic_wallpaper_thumbnail.xml   # Vector thumbnail for the Android wallpaper picker
    └── xml/snowscape_wallpaper.xml           # Live wallpaper metadata & settings activity link
```

## Building & Installing

### Build Debug APK
```bash
./gradlew assembleDebug
```
On Windows:
```powershell
.\gradlew.bat assembleDebug
```

A pre-built debug APK is also copied to the project root as `Snowscape.apk`.

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Install on a Device or Emulator
```bash
adb install -r Snowscape.apk
```
Then open **Snowscape** from your app drawer and tap **Set Live Wallpaper** (or select **Snowscape Live Wallpaper** in your device's **Wallpaper & style** settings).

## Requirements

- Android 7.0 (API 24) or higher
- Material You dynamic wallpaper colors require Android 8.1+ (API 27+) and color hints require Android 12+ (API 31+)
