package com.example.personalvault.util

/**
 * Free-tier limits for Bayganikade (the Cafe Bazaar edition). Easy Archive (the Google Play
 * edition on the `main` branch) has no such limits — this file exists only on the
 * `bazaar-edition` branch, tied to its freemium model: a fixed number of folders, and a fixed
 * number of files per folder, free forever; Pro removes both caps.
 *
 * These are just numeric constants and a couple of pure check functions — deliberately no
 * enforcement logic here. Each call site (folder creation, file creation) is responsible for
 * checking against these and showing the upgrade prompt itself, so the limit is enforced right
 * at the moment and place the person is stopped, with a message that makes sense in context.
 */
object TierLimits {
    const val FREE_MAX_FOLDERS = 3
    const val FREE_MAX_FILES_PER_FOLDER = 30

    fun folderLimitReached(currentFolderCount: Int, isPro: Boolean): Boolean =
        !isPro && currentFolderCount >= FREE_MAX_FOLDERS

    fun fileLimitReached(currentFileCountInFolder: Int, isPro: Boolean): Boolean =
        !isPro && currentFileCountInFolder >= FREE_MAX_FILES_PER_FOLDER
}
