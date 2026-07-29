# Premium Redesign ("Porcelain & Pine") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle all five screens into a pine-teal-on-porcelain premium visual system without changing any behavior.

**Architecture:** A token layer (color, type, shape, extended brass + avatar-gradient tokens) sits under Material3 and is consumed by shared `ui/common` composables. Screens are recomposed to use those tokens/components; ViewModels, domain, data, and navigation are untouched. Foundation tasks (1–3) land first; the five screen tasks (4–8) depend only on the foundation and are independent of each other.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Coil, Compose Downloadable Google Fonts.

## Global Constraints

- Presentation layer only. Do **not** edit any `*ViewModel.kt`, `domain/`, `data/`, `di/`, or `AppNav.kt`.
- All existing unit tests must stay green unchanged after every task: `./gradlew testDebugUnitTest`.
- Every task ends with a clean Kotlin compile: `./gradlew compileDebugKotlin`.
- Preserve all `contentDescription`s and 48.dp minimum touch targets already present.
- Both light and dark themes are first-class; every new composable gets a `@Preview` in both.
- Package root: `com.ryccoatika.contactmanager`. Namespace/appId identical.
- Design source of truth: `docs/superpowers/specs/2026-07-30-premium-redesign-design.md` and the approved mockup.

---

### Task 1: Typography — Manrope via Downloadable Google Fonts + full type scale

**Files:**
- Modify: `app/build.gradle.kts` (add dependency)
- Create: `app/src/main/res/values/font_certs.xml`
- Modify: `app/src/main/res/values/strings.xml` (provider strings, optional)
- Rewrite: `app/src/main/java/com/ryccoatika/contactmanager/ui/theme/Type.kt`

**Interfaces:**
- Produces: `val Typography: Typography` (full M3 scale), `val TabularNums: TextStyle` (for phone numbers/counts), `val AppFontFamily: FontFamily` (Manrope with system fallback).

- [ ] **Step 1: Add the downloadable-fonts dependency**

In `app/build.gradle.kts` dependencies block, add:
```kotlin
implementation("androidx.compose.ui:ui-text-google-fonts:1.7.5")
```
(Match the Compose version already used in the file; if the BOM governs Compose, use `implementation("androidx.compose.ui:ui-text-google-fonts")`.)

- [ ] **Step 2: Add the Google Fonts provider certificate array**

The provider requires the public Google-signed certificate list. Fetch the canonical file (do not hand-author the base64):
```bash
curl -L -o app/src/main/res/values/font_certs.xml \
  https://raw.githubusercontent.com/android/compose-samples/main/Jetchat/app/src/main/res/values/font_certs.xml
```
Verify it defines `<array name="com_google_android_gms_fonts_certs">`. If the fetch fails (no network), SKIP the Manrope wiring and use the fallback noted in Step 3 (system default with the tuned scale) — the redesign still ships; note this in the commit.

- [ ] **Step 3: Rewrite `Type.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ryccoatika.contactmanager.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val manrope = GoogleFont("Manrope")

// Manrope with a graceful fall back to the platform default (Roboto) when the
// Google Fonts provider is unavailable (no Play services / offline first run).
val AppFontFamily = FontFamily(
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = manrope, fontProvider = provider, weight = FontWeight.ExtraBold),
)

/** Line up digits in phone numbers, counts, and codes. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

private val base = Typography()
val Typography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = base.titleMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = AppFontFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = AppFontFamily),
    bodySmall = base.bodySmall.copy(fontFamily = AppFontFamily),
    labelLarge = base.labelLarge.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = AppFontFamily, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)
```
Fallback variant (only if Step 2 failed): drop the `googlefonts` imports and set every `fontFamily = FontFamily.Default`; keep the weights/spacing.

