package com.dearby.nativeapp.app

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dearby.nativeapp.entities.account.api.AccountException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Profile and card photos (contract "프로필 사진"): read through the account client — with the session when signed in, so a 401 refreshes like any
 * other call — and kept in memory by the full `photoUrl`. Its `?v=` changes with the photo, so a cached entry is never stale.
 */
class PhotoStore(private val account: AccountViewModel) {
    private val cache = LruCache<String, ImageBitmap>(32)

    suspend fun load(path: String): ImageBitmap? = cache.get(path) ?: try {
        val bytes = if (account.state.value.phase == AccountPhase.SIGNED_IN) account.authorized { account.client.photo(path, it) } else account.client.photo(path, null)
        decode(bytes)?.also { cache.put(path, it) }
    } catch (e: AccountException) { null }

    /** The photo just uploaded under its new [path], so the profile does not download what it already has. */
    suspend fun put(path: String, jpeg: ByteArray) { decode(jpeg)?.let { cache.put(path, it) } }

    fun clear() = cache.evictAll()

    private suspend fun decode(bytes: ByteArray) = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
}
