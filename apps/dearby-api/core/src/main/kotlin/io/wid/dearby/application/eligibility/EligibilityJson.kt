package io.wid.dearby.application.eligibility

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.eligibility.EligibilityCondition
import io.wid.dearby.domain.eligibility.EligibilityField
import io.wid.dearby.domain.eligibility.EligibilityGroup
import io.wid.dearby.domain.eligibility.EligibilityNode
import io.wid.dearby.domain.eligibility.EligibilityOp
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

// 대상자 조건 JSON(native-v1.md "대상자 조건") ↔ 도메인. 키는 camelCase(op, items, field, fieldEn, from, to, values, valuesEn, note, noteEn).
// strict(관리자): 어휘 밖 필드·잘못된 모양은 400. lenient(수집): 어휘 밖·잘못된 조건은 기타(note)로 바꾸고 빈 그룹은 버린다
object EligibilityJson {

    private val json = JsonMapper.builder().findAndAddModules().build()

    private const val MAX_TEXT = 200
    private const val MAX_VALUES = 50

    // null·JSON null이면 조건 없음(null). lenient에서 쓸 만한 조건이 하나도 없으면 null
    fun parse(node: JsonNode?, lenient: Boolean): EligibilityGroup? {
        if (node == null || node.isNull) return null
        if (!node.isObject) return reject(lenient, "대상자 조건은 객체여야 합니다") { null }
        val group = group(node, depth = 1, lenient = lenient) ?: return null
        return group.takeIf { it.items.isNotEmpty() || !lenient }
    }

    fun read(stored: String): EligibilityGroup? = parse(json.readTree(stored), lenient = true)

    fun write(group: EligibilityGroup): String = json.writeValueAsString(toNode(group))

    fun toNode(group: EligibilityGroup): ObjectNode = groupNode(group)

    private fun group(node: JsonNode, depth: Int, lenient: Boolean): EligibilityGroup? {
        if (depth > EligibilityGroup.MAX_DEPTH) return reject(lenient, "대상자 조건은 ${EligibilityGroup.MAX_DEPTH}단계까지 중첩할 수 있습니다") { null }
        val op = when (node.text("op")?.lowercase()) {
            "all", null -> EligibilityOp.ALL
            "any" -> EligibilityOp.ANY
            else -> return reject(lenient, "op는 all 또는 any입니다") { null }
        }
        val itemsNode = node.get("items")
        if (itemsNode == null || itemsNode.isNull || !itemsNode.isArray) return reject(lenient, "items는 배열이어야 합니다") { null }
        val items = (0 until itemsNode.size()).mapNotNull { item(itemsNode[it], depth, lenient) }
        if (items.isEmpty() && !lenient) throw InvalidInputException("items가 비어 있습니다")
        return EligibilityGroup(op, items)
    }

    private fun item(node: JsonNode, parentDepth: Int, lenient: Boolean): EligibilityNode? {
        if (!node.isObject) return reject(lenient, "조건은 객체여야 합니다") { null }
        if (node.has("items") && !node.get("items").isNull) {
            if (parentDepth >= EligibilityGroup.MAX_DEPTH && lenient) {
                // 깊이를 넘는 그룹은 조건만 꺼내 부모에 편다
                val inner = node.get("items")
                if (!inner.isArray) return null
                val flattened = (0 until inner.size()).mapNotNull { item(inner[it], parentDepth, lenient) }
                return flattened.singleOrNull() ?: flattened.takeIf { it.isNotEmpty() }?.let { EligibilityGroup(EligibilityOp.ALL, it) }
            }
            return group(node, parentDepth + 1, lenient)?.takeIf { it.items.isNotEmpty() }
        }
        return condition(node, lenient)
    }

    private fun condition(node: JsonNode, lenient: Boolean): EligibilityCondition? {
        val ko = node.text("field")
        val en = node.text("fieldEn")
        val field = ko?.let(EligibilityField::byKo) ?: en?.let(EligibilityField::byEn)
        if (ko == null && en == null) return reject(lenient, "field가 없습니다") { null }
        if (field == null) {
            // 어휘 밖 필드: 수집이면 기타로 바꾸고 원문을 note에 남긴다
            return reject(lenient, "모르는 field입니다: ${ko ?: en}") { other(node, label = ko ?: en!!) }
        }
        if (ko != null && en != null && !field.en.equals(en.trim(), ignoreCase = true) && EligibilityField.byEn(en) != null) {
            return reject(lenient, "field와 fieldEn이 맞지 않습니다: $ko / $en") { other(node, label = ko) }
        }
        val from = node.number("from", lenient) ?: return null
        val to = node.number("to", lenient) ?: return null
        val values = node.texts("values", lenient) ?: return null
        val valuesEn = node.texts("valuesEn", lenient) ?: return null
        val note = node.text("note")?.take(MAX_TEXT)
        val noteEn = node.text("noteEn")?.take(MAX_TEXT)
        return when (field.kind) {
            EligibilityField.Kind.NUMERIC -> {
                if (from.value == null && to.value == null) return reject(lenient, "${field.ko}에는 from 또는 to가 필요합니다") { other(node, field.ko) }
                if (values.isNotEmpty() || valuesEn.isNotEmpty()) return reject(lenient, "${field.ko}에는 values를 쓸 수 없습니다") { other(node, field.ko) }
                if (from.value != null && to.value != null && from.value > to.value) return reject(lenient, "${field.ko}의 from이 to보다 큽니다") { other(node, field.ko) }
                EligibilityCondition(field, from.value, to.value, note = note, noteEn = noteEn)
            }
            EligibilityField.Kind.SET -> {
                if (from.value != null || to.value != null) return reject(lenient, "${field.ko}에는 from·to를 쓸 수 없습니다") { other(node, field.ko) }
                val paired = pairValues(field, values, valuesEn, lenient) ?: return other(node, field.ko)
                if (paired.isEmpty()) return reject(lenient, "${field.ko}에는 values가 필요합니다") { if (note != null) other(node, field.ko) else null }
                EligibilityCondition(field, values = paired.map { it.first }, valuesEn = paired.map { it.second }, note = note, noteEn = noteEn)
            }
            EligibilityField.Kind.NOTE -> {
                if (note.isNullOrBlank()) return reject(lenient, "기타에는 note가 필요합니다") { null }
                EligibilityCondition(field, note = note.trim(), noteEn = noteEn?.trim()?.ifEmpty { null })
            }
        }
    }