- [ ] **Step 4: Compile**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (`Typography` is not yet referenced by the theme — that happens in Task 2 — so this only checks the file compiles.)

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/res/values/font_certs.xml app/src/main/java/com/ryccoatika/contactmanager/ui/theme/Type.kt
git commit -m "feat(ui): Manrope type scale via downloadable Google Fonts"
```

---

### Task 2: Color, Shape, extended tokens, Theme

**Files:**
- Rewrite: `app/src/main/java/com/ryccoatika/contactmanager/ui/theme/Color.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/theme/ExtendedColors.kt`
- Rewrite: `app/src/main/java/com/ryccoatika/contactmanager/ui/theme/Theme.kt`

**Interfaces:**
- Produces:
  - Color vals per the spec table (light/dark) in `Color.kt`.
  - `val AppShapes: Shapes` in `Shape.kt`.
  - `data class ExtendedColors(val brass, onBrass, brassContainer, onBrassContainer: Color)`, `val LocalExtendedColors`, and `val MaterialTheme.extendedColors: ExtendedColors @Composable @ReadOnlyComposable get()` in `ExtendedColors.kt`.
  - `object AvatarPalette { fun brushFor(key: String): Brush; fun colorsFor(key: String): List<Color> }` in `ExtendedColors.kt`.
  - `ContactManagerTheme(darkTheme, dynamicColor = false, content)` in `Theme.kt` — dynamic color now defaults OFF.

- [ ] **Step 1: Rewrite `Color.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.ui.graphics.Color

// "Porcelain & Pine" — pine teal signature on a green-biased porcelain neutral.
// Light
val PinePrimaryLight = Color(0xFF0E5A51)
val OnPinePrimaryLight = Color(0xFFFFFFFF)
val PineContainerLight = Color(0xFFD7EDE7)
val OnPineContainerLight = Color(0xFF063B34)
val SecondaryLight = Color(0xFF4C625B)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFDDE9E3)
val OnSecondaryContainerLight = Color(0xFF14201B)
val GroundLight = Color(0xFFF4F6F3)
val OnGroundLight = Color(0xFF14201B)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF14201B)
val SurfaceVariantLight = Color(0xFFECEFEA)
val OnSurfaceVariantLight = Color(0xFF5D6B64)
val OutlineLight = Color(0xFFC2CBC5)

// Dark
val PinePrimaryDark = Color(0xFF74D6C6)
val OnPinePrimaryDark = Color(0xFF06352E)
val PineContainerDark = Color(0xFF213330)
val OnPineContainerDark = Color(0xFFBFEDE4)
val SecondaryDark = Color(0xFFB4C6BE)
val OnSecondaryDark = Color(0xFF20342E)
val SecondaryContainerDark = Color(0xFF34443E)
val OnSecondaryContainerDark = Color(0xFFDDE9E3)
val GroundDark = Color(0xFF0F1512)
val OnGroundDark = Color(0xFFE8EEEA)
val SurfaceDark = Color(0xFF171E1A)
val OnSurfaceDark = Color(0xFFE8EEEA)
val SurfaceVariantDark = Color(0xFF212A25)
val OnSurfaceVariantDark = Color(0xFF94A39C)
val OutlineDark = Color(0xFF3B443F)

// Extended — brass accent, used sparingly.
val BrassLight = Color(0xFF8F6423)
val OnBrassLight = Color(0xFFFFFFFF)
val BrassContainerLight = Color(0xFFF1E7D4)
val OnBrassContainerLight = Color(0xFF3D2C0E)
val BrassDark = Color(0xFFD9AE6E)
val OnBrassDark = Color(0xFF3D2C0E)
val BrassContainerDark = Color(0xFF2C2417)
val OnBrassContainerDark = Color(0xFFF1E7D4)
```

- [ ] **Step 2: Create `Shape.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```

- [ ] **Step 3: Create `ExtendedColors.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

data class ExtendedColors(
    val brass: Color,
    val onBrass: Color,
    val brassContainer: Color,
    val onBrassContainer: Color,
)

val LightExtendedColors = ExtendedColors(BrassLight, OnBrassLight, BrassContainerLight, OnBrassContainerLight)
val DarkExtendedColors = ExtendedColors(BrassDark, OnBrassDark, BrassContainerDark, OnBrassContainerDark)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

