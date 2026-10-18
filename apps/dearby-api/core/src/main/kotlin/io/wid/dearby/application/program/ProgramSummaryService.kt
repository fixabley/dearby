package io.wid.dearby.application.program

import io.wid.dearby.domain.InvalidInputException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 발견 화면의 프로그램 묶음 조회(native-v1.md "발견 프로그램 묶음")
@Service
class ProgramSummaryService(private val summaries: ProgramSummaryRepository) {

    @Transactional(readOnly = true)
    fun search(condition: ProgramSummarySearch): ProgramSummaryPage {
        if (condition.size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        if (condition.cursor < 0) throw InvalidInputException("cursor가 올바르지 않습니다")
        if ((condition.q?.length ?: 0) > MAX_QUERY_LENGTH) throw InvalidInputException("검색어는 ${MAX_QUERY_LENGTH}자까지입니다")
        // 다음 페이지가 있는지 알기 위해 하나 더 읽는다
        val found = summaries.search(condition.copy(size = condition.size + 1), OffsetDateTime.now(ZoneOffset.UTC))
        val page = found.take(condition.size)
        return ProgramSummaryPage(page, if (found.size > condition.size) condition.cursor + condition.size else null)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_QUERY_LENGTH = 100
    }
}
