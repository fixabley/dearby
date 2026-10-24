package io.wid.dearby.batch.adaptor.claude

import io.wid.dearby.application.collection.StorylessActivity
import io.wid.dearby.batch.application.StoryWriter
import io.wid.dearby.batch.application.WrittenStory
import io.wid.dearby.domain.HighlightIcon
import io.wid.dearby.domain.Id
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

// 저장된 공개 설명만 넘겨 hook·highlights를 JSON으로 받는다(도구 없음, 수집과 같은 모델·구독)
@Component
class ClaudeCliStoryWriter(private val cli: ClaudeCli, private val json: JsonMapper) : StoryWriter {

    override fun write(activities: List<StorylessActivity>): Map<Id, WrittenStory> {
        val prompt = json.writeValueAsString(activities.map { mapOf("id" to it.id, "name" to it.name, "description" to it.description.take(MAX_DESCRIPTION)) })
        val answer = cli.ask(SYSTEM, prompt)
        val node = json.readTree(answer.substring(answer.indexOf('{').coerceAtLeast(0), answer.lastIndexOf('}') + 1))
        return node.path("items").toList().associate { item ->
            item.path("id").asLong() to WrittenStory(
                item.path("hook").takeIf { it.isString }?.asString(),
                item.path("highlights").toList().map { it.path("icon").asString(null) to it.path("text").asString(null) },
            )
        }
    }

    private companion object {
        const val MAX_DESCRIPTION = 4000
        val SYSTEM = """
            너는 활동 소개 카드의 글을 쓴다. 입력은 활동 JSON 배열 [{id, name, description}]이다. 설명에 있는 내용만 쓰고 지어내지 않는다.
            각 활동마다 hook(처음 보는 사람에게 건네는 30자 안팎 한국어 한 줄, 이름을 되풀이하지 않는다, 60자 이하)과
            highlights(하는 일·받는 것을 40자 이하 짧은 문장 최대 3개, 각 icon은 ${HighlightIcon.entries.joinToString(", ") { it.code }} 중 하나:
            비용·지원금 cost, 기간·일정 period, 팀 활동 team, 멘토링 mentor, 상·상금 award, 장소 place, 강연·발표 talk, 참가권·혜택 ticket)를 만든다.
            날짜·마감은 넣지 않는다. 설명으로 만들 수 없으면 hook은 null, highlights는 빈 배열.
            다른 말 없이 JSON 하나만 답한다: {"items":[{"id":1,"hook":"…","highlights":[{"icon":"team","text":"…"}]}]}
        """.trimIndent()
    }
}
