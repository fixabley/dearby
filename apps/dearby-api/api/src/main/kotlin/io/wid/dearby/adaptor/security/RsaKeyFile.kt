package io.wid.dearby.adaptor.security

import com.nimbusds.jose.jwk.RSAKey
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.*

// 개인키 파일만 두고 공개키는 개인키에서 계산한다
fun loadOrCreateRsaKey(path: Path): RSAKey {
    if (Files.notExists(path)) {
        try {
            create(path)
        } catch (_: FileAlreadyExistsException) {
            // 다른 서버가 먼저 만들었으면 그 키를 쓴다
        }
    }
    val der = Base64.getMimeDecoder().decode(Files.readString(path).replace(Regex("-----[A-Z ]+-----"), ""))
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateCrtKey
    val publicKey =
        keyFactory.generatePublic(RSAPublicKeySpec(privateKey.modulus, privateKey.publicExponent)) as RSAPublicKey
    return RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build()
}

private fun create(path: Path) {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(keyPair.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
    path.toAbsolutePath().parent?.let(Files::createDirectories)
    val ownerOnly = if ("posix" in path.fileSystem.supportedFileAttributeViews()) {
        arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    } else {
        emptyArray()
    }
    Files.newByteChannel(path, setOf(CREATE_NEW, WRITE), *ownerOnly)
        .use { it.write(java.nio.ByteBuffer.wrap(pem.toByteArray())) }
}
