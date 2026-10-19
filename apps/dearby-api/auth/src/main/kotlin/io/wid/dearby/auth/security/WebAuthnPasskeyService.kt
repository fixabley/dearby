package io.wid.dearby.auth.security

import com.webauthn4j.util.exception.WebAuthnException
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.application.auth.*
import io.wid.dearby.application.auth.PasskeyService.Companion.DEFAULT_DISPLAY_NAME
import io.wid.dearby.application.auth.PasskeyService.Companion.MAX_DISPLAY_NAME
import io.wid.dearby.application.user.UserRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.springframework.security.web.webauthn.api.*
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule
import org.springframework.security.web.webauthn.management.*
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer
import java.util.*

// 패스키 검증은 Spring Security(webauthn4j)에 맡긴다
@Service
class WebAuthnPasskeyService(
    private val operations: WebAuthnRelyingPartyOperations,
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val challenges: WebAuthnChallengeRepository,
    private val users: UserRepository,
    private val credentials: PasskeyCredentialRepository,
    private val tokens: TokenService,
    private val transaction: TransactionTemplate,
    private val properties: WebAuthnProperties,
) : PasskeyService {
    // options 직렬화와 credential 역직렬화에 WebAuthn 표준 JSON(base64url) 형식을 쓰는 전용 매퍼
    private val mapper = JsonMapper.builder()
        .addModule(WebauthnJacksonModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    // 새 가입: 계정은 아직 없다. 새 UserId를 정하고, user handle은 그 UUID의 16바이트로 해 challenge와 함께 저장한다. 가입 완료 때 계정을 만든다.
    // 기존 계정에 더하기(userId): 그 계정의 user entity가 있으면 그 id·이름을 그대로 쓰고(덮어쓰지 않는다), 없으면(애플·구글만 쓰던 계정) 새 handle로 만든다.
    // displayName은 새 가입에서는 OS 패스키 목록의 계정 이름이자 패스키 label, 더하기에서는 label만 된다. 이미 있는 패스키는 excludeCredentials로 알려 기기가 덮어쓰지 않게 한다
    override fun registrationOptions(displayName: String?, userId: UserId?): PasskeyOptions {
        val name = displayName?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_DISPLAY_NAME
        if (name.length > MAX_DISPLAY_NAME) throw InvalidInputException("displayName이 너무 깁니다")
        val id = userId ?: UUID.randomUUID()
        val entity = userId?.let { existingEntity(it) }
        val stored = StoredChallenge(
            challenge = Bytes.random().bytes,
            userId = id,
            userEntityId = (entity?.id ?: userHandle(id)).toBase64UrlString(),
            displayName = name,
        )
        val exclude = userId?.let { credentials.findAllByUser(it) }.orEmpty().map {
            PublicKeyCredentialDescriptor.builder().id(Bytes.fromBase64(it.credentialId)).type(PublicKeyCredentialType.PUBLIC_KEY).build()
        }
        val challengeId = challenges.issue(Ceremony.REGISTRATION, stored)
        return PasskeyOptions(challengeId, mapper.valueToTree(creationOptions(stored, entity ?: newUserEntity(stored), exclude)))
    }

    override fun register(challengeId: UUID, credential: JsonNode): TokenPair {
        val parsed = parse(credential, AuthenticatorAttestationResponse::class.java)
        val stored = challenges.consume(challengeId, Ceremony.REGISTRATION)
            ?: throw AuthenticationFailedException("challenge가 없거나 만료되었습니다")
        val userId = stored.userId!!
        // 검증이 실패하면 계정·user entity도 함께 롤백된다. 기존 계정에 더하는 경우 계정과 user entity는 그대로 둔다(이름을 덮어쓰지 않는다)
        return transaction.execute {
            val user = users.findById(userId) ?: User(userId, listOf(UserRole.USER)).also(users::create)
            val existing = userEntities.findById(Bytes.fromBase64(stored.userEntityId!!))
            val entity = existing ?: newUserEntity(stored).also(userEntities::save)
            rejectOnFailure {
                operations.registerCredential(
                    ImmutableRelyingPartyRegistrationRequest(
                        creationOptions(stored, entity),
                        RelyingPartyPublicKey(parsed, stored.displayName!!)
                    )
                )
            }
            tokens.issue(user)
        }
    }

    override fun authenticationOptions(): PasskeyOptions {
        val stored = StoredChallenge(Bytes.random().bytes, null, null, null)
        val challengeId = challenges.issue(Ceremony.AUTHENTICATION, stored)
        return PasskeyOptions(challengeId, mapper.valueToTree(requestOptions(stored)))
    }

    override fun authenticate(challengeId: UUID, credential: JsonNode): TokenPair {
        val parsed = parse(credential, AuthenticatorAssertionResponse::class.java)
        val stored = challenges.consume(challengeId, Ceremony.AUTHENTICATION)
            ?: throw AuthenticationFailedException("challenge가 없거나 만료되었습니다")
        return transaction.execute {
            // 모르는 패스키는 IllegalArgumentException
            val entity = rejectOnFailure {
                operations.authenticate(RelyingPartyAuthenticationRequest(requestOptions(stored), parsed))
            }
            val user = userId(entity)?.let(users::findById)
                ?: throw AuthenticationFailedException("패스키에 연결된 계정이 없습니다")
            tokens.issue(user)
        }
    }

    // 완료 요청에서 같은 값으로 다시 만들어 검증한다. registerCredential은 rp.id·challenge·UV·pubKeyCredParams·user.id를 쓴다
    private fun creationOptions(
        stored: StoredChallenge,
        user: PublicKeyCredentialUserEntity,
        exclude: List<PublicKeyCredentialDescriptor> = emptyList(),
    ): PublicKeyCredentialCreationOptions =
        PublicKeyCredentialCreationOptions.builder()
            .attestation(AttestationConveyancePreference.NONE)
            .excludeCredentials(exclude)
            .pubKeyCredParams(
                PublicKeyCredentialParameters.EdDSA,
                PublicKeyCredentialParameters.ES256,
                PublicKeyCredentialParameters.RS256,
            )
            .authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                    .residentKey(ResidentKeyRequirement.REQUIRED)
                    .userVerification(USER_VERIFICATION)
                    .build()
            )
            .challenge(Bytes(stored.challenge))
            .extensions(ImmutableAuthenticationExtensionsClientInputs(ImmutableAuthenticationExtensionsClientInput.credProps))
            .timeout(WebAuthnChallengeRepository.TTL)
            .user(user)
            .rp(PublicKeyCredentialRpEntity.builder().id(properties.rpId).name(properties.rpName).build())
            .build()

    // 새 user entity. OS 패스키 목록의 계정 이름은 사람이 읽는 이름이고 계정 연결에는 쓰지 않는다(연결은 user handle)
    private fun newUserEntity(stored: StoredChallenge): PublicKeyCredentialUserEntity =
        ImmutablePublicKeyCredentialUserEntity.builder()
            .id(Bytes.fromBase64(stored.userEntityId!!))
            .name(stored.displayName!!)
            .displayName(stored.displayName!!)
            .build()

    // allowCredentials를 비워 기기에 있는 패스키 중에서 고르게 한다
    private fun requestOptions(stored: StoredChallenge): PublicKeyCredentialRequestOptions =
        PublicKeyCredentialRequestOptions.builder()
            .challenge(Bytes(stored.challenge))
            .rpId(properties.rpId)
            .timeout(WebAuthnChallengeRepository.TTL)
            .userVerification(USER_VERIFICATION)
            .build()

    // user handle(16바이트) = 사용자 UUID. 2026-10-15 이전 가입분은 user handle이 무작위라 name에 남은 UUID로 찾는다
    private fun userId(entity: PublicKeyCredentialUserEntity): UUID? =
        entity.id.bytes.takeIf { it.size == 16 }?.let { ByteBuffer.wrap(it).let { buffer -> UUID(buffer.long, buffer.long) } }
            ?: runCatching { UUID.fromString(entity.name) }.getOrNull()

    private fun userHandle(userId: UUID): Bytes = Bytes.fromBase64(io.wid.dearby.application.auth.userHandle(userId))

    // 계정의 user entity: user handle로, 없으면 2026-10-15 이전 가입분의 name(UUID 문자열)으로 찾는다. 애플·구글만 쓰던 계정은 없다
    private fun existingEntity(userId: UserId): PublicKeyCredentialUserEntity? {
        users.findById(userId) ?: throw AuthenticationFailedException("계정을 찾을 수 없습니다")
        return userEntities.findById(userHandle(userId)) ?: userEntities.findByUsername(userId.toString())
    }

    private fun <R : AuthenticatorResponse> parse(json: JsonNode, response: Class<R>): PublicKeyCredential<R> {
        val type = mapper.typeFactory.constructParametricType(PublicKeyCredential::class.java, response)
        return try {
            mapper.treeToValue(json, type)
        } catch (e: RuntimeException) {
            throw InvalidInputException("credential 형식이 올바르지 않습니다")
        }
    }

    // 검증 실패(webauthn4j)와 모르는/중복 credential(IllegalArgumentException)은 인증 실패로 다룬다
    // InvalidInputException도 IllegalArgumentException이라 이 블록 안에서 던지면 401이 된다. 형식 검사는 블록 밖에서 한다
    private inline fun <T> rejectOnFailure(block: () -> T): T = try {
        block()
    } catch (e: WebAuthnException) {
        throw AuthenticationFailedException("패스키 검증에 실패했습니다")
    } catch (e: IllegalArgumentException) {
        throw AuthenticationFailedException("패스키 검증에 실패했습니다")
    }

    companion object {
        // 패스키만으로 로그인하므로 기기 잠금(생체·PIN) 확인을 요구한다
        val USER_VERIFICATION: UserVerificationRequirement = UserVerificationRequirement.REQUIRED
    }
}
