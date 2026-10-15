package io.wid.dearby.batch.application

// 페이지를 받을 수 없음. message는 collection_runs.rejected_reasons에 남는다
class PageFetchException(message: String) : RuntimeException(message)
