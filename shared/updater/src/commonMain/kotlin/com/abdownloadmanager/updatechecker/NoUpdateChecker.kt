package com.abdownloadmanager.updatechecker

import io.github.z4kn4fein.semver.Version
import ir.amirab.util.platform.Arch
import ir.amirab.util.platform.Platform

/**
 * Used by this customized build: an official update would replace it with the
 * upstream app and drop the custom features, so "check for updates" always says up to date.
 */
class NoUpdateChecker(currentVersion: Version) : UpdateChecker(currentVersion) {
    override suspend fun getMyPlatformLatestVersion(): UpdateInfo {
        return UpdateInfo(
            version = currentVersion,
            platform = Platform.getCurrentPlatform(),
            arch = Arch.getCurrentArch(),
            updateSource = emptyList(),
            changeLog = "",
        )
    }
}
