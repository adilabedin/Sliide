# Brand assets

`generate_brand_assets.py` holds the Sliide logo mark (a rounded-stroke champagne "S") as one set of Bézier
constants on the 108×108 adaptive-icon grid, and generates every platform asset from it:

| Output | Used for |
|---|---|
| `androidApp/src/main/res/drawable/ic_logo_mark.xml` | Adaptive-icon foreground, monochrome (themed) icon layer, splash icon |
| `iosApp/iosApp/Assets.xcassets/LaunchLogo.imageset/launch-logo@{1,2,3}x.png` | iOS launch-screen logo (120 pt) |
| `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024{,-dark,-tinted}.png` | iOS app icon (light, dark, tinted) |

It also fails if the mark's contrast against either splash background (Midnight, Ink900) drops below 3:1.

The outputs are committed; the Gradle and Xcode builds never run this script. Re-run it only after changing the mark
or its colours (the hex values mirror `core/designsystem/.../theme/Color.kt`).

```bash
pip3 install pillow
python3 -I tools/brand/generate_brand_assets.py
```