    // 한글·영문 값을 짝 맞춘다. 한쪽만 있으면 고정 어휘로 채우고, 없으면 같은 글자를 쓴다. 길이가 다르면 strict는 400
    private fun pairValues(field: EligibilityField, ko: List<String>, en: List<String>, lenient: Boolean): List<Pair<String, String>>? {
        if (ko.isNotEmpty() && en.isNotEmpty() && ko.size != en.size) return reject(lenient, "${field.ko}의 values와 valuesEn 길이가 다릅니다") { null }
        val size = maxOf(ko.size, en.size)
        return (0 until size).map { index ->
            val k = ko.getOrNull(index)
            val e = en.getOrNull(index)
            when {
                k != null && e != null -> k to e
                k != null -> k to (field.valueEn(k) ?: k)
                else -> (field.valueKo(e!!) ?: e) to e
            }
        }.distinct().take(MAX_VALUES)
    }

    // 어휘 밖·모양이 틀린 조건을 기타로 바꾼 것(수집 전용). 원문 값을 note에 남긴다
    private fun other(node: JsonNode, label: String): EligibilityCondition? {
        val parts = buildList {
            node.text("note")?.let { add(it) }
            val values = node.get("values")?.takeIf { it.isArray }?.let { array -> (0 until array.size()).mapNotNull { array[it].takeIf { v -> v.isTextual }?.asString() } }.orEmpty()
            if (values.isNotEmpty()) add(values.joinToString(", "))
            val from = node.get("from")?.takeIf { it.isNumber }?.decimalValue()?.toPlainString()
            val to = node.get("to")?.takeIf { it.isNumber }?.decimalValue()?.toPlainString()
            if (from != null || to != null) add("${from ?: ""}~${to ?: ""}")
        }
        val note = (listOf(label.trim()) + parts).filter { it.isNotBlank() }.joinToString(": ").take(MAX_TEXT)
        if (note.isBlank()) return null
        return EligibilityCondition(EligibilityField.OTHER, note = note, noteEn = node.text("noteEn")?.take(MAX_TEXT))
    }

    private fun <T> reject(lenient: Boolean, message: String, fallback: () -> T): T =
        if (lenient) fallback() else throw InvalidInputException(message)

    private fun JsonNode.text(key: String): String? = get(key)?.takeIf { it.isTextual }?.asString()?.trim()?.ifEmpty { null }

    private class NumberValue(val value: java.math.BigDecimal?)

    // 숫자가 아닌 값: strict는 400, lenient는 조건 전체를 버린다(null)
    private fun JsonNode.number(key: String, lenient: Boolean): NumberValue? {
        val node = get(key) ?: return NumberValue(null)
        if (node.isNull) return NumberValue(null)
        if (node.isNumber) return NumberValue(node.decimalValue())
        node.asString().trim().toBigDecimalOrNull()?.let { return NumberValue(it) }
        return reject(lenient, "${key}은(는) 숫자여야 합니다") { null }
    }

    private fun JsonNode.texts(key: String, lenient: Boolean): List<String>? {
        val node = get(key) ?: return emptyList()
        if (node.isNull) return emptyList()
        if (!node.isArray) return reject(lenient, "${key}은(는) 배열이어야 합니다") { null }
        val values = (0 until node.size()).mapNotNull { node[it].takeIf { v -> v.isTextual }?.asString()?.trim()?.ifEmpty { null }?.take(MAX_TEXT) }
        if (values.size != node.size() && !lenient) throw InvalidInputException("${key}에는 글자만 넣을 수 있습니다")
        return values
    }

    private fun groupNode(group: EligibilityGroup): ObjectNode = json.createObjectNode().apply {
        put("op", group.op.name.lowercase())
        putArray("items").apply {
            group.items.forEach { item ->
                add(
                    when (item) {
                        is EligibilityGroup -> groupNode(item)
                        is EligibilityCondition -> conditionNode(item)
                    }
                )
            }
        }
    }

    private fun conditionNode(condition: EligibilityCondition): ObjectNode = json.createObjectNode().apply {
        put("field", condition.field.ko)
        put("fieldEn", condition.field.en)
        condition.from?.let { put("from", it) }
        condition.to?.let { put("to", it) }
        if (condition.values.isNotEmpty()) {
            putArray("values").apply { condition.values.forEach { add(it) } }
            putArray("valuesEn").apply { condition.valuesEn.forEach { add(it) } }
        }
        condition.note?.let { put("note", it) }
        condition.noteEn?.let { put("noteEn", it) }
    }
}