/** Desaturated jewel gradients for identity avatars, chosen deterministically by name. */
object AvatarPalette {
    private val gradients = listOf(
        listOf(Color(0xFF2C7A70), Color(0xFF54B9AA)), // teal
        listOf(Color(0xFF48579A), Color(0xFF8090D4)), // indigo
        listOf(Color(0xFF7C4A72), Color(0xFFB681AC)), // plum
        listOf(Color(0xFF977132), Color(0xFFD4AC64)), // brass
        listOf(Color(0xFFA2545C), Color(0xFFD68D93)), // rose
        listOf(Color(0xFF3E5A63), Color(0xFF7196A2)), // slate
    )

    fun colorsFor(key: String): List<Color> =
        gradients[(key.hashCode().absoluteValue) % gradients.size]

    fun brushFor(key: String): Brush = Brush.linearGradient(colorsFor(key))
}
```

- [ ] **Step 4: Rewrite `Theme.kt`**

```kotlin
package com.ryccoatika.contactmanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PinePrimaryDark, onPrimary = OnPinePrimaryDark,
    primaryContainer = PineContainerDark, onPrimaryContainer = OnPineContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    background = GroundDark, onBackground = OnGroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PinePrimaryLight, onPrimary = OnPinePrimaryLight,
    primaryContainer = PineContainerLight, onPrimaryContainer = OnPineContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    background = GroundLight, onBackground = OnGroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

/**
 * "Porcelain & Pine" is the default identity. Dynamic color (Monet) is opt-in
 * on Android 12+; dark mode follows the system setting.
 */
@Composable
fun ContactManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 5: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/theme/
git commit -m "feat(ui): Porcelain & Pine tokens, shapes, extended brass + avatar palette"
```

---

### Task 3: Shared components + provenance palette retune

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/common/AccountVisuals.kt` (colors only)
- Create: `app/src/main/java/com/ryccoatika/contactmanager/ui/common/ContactUi.kt`

**Interfaces:**
- Produces (all in `ui.common`):
  - `@Composable ContactAvatar(name: String, photoUri: Any?, modifier, size: Dp = 44.dp)`
  - `@Composable SelectedAvatar(size: Dp = 44.dp)`
  - `@Composable AccountDot(accountType: String?, accountName: String?, size: Dp = 8.dp)`
  - `@Composable CapabilityTag(capability: AccountCapability, writable: Boolean = true)`
  - `@Composable SectionCard(modifier, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable QuickActionPill(label: String, icon: ImageVector, onClick: () -> Unit, modifier)`
  - `@Composable SearchField(query: String, placeholder: String, onQueryChange: (String) -> Unit, modifier)`
- Consumes: `AvatarPalette`, `MaterialTheme.extendedColors` (Task 2); `AccountCapability` (`domain.model`).

- [ ] **Step 1: Retune `AccountVisuals` palette (colors only, labels untouched)**

Replace the `palette` list in `AccountVisuals.kt` with muted premium tones:
```kotlin
private val palette = listOf(
    Color(0xFF3B7DDD), Color(0xFF1E9E6A), Color(0xFFA9782F), Color(0xFF5D7680),
    Color(0xFF7C4A72), Color(0xFF2C7A70), Color(0xFFB4622F), Color(0xFF4C5A78),
)
```
Leave `color(...)` and `label(...)` signatures and logic exactly as they are.

- [ ] **Step 2: Create `ContactUi.kt` with the shared composables**

```kotlin
package com.ryccoatika.contactmanager.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ryccoatika.contactmanager.domain.model.AccountCapability
import com.ryccoatika.contactmanager.ui.theme.AvatarPalette
import com.ryccoatika.contactmanager.ui.theme.extendedColors

@Composable
fun ContactAvatar(
    name: String,
    photoUri: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(AvatarPalette.brushFor(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun SelectedAvatar(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun AccountDot(accountType: String?, accountName: String?, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(AccountVisuals.color(accountType, accountName)))
}

@Composable
fun CapabilityTag(capability: AccountCapability, writable: Boolean = true) {
    val brass = MaterialTheme.extendedColors
    val (label, container, onContainer) = when (capability) {
        AccountCapability.FULL_CRUD ->
            Triple("Full access", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        AccountCapability.READ_ONLY ->
            Triple("Read-only", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        AccountCapability.SIM ->
            Triple(if (writable) "SIM" else "SIM · limited", brass.brassContainer, brass.brass)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = onContainer,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun QuickActionPill(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailing,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}
```

