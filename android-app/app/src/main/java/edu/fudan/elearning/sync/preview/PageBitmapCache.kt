package edu.fudan.elearning.sync.preview

import android.graphics.Bitmap
import android.util.LruCache
import java.util.Collections

/**
 * 页面位图缓存：按字节数预算的 LRU。展示中的页会被 [pin] 住，淘汰时不回收，
 * 避免渲染到已 recycle 的位图导致崩溃；离开可视区后 [unpin]，可被正常回收。
 */
class PageBitmapCache(
    maxBytes: Int = DEFAULT_MAX_BYTES
) {
    private val pinned: MutableSet<Int> =
        Collections.synchronizedSet(Collections.newSetFromMap(HashMap()))

    private val lru = object : LruCache<Int, Bitmap>(maxBytes) {
        override fun sizeOf(key: Int, value: Bitmap): Int =
            runCatching { value.byteCount }.getOrDefault(1)

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !pinned.contains(key) && !oldValue.isRecycled) {
                runCatching { oldValue.recycle() }
            }
        }
    }

    operator fun get(key: Int): Bitmap? = lru.get(key)

    fun put(key: Int, value: Bitmap) {
        lru.put(key, value)
    }

    fun pin(key: Int) {
        pinned.add(key)
    }

    fun unpin(key: Int) {
        pinned.remove(key)
    }

    companion object {
        private const val DEFAULT_MAX_BYTES = 48 * 1024 * 1024 // 48 MiB
    }
}