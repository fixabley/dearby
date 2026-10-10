package io.wid.dearby.adaptor.security

import com.webauthn4j.util.exception.WebAuthnException
import io.wid.dearby.application.Ceremony
import io.wid.dearby.application.InvalidPasskeyRequestException
import io.wid.dearby.application.PasskeyOptions
import io.wid.dearby.application.PasskeyRejectedException
import io.wid.dearby.application.PasskeyService
import io.wid.dearby.application.PasskeyService.Companion.DEFAULT_DISPLAY_NAME
import io.wid.dearby.application.PasskeyService.Companion.MAX_DISPLAY_NAME
import io.wid.dearby.application.StoredChallenge
import io.wid.dearby.application.TokenPair
import io.wid.dearby.application.TokenService
import io.wid.dearby.application.UserRepository
import io.wid.dearby.application.WebAuthnChallengeRepository
import io.wid.dearby.domain.User
import io.wid.dearby.domain.UserRole
import org.springframework.security.web.webauthn.api.*
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule
import org.springframework.security.web.webauthn.management.*
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.*

// 패스키 검증은 Spring Security(webauthn4j)에 맡긴다
@Service
class WebAuthnPasskeyService(
    private val operations: WebAuthnRelyingPartyOperations,
    private val userEntities: PublicKeyCredentialUserEntityRepository,
    private val challenges: WebAuthnChallengeRepository,
    private val users: UserRepository,
    private val tokens: TokenService,
    private val transaction: TransactionTemplate,
    private val properties: WebAuthnProperties,
) : PasskeyService {
    // options 직렬화와 credential 역직렬화에 WebAuthn 표준 JSON(base64url) 형식을 쓰는 전용 매퍼
    private val mapper = JsonMapper.builder()
        .addModule(WebauthnJacksonModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    // 계정은 아직 없다. 새 UserId와 user handle을 정해 challenge와 함께 저장하고, 가입 완료 때 계정을 만든다
    override fun registrationOptions(displayName: String?): PasskeyOptions {
        val name = displayName?.trim().takeUnless { it.isNullOrEmpty() } ?: DEFAULT_DISPLAY_NAME
        if (name.length > MAX_DISPLAY_NAME) throw InvalidPasskeyRequestException("displayName이 너무 깁니다")
        val stored = StoredChallenge(
            challenge = Bytes.random().bytes,
            userId = UUID.randomUUID(),
            userEntityId = Bytes.random().toBase64UrlString(),
            displayName = name,
        )
        val challengeId = challenges.issue(Ceremony.REGISTRATION, stored)
        return PasskeyOptions(challengeId, mapper.valueToTree(creationOptions(stored)))
    }

    override fun register(challengeId: UUID, credentialJson: JsonNode): TokenPair {
        val credential = parse(credentialJson, AuthenticatorAttestationResponse::class.java)
        val stored = challenges.consume(challengeId, Ceremony.REGISTRATION)
            ?: throw PasskeyRejectedException("challenge가 없거나 만료되었습니다")
        val user = User(stored.userId!!, listOf(UserRole.USER))
        val options = creationOptions(stored)
        // 검증이 실패하면 계정·user entity도 함께 롤백된다
        return transaction.execute {
            users.create(user)
            userEntities.save(options.user)
            rejectOnFailure {
                operations.registerCredential(
                    ImmutableRelyingPartyRegistrationRequest(options, RelyingPartyPublicKey(credential, stored.displayName!!))
                )
            }
            tokens.issue(user)
        }!!
    }

    override fun authenticationOptions(): PasskeyOptions {
        val stored = StoredChallenge(Bytes.random().bytes, null, null, null)
        val challengeId = challenges.issue(Ceremony.AUTHENTICATION, stored)
        return PasskeyOptions(challengeId, mapper.valueToTree(requestOptions(stored)))
    }

    override fun authenticate(challengeId: UUID, credentialJson: JsonNode): TokenPair {
        val credential = parse(credentialJson, AuthenticatorAssertionResponse::class.java)
        val stored = challenges.consume(challengeId, Ceremony.AUTHENTICATION)
            ?: throw PasskeyRejectedException("challenge가 없거나 만료되었습니다")
        return transaction.execute {
            // 모르는 패스키는 IllegalArgumentException
            val entity = rejectOnFailure {
                operations.authenticate(RelyingPartyAuthenticationRequest(requestOptions(stored), credential))
            }
            val user = runCatching { UUID.fromString(entity.name) }.getOrNull()?.let(users::findById)
                ?: throw PasskeyRejectedException("패스키에 연결된 계정이 없습니다")
            tokens.issue(user)
        }!!
    }

    // 완료 요청에서 같은 값으로 다시 만들어 검증한다. registerCredential은 rp.id·challenge·UV·pubKeyCredParams·user.id를 쓴다
    private fun creationOptions(stored: StoredChallenge): PublicKeyCredentialCreationOptions =
        PublicKeyCredentialCreationOptions.builder()
            .attestation(AttestationConveyancePreference.NONE)
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
            .user(
                ImmutablePublicKeyCredentialUserEntity.builder()
                    .id(Bytes.fromBase64(stored.userEntityId!!))
                    .name(stored.userId!!.toString())
                    .displayName(stored.displayName!!)
                    .build()
            )
            .rp(PublicKeyCredentialRpEntity.builder().id(properties.rpId).name(properties.rpName).build())
            .build()

    // allowCredentials를 비워 기기에 있는 패스키 중에서 고르게 한다
    private fun requestOptions(stored: StoredChallenge): PublicKeyCredentialRequestOptions =
        PublicKeyCredentialRequestOptions.builder()
            .challenge(Bytes(stored.challenge))
            .rpId(properties.rpId)
            .timeout(WebAuthnChallengeRepository.TTL)
            .userVerification(USER_VERIFICATION)
            .build()

    private fun <R : AuthenticatorResponse> parse(json: JsonNode, response: Class<R>): PublicKeyCredential<R> {
        val type = mapper.typeFactory.constructParametricType(PublicKeyCredential::class.java, response)
        return try {
            mapper.treeToValue(json, type)
        } catch (e: RuntimeException) {
            throw InvalidPasskeyRequestException("credential 형식이 올바르지 않습니다")
        }
    }

    // 검증 실패(webauthn4j)와 모르는/중복 credential(IllegalArgumentException)은 인증 실패로 다룬다
    private inline fun <T> rejectOnFailure(block: () -> T): T = try {
        block()
    } catch (e: WebAuthnException) {
        throw PasskeyRejectedException("패스키 검증에 실패했습니다")
    } catch (e: IllegalArgumentException) {
        throw PasskeyRejectedException("패스키 검증에 실패했습니다")
    }

    companion object {
        // 패스키만으로 로그인하므로 기기 잠금(생체·PIN) 확인을 요구한다
        val USER_VERIFICATION: UserVerificationRequirement = UserVerificationRequirement.REQUIRED
    }
}
