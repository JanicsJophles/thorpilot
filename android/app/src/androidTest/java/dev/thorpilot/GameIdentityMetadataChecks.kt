package dev.thorpilot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Real Android JSON types and local-review trust boundary checks. */
object GameIdentityMetadataChecks {
    fun run(context: Context) {
        val hash = "a".repeat(64)
        val cover = "https://images.igdb.com/igdb/image/upload/t_cover_big/co123.jpg"
        fun fixture() = JSONObject().put("version", 1).put("platform", "n3ds").put("sha256", hash)
            .put("canonical_title", "Example Adventure").put("match_status", "needs_review")
            .put("provider_ids", JSONObject().put("igdb", 123)).put("cover_url", cover)
            .put("region", "USA").put("revision", "1.0")
            .put("candidates", JSONArray().put(JSONObject().put("canonical_title", "Another Adventure")
                .put("provider_ids", JSONObject().put("igdb", 456)).put("cover_url", cover)))
        fun parse(j: JSONObject?) = GameIdentityMetadata.parse(j, "n3ds", hash)
        val metadata = requireNotNull(parse(fixture()))
        check(parse(metadata.json()) == metadata)
        check(metadata.providerIds["igdb"] == 123L)
        check(GameIdentityMetadata.parse(fixture(), "nds", hash) == null)
        check(GameIdentityMetadata.parse(fixture(), "n3ds", "b".repeat(64)) == null)
        check(parse(null) == null)
        listOf(
            fixture().put("version", 2), fixture().put("version", "1"),
            fixture().put("canonical_title", "a".repeat(257)), fixture().put("canonical_title", "bad\nname"),
            fixture().put("canonical_title", 42), fixture().put("canonical_title", "  "),
            fixture().put("match_status", "confirmed"), fixture().put("sha256", "a".repeat(63)),
            fixture().put("cover_url", "https://evil.example/cover.jpg"),
            fixture().put("provider_ids", JSONObject().put("igdb", "123")),
            fixture().put("provider_ids", JSONObject().put("igdb", 1.5)),
            fixture().put("provider_ids", JSONObject().put("igdb", -1)),
            fixture().put("provider_ids", JSONObject().put("igdb", Long.MAX_VALUE)),
            fixture().put("provider_ids", JSONObject().put("unknown", 1)),
            fixture().put("candidates", JSONArray().also { a -> repeat(9) { a.put(metadata.candidates[0].json()) } }),
            fixture().put("candidates", JSONArray().put("bad candidate")),
        ).forEach { check(parse(it) == null) { "Accepted invalid metadata: $it" } }
        val selected = metadata.selectCandidate(0)
        check(selected.deviceReviewed && selected.canonicalTitle == "Another Adventure" && selected.providerIds["igdb"] == 456L)
        check(!requireNotNull(parse(selected.json())).deviceReviewed)
        check(GameIdentityMetadata.parse(selected.json(), "n3ds", hash, trustDeviceReview = true) == selected)
        val manual = metadata.reviewTitle("  My Corrected Title  ")
        check(manual.canonicalTitle == "My Corrected Title" && manual.providerIds.isEmpty() && manual.coverUrl == null && manual.deviceReviewed)
        check(metadata.reviewTitle(metadata.canonicalTitle).providerIds == metadata.providerIds)
        check(runCatching { metadata.reviewTitle("\n") }.isFailure)
        check(runCatching { metadata.selectCandidate(8) }.isFailure)
        check(parse(JSONObject(fixture().toString())) == metadata)
        check(parse(fixture().put("canonical_title", "a".repeat(256)))?.canonicalTitle?.length == 256)
        check(metadata.reviewTitle("a".repeat(256)).canonicalTitle.length == 256)
        check(runCatching { metadata.reviewTitle("a".repeat(257)) }.isFailure)

        val entry = ThorDownloadEntry("identity-fixture", "Source Title", "n3ds", "fixture.cia", 123, hash,
            metadata = selected)
        val serialized = JSONObject(entry.json().toString())
        check(ThorDownloadEntry.parse(serialized) == entry)
        check(ThorDownloadEntry.parse(serialized, trustDeviceReview = false).metadata == selected.copy(deviceReviewed = false))
        check(ThorDownloadEntry.parse(serialized.put("metadata", JSONObject().put("version", 999))).metadata == null)
        check(ThorDownloadEntry.parse(entry.json().put("metadata", metadata.json().put("sha256", "b".repeat(64)))).metadata == null)

        listOf("bad/path.cia", "../fixture.cia", "bad\\path.cia", "bad\nname.cia").forEach { provenance ->
            val parsed = ThorDownloadEntry.parse(entry.json().put("original_file_name", provenance))
            check(parsed.originalFileName == null)
            check(parsed.withIdentity(selected).destination() == "n3ds/fixture.cia")
        }

        val preferenceName = "game-identity-reviews-test"
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val reviews = GameIdentityReviews(context, preferenceName)
            check(reviews.load("server-a-token-a", entry) == null)
            reviews.save("server-a-token-a", entry, selected)
            val restored = GameIdentityReviews(context, preferenceName)
            check(restored.load("server-a-token-a", entry) == selected)
            check(restored.load("server-a-token-b", entry) == null)
            check(restored.load("server-b-token-a", entry) == null)
            check(restored.load("", entry) == null)
            check(restored.load("server-a-token-a", entry.copy(sha256 = "b".repeat(64))) == null)
            check(restored.load("server-a-token-a", entry.copy(platform = "nds")) == null)
            // Metadata follows file identity, not a rename or catalog ID refresh.
            check(restored.load("server-a-token-a", entry.copy(id = "new-id", fileName = "renamed.cia")) == selected)
            check(restored.load("server-a-token-a", entry.copy(sha256 = hash.uppercase())) == selected)
            check(runCatching { restored.save("server-a-token-a", entry, metadata) }.isFailure)
            check(runCatching { restored.save("", entry, selected) }.isFailure)
            check(runCatching { restored.save("server-a-token-a", entry.copy(platform = "nds"), selected) }.isFailure)
            check(runCatching { restored.save("server-a-token-a", entry.copy(sha256 = "b".repeat(64)), selected) }.isFailure)
            restored.save("server-b-token-a", entry, manual)
            restored.remove("server-a-token-a", entry)
            check(GameIdentityReviews(context, preferenceName).load("server-a-token-a", entry) == null)
            check(restored.load("server-b-token-a", entry) == manual)
            restored.remove("server-a-token-a", entry) // Reset remains harmless when already empty.
            check(restored.load("server-b-token-a", entry) == manual)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
