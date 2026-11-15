package com.patbaumgartner.android.myapps.domain.model

/** A downloadable asset of a GitHub release. */
data class AssetInfo(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
)

/** A parsed GitHub release, already validated for the channel it was fetched for. */
data class ReleaseInfo(
    val id: Long,
    val tag: String,
    val name: String,
    val notes: String,
    val prerelease: Boolean,
    val publishedAt: String,
    val assets: List<AssetInfo>,
)

/** Raw GitHub REST API shapes (api.github.com), parsed with org.json. */
data class GhAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    val browserDownloadUrl: String,
    val contentType: String? = null,
)

data class GhRelease(
    val id: Long,
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val publishedAt: String? = null,
    val assets: List<GhAsset> = emptyList(),
)

data class GhRepo(
    val name: String,
    val description: String? = null,
    val htmlUrl: String? = null,
)
