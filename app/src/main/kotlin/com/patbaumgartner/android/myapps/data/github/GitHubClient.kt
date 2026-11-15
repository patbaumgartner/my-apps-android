package com.patbaumgartner.android.myapps.data.github

import com.patbaumgartner.android.myapps.domain.model.GhAsset
import com.patbaumgartner.android.myapps.domain.model.GhRelease
import com.patbaumgartner.android.myapps.domain.model.GhRepo
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface GitHubResult<out T> {
    data class Ok<T>(val value: T, val etag: String?) : GitHubResult<T>
    data class NotModified<T>(val cachedBody: String) : GitHubResult<T>
    data class HttpError<T>(val code: Int, val rateLimited: Boolean) : GitHubResult<T>
    data class NetworkError<T>(val message: String) : GitHubResult<T>
}

class RateLimitedException : IOException("GitHub API rate limit exceeded")
class NotFoundException(val path: String) : IOException("404 for $path")

/**
 * Unauthenticated GitHub REST API client (60 requests/hour per IP). No tokens are stored
 * or sent. GETs are ETag-cached through [etagStore]; downloads stream to a file.
 */
class GitHubClient(
    private val etagStore: EtagStore,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) {
    interface EtagStore {
        fun etagFor(path: String): String?
        fun save(path: String, etag: String?, body: String)
        fun cachedBody(path: String): String?
    }

    fun listRepos(owner: String): GitHubResult<List<GhRepo>> =
        getJson("/users/$owner/repos?per_page=100", "repos") { body ->
            JSONArray(body).let { array ->
                List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    GhRepo(
                        name = o.getString("name"),
                        description = o.optString("description").ifBlank { null },
                        htmlUrl = o.optString("html_url").ifBlank { null },
                    )
                }
            }
        }

    fun latestRelease(owner: String, repo: String): GitHubResult<GhRelease> =
        getJson("/repos/$owner/$repo/releases/latest", cacheKey(owner, repo, null), ::parseRelease)

    fun releaseByTag(owner: String, repo: String, tag: String): GitHubResult<GhRelease> =
        getJson("/repos/$owner/$repo/releases/tags/$tag", cacheKey(owner, repo, tag), ::parseRelease)

    /**
     * Last successfully fetched release JSON for [owner]/[repo] ([tag] = null means the
     * "latest" endpoint), or null. Fallback for when the API is rate-limited or
     * unreachable, so the last known-good release stays visible instead of an error.
     */
    fun cachedRelease(owner: String, repo: String, tag: String?): String? =
        etagStore.cachedBody(cacheKey(owner, repo, tag))

    fun parseRelease(body: String): GhRelease {
        val o = JSONObject(body)
        val assets = o.optJSONArray("assets")?.let { array ->
            List(array.length()) { i ->
                val a = array.getJSONObject(i)
                GhAsset(
                    id = a.getLong("id"),
                    name = a.getString("name"),
                    size = a.optLong("size", 0),
                    browserDownloadUrl = a.getString("browser_download_url"),
                    contentType = a.optString("content_type").ifBlank { null },
                )
            }
        } ?: emptyList()
        return GhRelease(
            id = o.getLong("id"),
            tagName = o.getString("tag_name"),
            name = o.optString("name").ifBlank { null },
            body = o.optString("body").ifBlank { null },
            prerelease = o.optBoolean("prerelease", false),
            draft = o.optBoolean("draft", false),
            publishedAt = o.optString("published_at").ifBlank { null },
            assets = assets,
        )
    }

    /** Streams [url] into [destination], reporting progress. Never follows non-HTTPS URLs. */
    fun download(url: String, destination: java.io.File, onProgress: (Long, Long) -> Unit): Result<Long> = runCatching {
        require(url.startsWith("https://")) { "refusing non-HTTPS download: $url" }
        val connection = open(url)
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.instanceFollowRedirects = true
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code downloading $url")
        val total = connection.contentLengthLong
        destination.parentFile?.mkdirs()
        destination.outputStream().use { out ->
            connection.inputStream.use { input ->
                val buffer = ByteArray(256 * 1024)
                var written = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    written += read
                    onProgress(written, total)
                }
                return@runCatching written
            }
        }
    }

    private fun <T> getJson(path: String, cacheKey: String, decode: (String) -> T): GitHubResult<T> =
        runCatching {
            val url = API_BASE + path
            val connection = open(url)
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            etagStore.etagFor(cacheKey)?.let { connection.setRequestProperty("If-None-Match", it) }
            connection.connect()
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    etagStore.cachedBody(cacheKey)?.let { GitHubResult.NotModified(it) }
                        ?: GitHubResult.HttpError(code, rateLimited = false)
                }

                403, 429 -> {
                    val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                    GitHubResult.HttpError(code, rateLimited = remaining == "0")
                }

                404 -> GitHubResult.HttpError(code, rateLimited = false)
                in 200..299 -> {
                    val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val etag = connection.getHeaderField("ETag")
                    etagStore.save(cacheKey, etag, body)
                    GitHubResult.Ok(decode(body), etag)
                }

                else -> GitHubResult.HttpError(code, rateLimited = false)
            }.also { connection.disconnect() }
        }.getOrElse { GitHubResult.NetworkError(it.message ?: "network error") }

    private fun cacheKey(owner: String, repo: String, tag: String?): String =
        if (tag == null) "$owner/$repo/latest" else "$owner/$repo/$tag"

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "myApps-android")
        }

    companion object {
        const val API_BASE = "https://api.github.com"
    }
}
