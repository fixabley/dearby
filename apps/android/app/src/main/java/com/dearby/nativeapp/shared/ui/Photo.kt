package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp

/**
 * Loads a photo by its API path (`photoUrl`, contract "프로필 사진"): the reads need the session, so the app provides a loader on its authorised client
 * instead of an image library. Null when it cannot be read; the avatar then keeps the initial.
 */
val LocalPhotoLoader = staticCompositionLocalOf<suspend (String) -> ImageBitmap?> { { null } }

/** The person's round photo when [photoUrl] loads, otherwise [InitialAvatar]. Decorative: the name is always next to it. */
@Composable fun PersonAvatar(name: String, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val load = LocalPhotoLoader.current
    val photo by produceState<ImageBitmap?>(null, photoUrl) { value = photoUrl?.let { load(it) } }
    photo?.let { Image(it, null, modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop) } ?: InitialAvatar(name, size, modifier)
}
