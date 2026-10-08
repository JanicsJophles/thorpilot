package dev.thorpilot

import org.json.JSONArray
import org.json.JSONObject

/** Optional catalog identity, bound to the file checksum and platform rather than its display name. */
data class GameIdentityMetadata(
    val platform: String,
    val sha256: String,
    val canonicalTitle: String,
    val matchStatus: String,
    val providerIds: Map<String, Long> = emptyMap(),
    val region: String? = null,
    val revision: String? = null,
    val coverUrl: String? = null,
    val candidates: List<Candidate> = emptyList(),
    val deviceReviewed: Boolean = false,
) {
    data class Candidate(val canonicalTitle: String, val providerIds: Map<String, Long>, val coverUrl: String? = null) {
        fun json(): JSONObject = JSONObject().put("canonical_title", canonicalTitle)
            .put("provider_ids", JSONObject(providerIds)).put("cover_url", coverUrl)
    }

    fun json(): JSONObject = JSONObject().put("version", 1).put("platform", platform).put("sha256", sha256)
        .put("canonical_title", canonicalTitle).put("match_status", matchStatus)
        .put("provider_ids", JSONObject(providerIds)).put("region", region).put("revision", revision)
        .put("cover_url", coverUrl).put("candidates", JSONArray().also { array -> candidates.forEach { array.put(it.json()) } })
        .put("device_reviewed", deviceReviewed)

    /** A selection is a local decision; it does not claim a provider verified the file. */
    fun selectCandidate(index: Int): GameIdentityMetadata {
        val candidate = candidates[index]
        return copy(canonicalTitle = candidate.canonicalTitle, providerIds = candidate.providerIds,
            coverUrl = candidate.coverUrl, deviceReviewed = true)
    }

    /** A manually changed title cannot retain artwork or provider IDs belonging to a different title. */
    fun reviewTitle(title: String): GameIdentityMetadata {
        val clean = checkedText(title, 256)
        return if (clean == canonicalTitle) copy(deviceReviewed = true)
        else copy(canonicalTitle = clean, providerIds = emptyMap(), coverUrl = null, deviceReviewed = true)
    }

    companion object {
        private val statuses = setOf("matched", "needs_review", "unmatched")
        private val providers = setOf("igdb", "screenscraper", "launchbox")
        private val checksum = Regex("[a-fA-F0-9]{64}")
        private val platformName = Regex("[a-z0-9_-]{1,32}")

        /** Invalid optional metadata never prevents an otherwise valid legacy transfer. */
        fun parse(value: JSONObject?, expectedPlatform: String, expectedSha256: String,
                  trustDeviceReview: Boolean = false): GameIdentityMetadata? = runCatching {
            val j = requireNotNull(value)
            require(j.opt("version") is Number && j.get("version").toString() == "1")
            val platform = requiredText(j, "platform", 32)
            require(platformName.matches(platform) && platform == expectedPlatform)
            val sha = requiredText(j, "sha256", 64)
            require(checksum.matches(sha) && checksum.matches(expectedSha256) && sha.equals(expectedSha256, true))
            val status = requiredText(j, "match_status", 20)
            require(status in statuses)
            val candidateArray = if (!j.has("candidates") || j.isNull("candidates")) JSONArray() else j.getJSONArray("candidates")
            require(candidateArray.length() <= 8)
            val candidates = (0 until candidateArray.length()).map { index ->
                val candidate = candidateArray.getJSONObject(index)
                Candidate(requiredText(candidate, "canonical_title", 256), ids(candidate), cover(candidate))
            }
            GameIdentityMetadata(platform, sha.lowercase(), requiredText(j, "canonical_title", 256), status,
                ids(j), optionalText(j, "region", 64), optionalText(j, "revision", 64), cover(j), candidates,
                trustDeviceReview && j.opt("device_reviewed") == true)
        }.getOrNull()

        private fun ids(j: JSONObject): Map<String, Long> {
            if (!j.has("provider_ids") || j.isNull("provider_ids")) return emptyMap()
            val objectIds = j.getJSONObject("provider_ids")
            require(objectIds.length() <= providers.size)
            return objectIds.keys().asSequence().associateWith { key ->
                require(key in providers)
                val raw = objectIds.get(key)
                require(raw is Int || raw is Long)
                (raw as Number).toLong().also { require(it in 1..Int.MAX_VALUE.toLong()) }
            }
        }
        private fun cover(j: JSONObject): String? = optionalText(j, "cover_url", 2048)?.also { require(CoverImages.supported(it)) }
        private fun optionalText(j: JSONObject, key: String, max: Int): String? =
            if (!j.has(key) || j.isNull(key)) null else requiredText(j, key, max)
        private fun requiredText(j: JSONObject, key: String, max: Int): String {
            val value = j.get(key)
            require(value is String)
            return checkedText(value, max)
        }
        private fun checkedText(value: String, max: Int): String {
            require(value.length <= max && value.none { it.isISOControl() })
            return value.trim().also { require(it.isNotEmpty()) }
        }
    }
}
