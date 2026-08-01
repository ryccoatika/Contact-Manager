# ============================================================================
#  R8 / ProGuard keep rules — ContactManager
#
#  R8 full mode is ON (default since AGP 8.0; pinned in gradle.properties).
#  proguard-android-optimize.txt is applied from build.gradle.kts, so its
#  rules are NOT duplicated here.
#
#  This module has no reflection-based serialization (no Gson / Moshi /
#  Retrofit / kotlinx-serialization), no JNI, and no custom Parcelable. Hilt,
#  Jetpack Compose, Coil, Navigation-Compose and DataStore each ship their own
#  consumer keep rules through AAR metadata, so they need nothing here. The
#  ContactManagerApp / MainActivity entry points are kept from the manifest.
#
#  What remains is the small, deliberate set below. Resist adding blanket
#  -keep rules "just in case": under full mode they defeat the optimizer.
# ============================================================================

# --- Readable crash reports -------------------------------------------------
# Keep source file + line numbers so a retraced stack trace is useful, but mask
# the original file name. Retrace with build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Notes ------------------------------------------------------------------
# getSystemService(TelephonyManager::class.java) and friends reference framework
# class tokens only — no app class is reflected, so nothing to keep for SIM code.
