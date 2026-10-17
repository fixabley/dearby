package io.wid.dearby.adaptor.web.v1.guest

import org.springframework.boot.context.properties.ConfigurationProperties

// 웹 Next 서버와 같은 값(GUEST_PROXY_SECRET, 32자 이상). 비어 있으면 /v1/guest/*는 503(native-v1.md "웹 비로그인 명함 저장")
@ConfigurationProperties("dearby.guest")
data class GuestProperties(val proxySecret: String? = null)
