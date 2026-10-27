package com.mustangos.core

data class LaunchableApp(val packageName: String, val activity: String, val label: String)

object IntegrationPolicy {
    // A candidate is evidence for a picker, never proof of a vendor's identity or API.
    fun isZlinkCandidate(app: LaunchableApp): Boolean =
        Regex("z[ -]?link", RegexOption.IGNORE_CASE).containsMatchIn(app.label) ||
            app.packageName.split('.').any { it.equals("zlink", true) || it.equals("zlink5", true) }

    fun selectMedia(packages: List<String>, preferred: String?, zlink: String?): String? = when {
        preferred != null -> preferred.takeIf { it in packages }
        zlink != null && zlink in packages -> zlink
        packages.distinct().size == 1 -> packages.first()
        else -> null
    }
}

enum class MediaCommand(val action: Long) { PREVIOUS(16), PLAY(4), PAUSE(2), NEXT(32) }
data class MediaFacts(
    val source: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val updatedAtMs: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 0f,
    val actions: Long = 0
) {
    fun supports(command: MediaCommand): Boolean = source != null &&
        (actions == 0L || actions and command.action != 0L ||
            (command == MediaCommand.PLAY || command == MediaCommand.PAUSE) && actions and 512L != 0L)
    fun progress(nowMs: Long): Long {
        if (durationMs <= 0) return 0
        val elapsed = if (playing && updatedAtMs > 0) (nowMs - updatedAtMs).coerceAtLeast(0) else 0
        return (positionMs + (elapsed * speed).toLong()).coerceIn(0, durationMs)
    }
}
