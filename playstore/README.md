# Play Store assets

Drop the following files here before submitting to the Play Console. They are
**not** required for `bundleRelease` — they go straight into the store listing.

## Required

| File | Spec | Used for |
|------|------|----------|
| `ic_launcher_512.png` | 512 × 512 px, 32-bit PNG with alpha, ≤ 1 MB | High-res app icon in the store listing |
| `feature_graphic.png` | 1024 × 500 px, 24-bit PNG or JPEG, no alpha, ≤ 1 MB | Banner shown above screenshots on the listing |
| `phone_screenshot_1.png` … `phone_screenshot_8.png` | 16:9 or 9:16, min 320 px shortest side, max 3840 px longest side | At least 2, up to 8. Phones only is fine for v1. |

## How to generate the launcher PNG

The app already has an adaptive launcher icon (`res/drawable/ic_pollen.xml`
on a `#2E6B1A` background). To export the 512×512 PNG:

1. Android Studio → right-click `app/src/main/res` → New → Image Asset
2. Icon type: **Launcher Icons (Adaptive and Legacy)**
3. Foreground layer: select `ic_pollen` (Clip Art) or import the existing vector
4. Background layer: solid color `#2E6B1A`
5. Click **Next** → **Finish**. Android Studio writes the adaptive icon XMLs
   *and* a `playstore-icon.png` next to the build outputs. Move that file here
   as `ic_launcher_512.png`.

## Data Safety form

See [`DATA_SAFETY.md`](./DATA_SAFETY.md) for the exact answers to paste into
Play Console → App content → Data safety.

## Privacy policy URL

The policy lives at [`/PRIVACY.md`](../PRIVACY.md). Host it (GitHub Pages is
free and fine) and put that public URL into Play Console → Store listing →
Privacy policy.
