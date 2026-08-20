# Implementation Plan - Light and Dark Mode Support

Fix the app's theme to support both light and dark modes based on the system theme. Currently, the app is hardcoded to dark mode via its base theme and color definitions.

## Proposed Changes

### [Resource Management]

#### [MODIFY] [colors.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/values/colors.xml)
Update default colors to follow a Light Mode palette.
- Change `background_dark` to a light color (e.g., `#FFFFFF`).
- Change `surface_dark` to a light color (e.g., `#F5F5F5`).
- Change `text_primary` to a dark color (e.g., `#212121`).
- Change `text_secondary` to a medium gray (e.g., `#757575`).
- Change `divider_color` to a light gray (e.g., `#E0E0E0`).

#### [NEW] [colors.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/values-night/colors.xml)
Create a new color file for Dark Mode, using the original dark values.
- `background_dark`: `#000000`
- `surface_dark`: `#1E1E1E`
- `text_primary`: `#FFFFFF`
- `text_secondary`: `#B0B0B0`
- `divider_color`: `#2C2C2C`

#### [MODIFY] [themes.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/values/themes.xml)
- Change `Base.Theme.Resilio` parent to `Theme.Material3.DayNight.NoActionBar`.
- Ensure all theme items point to the semantic color names.
- Update `android:windowBackground` and other system-level attributes.

#### [DELETE] [themes.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/values-night/themes.xml)
Remove the redundant night-specific theme file, as the `DayNight` parent in the main `themes.xml` will handle the switch automatically when combined with `values-night/colors.xml`.

### [Layouts and Drawables]

#### [MODIFY] [bg_profile_gradient.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/drawable/bg_profile_gradient.xml)
Update the gradient to use theme-aware colors or provide a night version.
- Current hardcoded values: `#332B00`, `#110E00`, `#000000`.
- I will create a `values/colors.xml` entry for these gradient colors to make them switchable.

#### [MODIFY] [fragment_profile.xml](file:///C:/Users/WinUser/Downloads/CHEF-AI/resilio/app/src/main/res/layout/fragment_profile.xml)
Update hardcoded `@color/white` and `@color/background_dark` usages to use semantic colors like `@color/text_primary` or theme attributes like `?attr/colorSurface`.

## Verification Plan

### Manual Verification
- Deploy the app to an emulator or device.
- Switch the system theme between Light and Dark mode.
- Verify that all screens (especially the Profile fragment) update their colors accordingly.
- Ensure text remains legible in both modes.
