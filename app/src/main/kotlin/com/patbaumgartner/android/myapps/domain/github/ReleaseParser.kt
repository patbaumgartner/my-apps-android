package com.patbaumgartner.android.myapps.domain.github

import com.patbaumgartner.android.myapps.domain.model.AssetInfo
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.model.GhRelease
import com.patbaumgartner.android.myapps.domain.model.ReleaseInfo

/**
 * Pure conversion from GitHub API DTOs to domain releases, including the channel rules:
 * - STABLE maps to `/releases/latest`, which GitHub guarantees is not a prerelease;
 *   a prerelease there is still rejected defensively.
 * - SNAPSHOT maps to `/releases/tags/snapshot` and must be a prerelease.
 */
object ReleaseParser {

    fun parse(gh: GhRelease, channel: Channel): Result<ReleaseInfo> {
        if (gh.draft) return Result.failure(ReleaseRejectedException("release ${gh.tagName} is a draft"))
        when (channel) {
            Channel.STABLE -> if (gh.prerelease) {
                return Result.failure(ReleaseRejectedException("latest release ${gh.tagName} is a prerelease"))
            }

            Channel.SNAPSHOT -> if (!gh.prerelease) {
                return Result.failure(
                    ReleaseRejectedException("snapshot release ${gh.tagName} is not a prerelease"),
                )
            }
        }
        return Result.success(
            ReleaseInfo(
                id = gh.id,
                tag = gh.tagName,
                name = gh.name?.ifBlank { gh.tagName } ?: gh.tagName,
                notes = gh.body?.trim().orEmpty(),
                prerelease = gh.prerelease,
                publishedAt = gh.publishedAt.orEmpty(),
                assets = gh.assets.map { AssetInfo(it.id, it.name, it.size, it.browserDownloadUrl) },
            ),
        )
    }
}

class ReleaseRejectedException(message: String) : Exception(message)
