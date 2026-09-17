package android.net

import android.os.Parcel

/**
 * A minimal, fully-implemented [Uri] usable in plain JVM unit tests (no Robolectric):
 * on the JVM unit-test classpath `android.net.Uri` is the unstubbed SDK class whose
 * static factories (`Uri.parse`, …) throw, and its no-arg constructor is package-private
 * — so this class lives in `android.net` (same package, different compilation unit;
 * ordinary JVM package-private access, no reflection) purely to get a constructible
 * instance. Equality/identity is by [value] only — nothing else is called by the code
 * under test.
 */
class FakeUri(
    private val value: String,
) : Uri() {
    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is FakeUri && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun buildUpon(): Builder = throw UnsupportedOperationException()

    override fun getAuthority(): String? = null

    override fun getEncodedAuthority(): String? = null

    override fun getEncodedFragment(): String? = null

    override fun getEncodedPath(): String? = null

    override fun getEncodedQuery(): String? = null

    override fun getEncodedSchemeSpecificPart(): String? = null

    override fun getEncodedUserInfo(): String? = null

    override fun getFragment(): String? = null

    override fun getHost(): String? = null

    override fun getLastPathSegment(): String? = null

    override fun getPath(): String? = null

    override fun getPathSegments(): List<String> = emptyList()

    override fun getPort(): Int = -1

    override fun getQuery(): String? = null

    override fun getScheme(): String? = null

    override fun getSchemeSpecificPart(): String? = null

    override fun getUserInfo(): String? = null

    override fun isHierarchical(): Boolean = false

    override fun isRelative(): Boolean = false

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
