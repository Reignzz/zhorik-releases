package com.beamox.launcher.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Mailbox(
    val name: String,
    val address: String,
    val imapHost: String,
    val imapPort: Int,
    val webUrl: String,
)

data class TeamMember(val name: String, val role: String, val phone: String, val email: String)
data class QuickLink(val title: String, val emoji: String, val url: String)
data class Social(val title: String, val emoji: String, val url: String, val pkg: String)

data class BeamoxConfig(
    val company: String,
    val tagline: String,
    val wakeWords: List<String>,
    val claudeModel: String,
    val claudeSystemExtra: String,
    val statsUrl: String,
    val statsRefreshMinutes: Int,
    val studioUrl: String,
    val mailboxes: List<Mailbox>,
    val team: List<TeamMember>,
    val links: List<QuickLink>,
    val socials: List<Social>,
    val homeApps: List<String>,
) {
    companion object {
        /** Файл-переопределение (присылается install.sh через provision.json → "config"). */
        fun overrideFile(ctx: Context) = File(ctx.filesDir, "config.json")

        fun load(ctx: Context): BeamoxConfig {
            val text = overrideFile(ctx).takeIf { it.exists() }?.readText()
                ?: ctx.assets.open("beamox_config.json").bufferedReader().use { it.readText() }
            return parse(JSONObject(text))
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }

        fun parse(o: JSONObject): BeamoxConfig {
            val claude = o.optJSONObject("claude") ?: JSONObject()
            val stats = o.optJSONObject("stats") ?: JSONObject()
            val studio = o.optJSONObject("studio") ?: JSONObject()
            return BeamoxConfig(
                company = o.optString("company", "Beamox"),
                tagline = o.optString("tagline", ""),
                wakeWords = o.optJSONArray("wakeWords").strings().map { it.lowercase() }
                    .ifEmpty { listOf("привет бимокс", "бимокс", "привет биамокс", "биамокс") },
                claudeModel = claude.optString("model", "claude-opus-5-5"),
                claudeSystemExtra = claude.optString("systemExtra", ""),
                statsUrl = stats.optString("url", ""),
                statsRefreshMinutes = stats.optInt("refreshMinutes", 5).coerceAtLeast(1),
                studioUrl = studio.optString("url", ""),
                mailboxes = o.optJSONArray("mailboxes").objects().map {
                    Mailbox(
                        it.optString("name"), it.optString("address"),
                        it.optString("imapHost", "imap.gmail.com"), it.optInt("imapPort", 993),
                        it.optString("webUrl", ""),
                    )
                },
                team = o.optJSONArray("team").objects().map {
                    TeamMember(it.optString("name"), it.optString("role"), it.optString("phone"), it.optString("email"))
                },
                links = o.optJSONArray("links").objects().map {
                    QuickLink(it.optString("title"), it.optString("emoji", "🔗"), it.optString("url"))
                },
                socials = o.optJSONArray("socials").objects().map {
                    Social(it.optString("title"), it.optString("emoji", "🌐"), it.optString("url"), it.optString("pkg"))
                },
                homeApps = o.optJSONArray("homeApps").strings(),
            )
        }
    }
}
