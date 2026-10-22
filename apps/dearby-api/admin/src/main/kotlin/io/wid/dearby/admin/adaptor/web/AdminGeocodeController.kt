package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.geocode.GeocodeFailedException
import io.wid.dearby.application.geocode.GeocodeResult
import io.wid.dearby.application.geocode.Geocoder
import io.wid.dearby.domain.InvalidInputException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// 장소 검색(admin-v1.md "활동 장소와 지오코딩"): GET /v1/admin/geocode?q= → {items:[{name, address, lat, lng, source}]}. ADMIN만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/geocode")
class AdminGeocodeController(private val geocoder: Geocoder) {

    @GetMapping
    fun search(@RequestParam(required = false) q: String?): GeocodeResponse {
        val query = q?.trim().orEmpty()
        if (query.length !in MIN_LENGTH..MAX_LENGTH) throw InvalidInputException("검색어는 ${MIN_LENGTH}~${MAX_LENGTH}자여야 합니다")
        return GeocodeResponse(geocoder.search(query).take(LIMIT).map(GeocodeItemResponse::from))
    }

    // 제공자 호출 실패는 502로 알린다(운영자가 다시 시도하거나 직접 입력한다)
    @ExceptionHandler(GeocodeFailedException::class)
    fun geocodeFailed(e: GeocodeFailedException): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.message ?: "장소 검색에 실패했습니다")

    private companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 100
        const val LIMIT = 10
    }
}
