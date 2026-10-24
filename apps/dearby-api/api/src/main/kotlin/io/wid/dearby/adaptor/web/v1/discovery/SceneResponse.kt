package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.v1.activities.HighlightResponse
import io.wid.dearby.application.discovery.Scene

// 스토리형 쇼츠 장면(native-v1.md "맞춤 대기열"): {type, …}. 앱은 아는 type만 그리고 모르는 것은 건너뛴다
object SceneResponse {
    fun from(scene: Scene, name: String): Map<String, Any?> = when (scene) {
        is Scene.Hook -> mapOf("type" to "HOOK", "hook" to scene.hook, "name" to name)
        is Scene.Highlights -> mapOf("type" to "HIGHLIGHTS", "items" to scene.items.map { HighlightResponse(it.icon.code, it.text) })
        is Scene.Eligibility -> mapOf("type" to "ELIGIBILITY", "text" to scene.text, "items" to scene.items.map { mapOf("icon" to it.icon, "text" to it.text) })
        is Scene.Benefit -> mapOf("type" to "BENEFIT", "text" to scene.text)
        is Scene.Deadline -> mapOf("type" to "DEADLINE", "dday" to scene.dday, "label" to scene.label, "deadlineText" to scene.deadlineText, "reasons" to scene.reasons)
    }
}