- [ ] **Step 3: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `PhoneEmailTypeLabelTest`, `AccountClassifierTest`, etc. still pass (palette change is color-only).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/common/
git commit -m "feat(ui): shared premium components + muted provenance palette"
```

---

### Task 4: Home screen restyle

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `ContactAvatar`, `SelectedAvatar`, `AccountDot`, `SearchField`, `TabularNums` (Tasks 1–3).

- [ ] **Step 1: Swap the search field**

Replace the `OutlinedTextField` (lines ~223–237) with `SearchField`:
```kotlin
SearchField(
    query = state.query,
    placeholder = "Search ${state.contacts.size} contacts",
    onQueryChange = viewModel::setQuery,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    trailing = if (state.query.isNotEmpty()) {
        { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
    } else null,
)
```

- [ ] **Step 2: Restyle the account `FilterChip`s**

Keep the `LazyRow`/`FilterChip` structure; replace the `leadingIcon` dot Box with `AccountDot(account.type, account.name, size = 10.dp)` and render the count with `Modifier` unchanged. No logic change.

- [ ] **Step 3: Replace `ContactAvatar`/`SelectedAvatar` private composables with the shared ones**

Delete the private `ContactAvatar` and `SelectedAvatar` in this file (lines ~626–662 and ~626). In `ContactRow`, call:
```kotlin
leadingContent = { if (selected) SelectedAvatar() else ContactAvatar(contact.displayName, contact.photoThumbnailUri) },
```
Add a subtitle in `headlineContent`/`supportingContent`: keep `headlineContent = { Text(contact.displayName, style = MaterialTheme.typography.titleMedium) }` and add `supportingContent = { contactSubtitle(contact)?.let { Text(it, style = TabularNums.merge(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant) } }`. Add this helper at file scope:
```kotlin
private fun contactSubtitle(c: Contact): String? =
    c.rawContacts.firstNotNullOfOrNull { it.organization }
        ?: c.rawContacts.flatMap { it.phones }.firstOrNull()?.value
        ?: c.rawContacts.flatMap { it.emails }.firstOrNull()?.value
```
Keep the trailing provenance dots but route them through `AccountDot(raw.accountType, raw.accountName)`.

- [ ] **Step 4: Squircle FAB**

In the `FloatingActionButton`, add `shape = MaterialTheme.shapes.medium`.

- [ ] **Step 5: Restyle section headers (already pine via token — verify)**

`SectionHeader` uses `colorScheme.primary` — keep. Change its `Surface` color to `MaterialTheme.colorScheme.background` so headers sit on the porcelain ground.

- [ ] **Step 6: Add previews**

```kotlin
@Preview(name = "Home light") @Preview(name = "Home dark", uiMode = UI_MODE_NIGHT_YES)
@Composable private fun ContactRowPreview() {
    ContactManagerTheme {
        Surface { ContactRow(previewContact(), selected = false, onClick = {}, onLongClick = {}) }
    }
}
```
Add a `private fun previewContact(): Contact = ...` building a minimal `Contact` from `domain.model` (one raw contact, a phone, an org). Import `UI_MODE_NIGHT_YES` from `android.content.res.Configuration`, `@Preview` from `androidx.compose.ui.tooling.preview`, and `ContactManagerTheme`.

- [ ] **Step 7: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `HomeViewModelTest` still passes.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/home/HomeScreen.kt
git commit -m "feat(ui): premium Home — search pill, gradient avatars, subtitles, squircle FAB"
```

---

### Task 5: Detail screen restyle

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/detail/DetailScreen.kt`

**Interfaces:**
- Consumes: `ContactAvatar`, `SectionCard`, `QuickActionPill`, `AccountDot`, `CapabilityTag`, `TabularNums`, `MaterialTheme.extendedColors`.

- [ ] **Step 1: Replace `DetailHeader` with a tonal hero + quick actions**

```kotlin
@Composable
private fun DetailHero(contact: Contact, onEdit: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ContactAvatar(contact.displayName, contact.photoThumbnailUri, size = 88.dp)
            Spacer(Modifier.height(14.dp))
            Text(contact.displayName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            contact.rawContacts.firstNotNullOfOrNull { it.organization }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))
            val phone = contact.rawContacts.flatMap { it.phones }.firstOrNull()?.value
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (phone != null) {
                    QuickActionPill("Call", Icons.Default.Call, { /* keep existing dialer intent if present, else no-op */ }, Modifier.weight(1f))
                    QuickActionPill("Message", Icons.AutoMirrored.Filled.Message, { }, Modifier.weight(1f))
                }
                QuickActionPill("Edit", Icons.Default.Edit, onEdit, Modifier.weight(1f))
            }
        }
    }
}
```
Add a soft pine tint behind the avatar using `Brush.radialGradient` inside the `Surface` if desired (optional): wrap the avatar in a `Box` with `background(Brush.radialGradient(listOf(primaryContainer.copy(alpha=.6f), Color.Transparent)))`. Wire `onEdit` to the first editable raw contact: `onEdit = { current.rawContacts.firstOrNull { AccountClassifier.classify(it.accountType) != AccountCapability.READ_ONLY }?.let { onEditRawContact(it.rawContactId) } }`.

Note: Call/Message pills are visual; if no dialer/SMS intent exists in the current screen, leave their `onClick` as `{}` — do NOT invent new navigation (out of scope). Edit must work.

- [ ] **Step 2: Convert `RawContactCard` from `OutlinedCard` to `SectionCard`**

Replace `OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { ... } }` with `SectionCard(Modifier.fillMaxWidth()) { ... }` (drop the inner padding Column since `SectionCard` pads). Replace the inline account dot Box with `AccountDot(raw.accountType, raw.accountName, size = 10.dp)`. Replace the hand-rolled SIM chip with `CapabilityTag(AccountCapability.SIM, writable = ...)` when `capability == SIM`. Give `FieldRow`'s value `style = TabularNums.merge(MaterialTheme.typography.bodyLarge)`.

- [ ] **Step 3: Add previews** (light + dark) for `DetailHero` and `RawContactCard` using a `previewContact()` helper, same pattern as Task 4 Step 6.

- [ ] **Step 4: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `DetailViewModelTest` passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/detail/DetailScreen.kt
git commit -m "feat(ui): premium Detail — tonal hero, quick-action pills, account cards"
```

