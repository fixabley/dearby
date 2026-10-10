package io.wid.dearby.domain

data class TokenPair(val accessToken: String, val refreshToken: String, val expiresIn: Long)
