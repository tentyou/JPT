package com.example.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SharedPhotoLinksTest {
    @Test fun oneCaptureIsAvailableToEverySelectedAsset() {
        val files = Files.createTempDirectory("shared-photo-test").toFile()
        try {
            val source = files.resolve("photos/asset-a/photo_1.jpg").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            val links = SharedPhotoLinks.link(source, listOf("asset-a", "asset-b", "asset-c", "asset-b"))
            assertEquals(3, links.size)
            links.forEach { assertArrayEquals(source.readBytes(), it.readBytes()) }
            links.forEach { assertEquals(3, SharedPhotoLinks.sharedCount(it)) }
            links[1].delete()
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), source.readBytes())
        } finally {
            files.deleteRecursively()
        }
    }
}