---

### Task 6: Editor screen restyle

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/editor/EditorScreen.kt`

**Interfaces:**
- Consumes: `SectionCard`, `AccountDot`.

- [ ] **Step 1: Group the form into `SectionCard`s**

Wrap the name/organization/note fields and each `DynamicValueList` in `SectionCard`s with a small `labelSmall` group label above (e.g. "Phone", "Email"). Keep every `OutlinedTextField`, `viewModel::` callback, and the SIM branch exactly as-is — only wrap in cards and adjust spacing.

- [ ] **Step 2: Restyle the account picker as a destination row**

Keep `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly)` logic, but prepend an `AccountDot(state.selectedAccount?.type, state.selectedAccount?.name)` as the `leadingIcon` of the anchor field so the destination reads at a glance.

- [ ] **Step 3: Add a preview** (light + dark) of a `SectionCard` wrapping a sample `OutlinedTextField`.

- [ ] **Step 4: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `EditorViewModelTest` passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/editor/EditorScreen.kt
git commit -m "feat(ui): premium Editor — grouped field cards, account destination row"
```

---

### Task 7: Accounts screen restyle

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/accounts/AccountsScreen.kt`

**Interfaces:**
- Consumes: `SectionCard`, `AccountDot`, `CapabilityTag`.

- [ ] **Step 1: Convert `AccountRow` from `ListItem` to a card row**

Replace the `ListItem` with a `SectionCard` containing a `Row`: a colored rounded icon tile (a `Box` `size(42.dp)` `clip(shapes.small)` `background(AccountVisuals.color(...))` with the provider initial or a generic icon in white), then a `Column` (label + `account.name ?: "On this device"`), then a `Column` (bold `account.contactCount` with `TabularNums`, and `CapabilityTag(account.capability, account.writable)`), then the existing overflow `IconButton`+`DropdownMenu` unchanged. Keep `onClick`, `onMoveAll`, and the read-only gating exactly.

- [ ] **Step 2: Add list spacing** — set the `LazyColumn` `contentPadding = PaddingValues(16.dp)` and `verticalArrangement = Arrangement.spacedBy(10.dp)`.

- [ ] **Step 3: Add a preview** (light + dark) of `AccountRow` with a sample `ContactAccount`.

- [ ] **Step 4: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `AccountsViewModelTest` passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/accounts/AccountsScreen.kt
git commit -m "feat(ui): premium Accounts — card rows with icon tiles + capability tags"
```

