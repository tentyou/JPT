package com.example.data

import java.io.File
import java.nio.file.Files

/** Shares one captured image across asset folders. Hard links keep one physical file on supported storage. */
object SharedPhotoLinks {
    fun link(source: File, assetUids: List<String>): List<File> {
        if (!source.exists()) return emptyList()
        val distinctUids = assetUids.distinct()
        return distinctUids.mapNotNull { uid ->
            val directory = File(source.parentFile?.parentFile?.parentFile, "photos/$uid").apply { mkdirs() }
            val target = File(directory, source.name)
            val linked = if (target.absolutePath == source.absolutePath) source else runCatching {
                    if (target.exists()) target.delete()
                    Files.createLink(target.toPath(), source.toPath())
                    target
                }.getOrElse { runCatching { source.copyTo(target, overwrite = true) }.getOrNull() }
            linked?.also {
                if (distinctUids.size > 1) File(directory, ".${source.name}.shared").writeText(distinctUids.joinToString("\n"))
            }
        }
    }

    fun linkedAssetUids(image: File): List<String> {
        val value = File(image.parentFile, ".${image.name}.shared").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        if (value.isBlank() || value.toIntOrNull() != null) return emptyList()
        return value.lines().map(String::trim).filter(String::isNotBlank).distinct()
    }

    fun sharedCount(image: File): Int {
        val marker = File(image.parentFile, ".${image.name}.shared").takeIf { it.isFile } ?: return 1
        val value = marker.readText().trim()
        return value.toIntOrNull()?.coerceAtLeast(1) ?: value.lines().count { it.isNotBlank() }.coerceAtLeast(1)
    }

    fun deleteForAll(image: File, filesDir: File): List<String> {
        val uids = linkedAssetUids(image)
        uids.forEach { uid ->
            val directory = File(filesDir, "photos/$uid")
            File(directory, image.name).delete()
            File(directory, ".${image.name}.shared").delete()
        }
        return uids
    }
}
