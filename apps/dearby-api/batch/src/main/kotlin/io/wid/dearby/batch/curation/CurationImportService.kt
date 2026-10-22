package io.wid.dearby.batch.curation

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Address
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.LogoFile
import io.wid.dearby.domain.OrganizationLogo
import io.wid.dearby.domain.ProgramLogo
import io.wid.dearby.domain.Tag
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 운영 데이터 가져오기(admin-v1.md "운영 데이터 가져오기 — 2026-10-22"): 조사한 대상자 조건·태그·장소·로고를 운영자 API와 같은 검증으로 채운다.
// 빈 것만 채우고(항목의 overwrite면 바꾼다), 운영자 지정 필드(curated_fields)는 건드리지 않으며, 채운 값을 운영자 지정으로 남기지 않는다.
// apply=false(기본)면 계획만 돌려주고 아무것도 쓰지 않는다. 항목마다 트랜잭션 하나
@Service
class CurationImportService(
    private val activities: ActivityRepository,
    private val curations: ActivityCurationRepository,
    private val programs: ProgramRepository,
    private val organizations: OrganizationRepository,
    private val tags: TagRepository,
    private val programLogos: ProgramLogoRepository,
    private val organizationLogos: OrganizationLogoRepository,
    private val transactions: TransactionTemplate,
    private val json: JsonMapper,
) {

    // 한 항목의 한 필드에 대한 결과. action은 SET(쓸 것·쓴 것) 또는 SKIP
    data class Line(val target: String, val field: String, val action: String, val detail: String)

    fun import(file: Path, apply: Boolean): List<Line> {
        val root = json.readTree(Files.readString(file))
        val base = file.toAbsolutePath().parent
        val lines = mutableListOf<Line>()
        root.path("activities").forEach { item -> lines += item(apply) { activity(item, apply) } }
        root.path("programs").forEach { item -> lines += item(apply) { program(item, apply) } }
        root.path("organizationLogos").forEach { item -> lines += item(apply) { organizationLogo(item, base, apply) } }
        root.path("programLogos").forEach { item -> lines += item(apply) { programLogo(item, base, apply) } }
        return lines
    }

    // 항목 하나를 트랜잭션 하나로. 실패하면 그 항목만 SKIP(이유)으로 남기고 다음으로 간다
    private fun item(apply: Boolean, work: () -> List<Line>): List<Line> = try {
        if (apply) transactions.execute { work() }!! else work()
    } catch (e: Exception) {
        listOf(Line("item", "-", SKIP, e.message ?: e.javaClass.simpleName))
    }

    private fun activity(item: JsonNode, apply: Boolean): List<Line> {
        val id = item.path("id").asLong()
        val target = "activity $id"
        val activity = activities.findById(id, publicationStatus = null) ?: return listOf(Line(target, "-", SKIP, "활동 없음"))
        val curated = curations.findAllByActivityId(listOf(id)).singleOrNull()?.curatedFields.orEmpty().toSet()
        val overwrite = item.path("overwrite").asBoolean(false)
        val lines = mutableListOf<Line>()

        item.get("eligibility")?.takeUnless { it.isNull }?.let { node ->
            val group = EligibilityJson.parse(node, lenient = false) ?: return@let lines.add(Line(target, ELIGIBILITY, SKIP, "빈 조건"))
            lines += when {
                ELIGIBILITY in curated -> Line(target, ELIGIBILITY, SKIP, "운영자 지정")
                activity.eligibility != null && !overwrite -> Line(target, ELIGIBILITY, SKIP, "이미 있음")
                else -> Line(target, ELIGIBILITY, SET, EligibilityJson.write(group)).also { if (apply) curations.updateEligibility(id, group, curate = false) }
            }
        }

        item.get("tagNames")?.let { names ->
            val (resolved, unknown) = resolveTags(names, item.path("tagNameEn"))
            lines += unknown.map { Line(target, TAGS, SKIP, "사전에 없음, 영문 이름 없음: $it") }
            lines += when {
                resolved.isEmpty() -> Line(target, TAGS, SKIP, "쓸 태그 없음")
                TAGS in curated -> Line(target, TAGS, SKIP, "운영자 지정")
                activity.tags.isNotEmpty() && !overwrite -> Line(target, TAGS, SKIP, "이미 있음: ${activity.tags.joinToString { it.name }}")
                else -> Line(target, TAGS, SET, resolved.joinToString { it.name }).also { if (apply) tags.setActivityTags(id, tagIds(resolved), curate = false) }
            }
        }

        item.get("place")?.takeUnless { it.isNull }?.let { node ->
            val place = place(node)
            lines += when {
                PLACE in curated -> Line(target, PLACE, SKIP, "운영자 지정")
                activity.activitySchedule.address != null && !overwrite -> Line(target, PLACE, SKIP, "이미 있음: ${activity.activitySchedule.address!!.addressLine1}")
                else -> Line(target, PLACE, SET, "${place.addressLine1} | ${place.addressLine2} | ${place.lat},${place.lng}").also { if (apply) curations.updatePlace(id, place, curate = false) }
            }
        }
        return lines
    }

    private fun program(item: JsonNode, apply: Boolean): List<Line> {
        val id = item.path("id").asLong()
        val target = "program $id"
        val program = programs.findAllById(listOf(id)).singleOrNull() ?: return listOf(Line(target, "-", SKIP, "프로그램 없음"))
        val names = item.get("tagNames") ?: return emptyList()
        val (resolved, unknown) = resolveTags(names, item.path("tagNameEn"))
        return unknown.map { Line(target, TAGS, SKIP, "사전에 없음, 영문 이름 없음: $it") } + when {
            resolved.isEmpty() -> Line(target, TAGS, SKIP, "쓸 태그 없음")
            program.tags.isNotEmpty() && !item.path("overwrite").asBoolean(false) -> Line(target, TAGS, SKIP, "이미 있음: ${program.tags.joinToString { it.name }}")
            else -> Line(target, TAGS, SET, resolved.joinToString { it.name }).also { if (apply) tags.setProgramTags(id, tagIds(resolved)) }
        }
    }

    private fun organizationLogo(item: JsonNode, base: Path, apply: Boolean): List<Line> {
        val id = item.path("organizationId").asLong()
        val target = "organization $id"
        if (organizations.findAllById(listOf(id)).isEmpty()) return listOf(Line(target, LOGO, SKIP, "조직 없음"))
        if (organizationLogos.find(id) != null && !item.path("overwrite").asBoolean(false)) return listOf(Line(target, LOGO, SKIP, "이미 있음"))
        val (type, data) = logoFile(base, item.path("file").asString())
        if (apply) organizationLogos.save(OrganizationLogo(id, type, data, OffsetDateTime.now(ZoneOffset.UTC)))
        return listOf(Line(target, LOGO, SET, "$type ${data.size} bytes"))
    }

    private fun programLogo(item: JsonNode, base: Path, apply: Boolean): List<Line> {
        val id = item.path("programId").asLong()
        val target = "program $id"
        if (programs.findAllById(listOf(id)).isEmpty()) return listOf(Line(target, LOGO, SKIP, "프로그램 없음"))
        if (programLogos.find(id) != null && !item.path("overwrite").asBoolean(false)) return listOf(Line(target, LOGO, SKIP, "이미 있음"))
        val (type, data) = logoFile(base, item.path("file").asString())
        if (apply) programLogos.save(ProgramLogo(id, type, data, OffsetDateTime.now(ZoneOffset.UTC)))
        return listOf(Line(target, LOGO, SET, "$type ${data.size} bytes"))
    }

    // 사전 이름(한글·영문, 띄어쓰기·대소문자 무시)으로 찾는다. 없는 이름은 tagNameEn에 영문이 있을 때만 새 태그 후보(id 0)가 되고, 실제로 쓸 때 만든다
    private fun resolveTags(names: JsonNode, english: JsonNode): Pair<List<Tag>, List<String>> {
        val wanted = buildList<String> { names.forEach { node: JsonNode -> node.asString("").trim().takeIf { it.isNotEmpty() }?.let(::add) } }.distinct()
        val found = tags.findByNames(wanted)
        val known = found.flatMap { listOfNotNull(it.name, it.nameEn) }.map(::key).toSet()
        val missing = wanted.filter { key(it) !in known }
        val (creatable, unknown) = missing.partition { name: String -> english.path(name).asString("").isNotBlank() }
        return (found + creatable.map { name: String -> Tag(NEW_TAG, name, english.path(name).asString("").trim()) }).distinctBy { key(it.name) } to unknown
    }

    private fun tagIds(resolved: List<Tag>): List<Id> = resolved.map { if (it.id == NEW_TAG) tags.create(it.name, it.nameEn).id else it.id }

    private fun key(name: String) = name.replace(Regex("\\s"), "").lowercase()

    // 장소는 넷 다 있어야 한다(DB 제약과 같다). 이름은 address_line1, 주소는 address_line2
    private fun place(node: JsonNode): Address {
        val name = node.path("name").asString("").trim()
        val address = node.path("address").asString("").trim()
        require(name.isNotEmpty() && address.isNotEmpty() && node.path("lat").isNumber && node.path("lng").isNumber) { "장소는 name·address·lat·lng가 모두 필요합니다" }
        return Address(node.path("lat").asDouble(), node.path("lng").asDouble(), name, address)
    }

    // 확장자로 형식을 정하고 어드민 올리기와 같은 검사(PNG·JPEG·WebP 1MB 이하)를 거친다
    private fun logoFile(base: Path, file: String): Pair<String, ByteArray> {
        val path = base.resolve(file).normalize()
        val data = Files.readAllBytes(path)
        val type = when (path.fileName.toString().substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> null
        }
        return LogoFile.contentType(type, data) to data
    }

    private companion object {
        const val SET = "SET"
        const val SKIP = "SKIP"
        // curated_fields 이름(core CuratedFields와 같다)
        const val ELIGIBILITY = "eligibility"
        const val TAGS = "tags"
        const val PLACE = "place"
        const val LOGO = "logo"
        const val NEW_TAG: Id = 0L
    }
}