---

### Task 8: Duplicates screen restyle

**Files:**
- Modify: `app/src/main/java/com/ryccoatika/contactmanager/ui/duplicates/DuplicatesScreen.kt`

**Interfaces:**
- Consumes: `ContactAvatar`, `SectionCard`, `AccountDot`, `MaterialTheme.extendedColors`.

- [ ] **Step 1: Convert `DuplicateGroupCard` from `OutlinedCard` to `SectionCard`**; keep the confidence chip and action buttons. Render `group.matchReason` in `MaterialTheme.extendedColors.brass` with `labelMedium`.

- [ ] **Step 2: Restyle `MemberRow`** — replace the `primaryContainer` initial Box with `ContactAvatar(contact.displayName, /* no photo */ null, size = 40.dp)`; keep the phone/email preview `supportingContent`; route trailing dots through `AccountDot`.

- [ ] **Step 3: Make Merge the primary action** — in `DuplicateGroupCard`, change the action row so `Merge…` is a `FilledTonalButton`/`Button` (pine) and `Link` + `Not duplicate` are `TextButton`s.

- [ ] **Step 4: Restyle the empty state** — keep the `CheckCircle` + text; tint icon `colorScheme.primary` (already), wrap text in `titleMedium`.

- [ ] **Step 5: Add a preview** (light + dark) of `DuplicateGroupCard` with a sample `DuplicateGroup`.

- [ ] **Step 6: Compile + tests**

Run: `./gradlew compileDebugKotlin && ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `DuplicatesViewModelTest` passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ryccoatika/contactmanager/ui/duplicates/DuplicatesScreen.kt
git commit -m "feat(ui): premium Duplicates — reason in brass, avatar rows, primary merge"
```

---

### Task 9: Full-app verification

**Files:** none (verification only)

- [ ] **Step 1: Assemble the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: all pass — proves logic untouched.

- [ ] **Step 3: Visual pass** — if an emulator/device is available, install and eyeball all five screens in light and dark against the mockup; otherwise render the `@Preview`s. Confirm: pine primary, porcelain ground, gradient avatars, brass only on SIM/duplicate-reason, dark legible.

- [ ] **Step 4: Final commit / summary** — no code; report results.

---

## Self-Review

**Spec coverage:** Color ✓ (T2), Type/Manrope ✓ (T1), Shape ✓ (T2), extended brass + avatar gradients ✓ (T2), provenance retune ✓ (T3), shared components ✓ (T3), Home ✓ (T4), Detail ✓ (T5), Editor ✓ (T6), Accounts ✓ (T7), Duplicates ✓ (T8), dynamic-color opt-in default flip ✓ (T2 Step 4), verification/previews ✓ (T9 + per-screen). No spec section left without a task.

**Placeholder scan:** Call/Message `onClick = {}` in T5 is intentional and called out (no invented navigation), not a placeholder. No TBD/TODO left.

**Type consistency:** `AvatarPalette.brushFor`/`colorsFor`, `MaterialTheme.extendedColors`, `ExtendedColors` fields, `ContactAvatar(name, photoUri, modifier, size)`, `AccountDot`, `CapabilityTag(capability, writable)`, `SectionCard`, `QuickActionPill`, `SearchField` — names used in T4–T8 match their T1–T3 definitions.
