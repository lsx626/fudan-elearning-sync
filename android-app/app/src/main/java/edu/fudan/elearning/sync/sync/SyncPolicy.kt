package edu.fudan.elearning.sync.sync

import edu.fudan.elearning.sync.data.FileItem
import java.util.Locale

/**
 * 增量判定与删除安全闸门（纯逻辑，便于单测）。
 *
 * 两条硬性不变量：
 * 1. **下载判定**：只有文件缺失、状态不是 downloaded、大小/更新时间变化、
 *    或本地文件不存在/长度不符时才重下；全量同步则无条件重下。
 * 2. **删除判定**：只有「课程文件列表完整成功」时，才允许把本轮未出现的
 *    `file_id` 视为远端删除；任何 API 失败都不能导致标记或删除。
 */
object SyncPolicy {

    /** 安装包/可执行文件等「驳杂」扩展名。 */
    private val INSTALLER_EXTENSIONS = setOf(
        "exe", "msi", "dmg", "pkg", "apk", "deb", "rpm", "jar", "iso", "img",
        "bin", "dll", "sys", "cmd", "bat", "com", "scr", "7z", "rar", "tar"
    )

    /** 是否需要下载该文件。 */
    fun shouldDownload(
        full: Boolean,
        record: FileItem?,
        remoteSize: Long,
        remoteUpdatedAt: String,
        localExists: Boolean,
        localLength: Long
    ): Boolean {
        if (full) return true
        if (record == null) return true
        if (record.status != STATUS_DOWNLOADED) return true
        if (record.size != remoteSize) return true
        if (remoteUpdatedAt.isNotEmpty() && record.updatedAt != remoteUpdatedAt) return true
        if (!localExists) return true
        // 已知远端大小且本地长度不符：说明上次落盘不完整
        if (remoteSize > 0 && localLength != remoteSize) return true
        return false
    }

    /** 只有列表完整成功时才允许标记远端删除。 */
    fun shouldMarkRemoteMissing(listingSucceeded: Boolean): Boolean = listingSucceeded

    /** 本轮列表里已出现的 file_id 中，哪些既有的本地记录需要标记为远端删除。 */
    fun remoteMissingIds(existing: List<FileItem>, listedIds: Set<Long>, listingSucceeded: Boolean): List<Long> {
        if (!shouldMarkRemoteMissing(listingSucceeded)) return emptyList()
        return existing.filter { it.fileId !in listedIds && it.status != STATUS_REMOTE_MISSING }
            .map { it.fileId }
    }

    /** 是否跳过「驳杂」文件（安装包、可执行文件、Canvas 系统封面图）。 */
    fun shouldSkip(remoteName: String): Boolean {
        val lower = remoteName.lowercase(Locale.ROOT)
        val ext = lower.substringAfterLast('.', "")
        if (ext in INSTALLER_EXTENSIONS) return true
        if (lower.contains("course_image")) return true
        return false
    }

    const val STATUS_DOWNLOADED = "downloaded"
    const val STATUS_FAILED = "failed"
    const val STATUS_REMOTE_MISSING = "remote_missing"
    const val STATUS_PENDING = "pending"
}
