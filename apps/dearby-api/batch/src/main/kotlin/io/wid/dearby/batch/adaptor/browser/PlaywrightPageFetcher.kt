package io.wid.dearby.batch.adaptor.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitUntilState
import io.wid.dearby.batch.application.FetchedPage
import io.wid.dearby.batch.application.PageFetchException
import io.wid.dearby.batch.application.RenderingPageFetcher
import io.wid.dearby.batch.application.UrlGuard
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

// 헤드리스 Chromium(Playwright)으로 페이지를 실행한 뒤 document.body.innerText를 본문으로 쓴다(admin-v1.md "브라우저 렌더링").
// 브라우저는 처음 쓸 때 띄우고 close()에서 내린다. 이미지·글꼴·미디어와 허용 호스트 밖(또는 사설 IP) 요청은 route로 막는다
@Component
class PlaywrightPageFetcher : RenderingPageFetcher {

    private val log = LoggerFactory.getLogger(javaClass)
    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @Synchronized
    override fun fetch(url: String, allowedHosts: List<String>): FetchedPage {
        val start = UrlGuard.check(URI(url), allowedHosts)
        val context = try {
            browser().newContext(Browser.NewContextOptions().setUserAgent(USER_AGENT).setJavaScriptEnabled(true))
        } catch (e: PlaywrightException) {
            throw PageFetchException("렌더링 실패: 브라우저를 띄우지 못함(${firstLine(e)})")
        }
        try {
            val privateHosts = mutableMapOf<String, Boolean>()
            context.route("**/*") { route ->
                val request = route.request()
                val host = runCatching { URI(request.url()).host?.lowercase() }.getOrNull()
                val blocked = request.resourceType() in BLOCKED_RESOURCES ||
                    host == null ||
                    !UrlGuard.hostAllowed(host, allowedHosts) ||
                    (allowedHosts.isNotEmpty() && privateHosts.getOrPut(host) { UrlGuard.resolvesToPrivate(host) })
                if (blocked) route.abort() else route.resume()
            }
            val page = context.newPage()
            val response = try {
                page.navigate(start.toString(), Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT_MS).setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
            } catch (_: TimeoutError) {
                throw PageFetchException("렌더링 실패: ${(NAVIGATION_TIMEOUT_MS / 1000).toInt()}초 안에 열리지 않음")
            } catch (e: PlaywrightException) {
                throw PageFetchException("렌더링 실패: ${firstLine(e)}")
            }
            if (response != null && response.status() != 200) throw PageFetchException("HTTP ${response.status()}")
            // 네트워크 유휴는 최대 5초만 기다린다. 끝없이 폴링하는 사이트는 그때까지 그려진 본문을 쓴다
            try { page.waitForLoadState(LoadState.NETWORKIDLE, Page.WaitForLoadStateOptions().setTimeout(IDLE_TIMEOUT_MS)) } catch (_: TimeoutError) { }
            val finalUrl = UrlGuard.check(URI(page.url()), allowedHosts)
            val text = (page.evaluate("() => document.body ? document.body.innerText : ''") as? String).orEmpty()
            if (text.length > MAX_CHARS) throw PageFetchException("${MAX_CHARS / 1_000_000}백만 자 초과")
            val links = (page.evaluate("() => Array.from(document.querySelectorAll('a[href]')).map(a => a.href)") as? List<*>).orEmpty()
                .mapNotNull { it as? String }.filter { it.isNotEmpty() }.toSet()
            return FetchedPage(url = finalUrl.toString(), text = text, links = links)
        } catch (e: PlaywrightException) {
            throw PageFetchException("렌더링 실패: ${firstLine(e)}")
        } finally {
            runCatching { context.close() }
        }
    }

    private fun browser(): Browser {
        browser?.let { return it }
        val created = Playwright.create()
        playwright = created
        return created.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).also {
            browser = it
            log.info("Headless Chromium launched for rendered collection")
        }
    }

    @PreDestroy
    @Synchronized
    override fun close() {
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
        browser = null
        playwright = null
    }

    private fun firstLine(e: Exception) = e.message?.lineSequence()?.firstOrNull()?.take(200)

    private companion object {
        const val NAVIGATION_TIMEOUT_MS = 15_000.0
        const val IDLE_TIMEOUT_MS = 5_000.0
        const val MAX_CHARS = 3_000_000
        const val USER_AGENT = "DearbyCollector/1.0 (+https://dearby.wid.io.kr)"
        val BLOCKED_RESOURCES = setOf("image", "font", "media")
    }
}
