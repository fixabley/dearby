package io.wid.dearby.batch.curation

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.catalog.CatalogWrites
import io.wid.dearby.application.eligibility.EligibilityJson
import io.wid.dearby.application.organization.OrganizationLogoRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramCoverRepository
import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.application.timetable.TimetableService
import io.wid.dearby.domain.timetable.SessionInput
import io.wid.dearby.domain.timetable.SessionKind
import io.wid.dearby.domain.timetable.Speaker
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.TimetableRules
import io.wid.dearby.domain.timetable.TrackInput
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Address
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.CoverFile
import io.wid.dearby.domain.LogoFile
import io.wid.dearby.domain.OrganizationLogo
import io.wid.dearby.domain.ProgramCover
import io.wid.dearby.domain.ProgramCoverInfo
import io.wid.dearby.domain.ProgramLogo
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.Tag
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.curation.CollectionSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 운영 데이터 가져오기(admin-v1.md "운영 데이터 가져오기 — 2026-10-22"): 조사한 조직·프로그램(수집 설정 포함)을 만들고,
// 대상자 조건·태그·장소·로고를 채우고, 초안을 게시하거나 지운다. 운영자 API와 같은 검증(core CatalogWrites 등)을 거친다.
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
    private val programCovers: ProgramCoverRepository,
    private val transactions: TransactionTemplate,
    private val catalog: CatalogWrites,
    private val timetables: TimetableService,
    private val json: JsonMapper,
) {

    // 한 항목의 한 필드에 대한 결과. action은 SET(쓸 것·쓴 것) 또는 SKIP
    data class Line(val target: String, val field: String, val action: String, val detail: String)

    fun import(file: Path, apply: Boolean): List<Line> {
        val root = json.readTree(Files.readString(file))
        val base = file.toAbsolutePath().parent
        val lines = mutableListOf<Line>()
        // key → id. dry run에서 새로 만들 것은 PENDING(아직 id 없음)이라 뒤 항목은 계획만 보인다
        val orgKeys = mutableMapOf<String, Id>()
        val programKeys = mutableMapOf<String, Id>()
        root.path("organizations").forEach { item -> lines += item(apply) { organization(item, orgKeys, apply) } }
        root.path("newPrograms").forEach { item -> lines += item(apply) { newProgram(item, orgKeys, programKeys, apply) } }
        root.path("activities").forEach { item -> lines += item(apply) { activity(item, apply) } }
        root.path("programs").forEach { item -> lines += item(apply) { program(item, apply) } }
        root.path("organizationLogos").forEach { item -> lines += item(apply) { organizationLogo(item, base, orgKeys, apply) } }
        root.path("programLogos").forEach { item -> lines += item(apply) { programLogo(item, base, programKeys, apply) } }
        root.path("programCovers").forEach { item -> lines += item(apply) { programCover(item, base, programKeys, apply) } }
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
        val curation = curations.findAllByActivityId(listOf(id)).singleOrNull()
        val published = curation?.publicationStatus == PublicationStatus.PUBLISHED
        // 지우기는 초안만(어드민 DELETE와 같다: 수집 출처를 남겨 다시 만들지 않고 DRAFT_DISMISSED 피드백). 지우면 다른 필드는 보지 않는다
        if (item.path("dismiss").asBoolean(false)) {
            if (published) return listOf(Line(target, DISMISS, SKIP, "게시된 활동은 가져오기로 지우지 않습니다"))
            if (apply) catalog.deleteActivity(id)
            return listOf(Line(target, DISMISS, SET, activity.name))
        }
        val curated = curation?.curatedFields.orEmpty().toSet()
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
                else -> Line(target, TAGS, SET, resolved.joinToString { it.name }).also { if (apply) tags.setActivityTags(id, tagIds(resolved, item), curate = false) }
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

        // 하위 시간표(native-v1.md "트랙·세션 시간표"): 어드민 PUT timetable과 같은 검증. 이미 세션이 있으면 overwrite일 때만 바꾼다
        item.path("timetables").forEach { node -> lines += timetable(id, node, apply) }

        // 게시는 필드를 채운 뒤(어드민 게시와 같다: 상태만 바꾼다)
        if (item.path("publish").asBoolean(false)) {
            lines += if (published) {
                Line(target, PUBLISH, SKIP, "이미 게시됨")
            } else {
                Line(target, PUBLISH, SET, activity.name).also { if (apply) curations.updatePublicationStatus(id, PublicationStatus.PUBLISHED) }
            }
        }
        return lines
    }

    // {scheduleId, overwrite?, tracks: [{name, room?}], sessions: [{track?(순번), kind, title, startAt, endAt, speakers?: [{name, affiliation?}], tagNames?, description?}]}.
    // 세션 태그는 사전 이름(띄어쓰기·대소문자 무시)으로 찾고 모르는 이름은 버린다. 운영자 지정과 같이 저장되어 그 블록은 curated가 된다
    private fun timetable(activityId: Id, node: JsonNode, apply: Boolean): Line {
        val scheduleId = node.path("scheduleId").asLong()
        val target = "activity $activityId schedule $scheduleId"
        val existing = timetables.adminTimetable(activityId, scheduleId)
        if (existing.sessions.isNotEmpty() && !node.path("overwrite").asBoolean(false)) return Line(target, TIMETABLE, SKIP, "이미 있음: 세션 ${existing.sessions.size}개")
        val text = { n: JsonNode, field: String -> n.path(field).asString("").trim().ifEmpty { null } }
        val input = TimetableInput(
            tracks = buildList { node.path("tracks").forEach { add(TrackInput(null, it.path("name").asString(""), text(it, "room"))) } },
            sessions = buildList {
                node.path("sessions").forEach { s ->
                    val names = buildList { s.path("tagNames").forEach { add(it.asString("")) } }
                    add(
                        SessionInput(
                            id = null,
                            track = s.get("track")?.takeIf { it.isNumber }?.asInt(),
                            kind = SessionKind.valueOf(s.path("kind").asString("TALK")),
                            title = s.path("title").asString(""),
                            description = text(s, "description"),
                            startAt = OffsetDateTime.parse(s.path("startAt").asString()),
                            endAt = OffsetDateTime.parse(s.path("endAt").asString()),
                            speakers = buildList { s.path("speakers").forEach { add(Speaker(it.path("name").asString(""), text(it, "affiliation"))) } },
                            tagIds = if (names.isEmpty()) emptyList() else tags.findByNames(names).map { it.id }.take(TimetableRules.MAX_TAGS),
                        )
                    )
                }
            },
        )
        // dry run도 같은 검증을 거친다(쓰지 않음). 기존 트랙·세션은 새로 만든 것으로 바뀐다(id가 바뀌어 그 블록의 세션 찜은 지워진다)
        if (apply) timetables.replace(activityId, scheduleId, input)
        else TimetableRules.validate(input, TimetableRules.window(curations.findSchedule(activityId, scheduleId)!!.schedule))
        return Line(target, TIMETABLE, SET, "트랙 ${input.tracks.size}개, 세션 ${input.sessions.size}개")
    }

    // 같은 상위 조직 아래 같은 이름(띄어쓰기·대소문자 무시)이 있으면 그 조직을 쓴다. 없으면 어드민 만들기와 같은 검증으로 만든다
    private fun organization(item: JsonNode, orgKeys: MutableMap<String, Id>, apply: Boolean): List<Line> {
        val name = item.path("name").asString("").trim()
        val key = item.path("key").asString("").ifEmpty { name }
        val parent = reference(item, "parentId", "parentKey", orgKeys)
        val target = "organization $key"
        if (parent == PENDING) {
            orgKeys[key] = PENDING
            return listOf(Line(target, CREATED, SET, "$name (상위 조직도 새로 만듦)"))
        }
        val existing = organizations.search(name, parent, rootsOnly = parent == null, after = null, size = SEARCH_SIZE)
            .firstOrNull { key(it.name) == key(name) && it.parentOrganizationId == parent }
        if (existing != null) {
            orgKeys[key] = existing.id
            return listOf(Line(target, REUSED, SKIP, "이미 있음: id ${existing.id}"))
        }
        val nameEn = item.path("nameEn").asString("").ifBlank { null }
        orgKeys[key] = if (apply) catalog.createOrganization(name, nameEn, parent) else PENDING
        return listOf(Line(target, CREATED, SET, "$name${orgKeys[key].takeIf { it != PENDING }?.let { " → id $it" } ?: ""}"))
    }

    // 같은 조직에 같은 이름의 프로그램이 있으면 건너뛴다. 만들 때 수집 설정도 어드민 PUT collection-settings와 같은 검증으로 넣는다
    private fun newProgram(item: JsonNode, orgKeys: Map<String, Id>, programKeys: MutableMap<String, Id>, apply: Boolean): List<Line> {
        val name = item.path("name").asString("").trim()
        val key = item.path("key").asString("").ifEmpty { name }
        val target = "program $key"
        val organizationId = reference(item, "organizationId", "organizationKey", orgKeys)
            ?: return listOf(Line(target, CREATED, SKIP, "조직이 없습니다"))
        val category = item.path("category").asString("").ifBlank { null }?.let(ActivityCategory::parse)
        val collection = item.get("collection")?.takeUnless { it.isNull }?.let(::collection)?.let(catalog::validate)
        val plan = listOfNotNull(name, category?.name, collection?.officialUrl).joinToString(" | ")
        if (organizationId != PENDING) {
            val existing = programs.search(name, organizationId, after = null, size = SEARCH_SIZE).firstOrNull { key(it.name) == key(name) }
            if (existing != null) {
                programKeys[key] = existing.id
                return listOf(Line(target, CREATED, SKIP, "같은 조직에 같은 이름의 프로그램이 있음: id ${existing.id}"))
            }
        }
        if (!apply) {
            programKeys[key] = PENDING
            return listOf(Line(target, CREATED, SET, plan))
        }
        val nameEn = item.path("nameEn").asString("").ifBlank { null }
        val id = catalog.createProgram(organizationId, name, nameEn, collection)
        if (category != null) programs.update(id, organizationId, name.trim(), nameEn?.trim(), category)
        programKeys[key] = id
        return listOf(Line(target, CREATED, SET, "$plan → id $id"))
    }

    private fun collection(node: JsonNode) = CollectionSettings(
        officialUrl = node.path("officialUrl").asString("").ifBlank { null },
        allowedHosts = buildList { node.path("allowedHosts").forEach { add(it.asString("")) } },
        intervalDays = node.path("intervalDays").asInt(CollectionSettings.DEFAULT.intervalDays),
        enabled = node.path("enabled").asBoolean(true),
        renderPages = node.path("renderPages").asBoolean(false),
    )

    // 숫자 id 또는 같은 파일 앞쪽 항목의 key. 둘 다 없으면 null(최상위), 모르는 key는 오류
    private fun reference(item: JsonNode, idField: String, keyField: String, keys: Map<String, Id>): Id? {
        item.get(idField)?.takeIf { it.isNumber }?.let { return it.asLong() }
        val key = item.path(keyField).asString("").ifEmpty { return null }
        return keys[key] ?: throw IllegalArgumentException("앞에서 정의하지 않은 $keyField: $key")
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
            else -> Line(target, TAGS, SET, resolved.joinToString { it.name }).also { if (apply) tags.setProgramTags(id, tagIds(resolved, item)) }
        }
    }

    private fun organizationLogo(item: JsonNode, base: Path, orgKeys: Map<String, Id>, apply: Boolean): List<Line> {
        val id = reference(item, "organizationId", "organizationKey", orgKeys) ?: return listOf(Line("organization -", LOGO, SKIP, "조직 없음"))
        val target = "organization $id"
        if (id == PENDING) return listOf(Line("organization ${item.path("organizationKey").asString("")}", LOGO, SET, "새 조직에 올림"))
        if (organizations.findAllById(listOf(id)).isEmpty()) return listOf(Line(target, LOGO, SKIP, "조직 없음"))
        if (organizationLogos.find(id) != null && !item.path("overwrite").asBoolean(false)) return listOf(Line(target, LOGO, SKIP, "이미 있음"))
        val (type, data) = logoFile(base, item.path("file").asString())
        if (apply) organizationLogos.save(OrganizationLogo(id, type, data, OffsetDateTime.now(ZoneOffset.UTC)))
        return listOf(Line(target, LOGO, SET, "$type ${data.size} bytes"))
    }

    private fun programLogo(item: JsonNode, base: Path, programKeys: Map<String, Id>, apply: Boolean): List<Line> {
        val id = reference(item, "programId", "programKey", programKeys) ?: return listOf(Line("program -", LOGO, SKIP, "프로그램 없음"))
        val target = "program $id"
        if (id == PENDING) return listOf(Line("program ${item.path("programKey").asString("")}", LOGO, SET, "새 프로그램에 올림"))
        if (programs.findAllById(listOf(id)).isEmpty()) return listOf(Line(target, LOGO, SKIP, "프로그램 없음"))
        if (programLogos.find(id) != null && !item.path("overwrite").asBoolean(false)) return listOf(Line(target, LOGO, SKIP, "이미 있음"))
        val (type, data) = logoFile(base, item.path("file").asString())
        if (apply) programLogos.save(ProgramLogo(id, type, data, OffsetDateTime.now(ZoneOffset.UTC)))
        return listOf(Line(target, LOGO, SET, "$type ${data.size} bytes"))
    }

    // 대기열 커버(admin-v1.md "프로그램 커버"): 어드민 올리기와 같은 검사(PNG·JPEG·WebP 350KB 이하), 출처 페이지·원본 이미지 주소를 함께 남기고 켠 상태로 둔다
    private fun programCover(item: JsonNode, base: Path, programKeys: Map<String, Id>, apply: Boolean): List<Line> {
        val id = reference(item, "programId", "programKey", programKeys) ?: return listOf(Line("program -", COVER, SKIP, "프로그램 없음"))
        val target = "program $id"
        if (id == PENDING) return listOf(Line("program ${item.path("programKey").asString("")}", COVER, SET, "새 프로그램에 올림"))
        if (programs.findAllById(listOf(id)).isEmpty()) return listOf(Line(target, COVER, SKIP, "프로그램 없음"))
        if (programCovers.find(id) != null && !item.path("overwrite").asBoolean(false)) return listOf(Line(target, COVER, SKIP, "이미 있음"))
        val (type, data) = imageFile(base, item.path("file").asString(), CoverFile::contentType)
        val source = { field: String -> item.path(field).asString("").trim().ifEmpty { null }?.also { require(it.startsWith("https://")) { "${field}는 https 주소여야 합니다" } } }
        val info = ProgramCoverInfo(id, source("sourcePage"), source("sourceImage"), enabled = true, updatedAt = OffsetDateTime.now(ZoneOffset.UTC))
        if (apply) programCovers.save(ProgramCover(info, type, data))
        return listOf(Line(target, COVER, SET, "$type ${data.size} bytes"))
    }

    // 사전 이름(한글·영문, 띄어쓰기·대소문자 무시)으로 찾는다. 없는 이름은 tagNameEn에 영문이 있을 때만 새 태그 후보(id 0)가 되고, 실제로 쓸 때 만든다.
    // 순서는 보낸 순서(프로그램 태그의 position, native-v1.md "분야·성격 태그와 아이콘")
    private fun resolveTags(names: JsonNode, english: JsonNode): Pair<List<Tag>, List<String>> {
        val wanted = buildList<String> { names.forEach { node: JsonNode -> node.asString("").trim().takeIf { it.isNotEmpty() }?.let(::add) } }.distinct()
        val found = tags.findByNames(wanted)
        val known = found.flatMap { listOfNotNull(it.name, it.nameEn) }.map(::key).toSet()
        val missing = wanted.filter { key(it) !in known }
        val (creatable, unknown) = missing.partition { name: String -> english.path(name).asString("").isNotBlank() }
        val candidates = found + creatable.map { name: String -> Tag(NEW_TAG, name, english.path(name).asString("").trim(), TagKind.TRAIT) }
        fun order(tag: Tag) = wanted.indexOfFirst { key(it) == key(tag.name) || tag.nameEn?.let { en -> key(it) == key(en) } == true }
        return candidates.sortedBy(::order).distinctBy { key(it.name) } to unknown
    }

    // 새 태그는 tagKind: {"<이름>": "FIELD"|"TRAIT"}(기본 TRAIT)로 만든다. tagParent: {"<하위 분야>": "<상위 분야>"}는 이 목록의 분야 태그에 상위 분야를 단다
    // (상위 분야는 사전의 최상위 분야 태그, 한 단계. 어긋나면 이 항목 전체를 건너뛴다)
    private fun tagIds(resolved: List<Tag>, item: JsonNode): List<Id> {
        val kinds = item.path("tagKind")
        val created = resolved.map { tag ->
            if (tag.id != NEW_TAG) tag
            else tags.create(tag.name, tag.nameEn, TagKind.valueOf(kinds.path(tag.name).asString(TagKind.TRAIT.name)))
        }
        item.path("tagParent").properties().forEach { (childName, parentName) ->
            val child = created.firstOrNull { key(it.name) == key(childName) } ?: throw IllegalArgumentException("tagParent의 하위 분야가 tagNames에 없음: $childName")
            val parent = tags.findByNames(listOf(parentName.asString())).firstOrNull() ?: throw IllegalArgumentException("tagParent의 상위 분야가 사전에 없음: ${parentName.asString()}")
            require(child.kind == TagKind.FIELD && parent.kind == TagKind.FIELD && parent.parentId == null && parent.id != child.id && !tags.hasChildren(child.id)) {
                "하위 분야는 분야 태그끼리 한 단계만: $childName > ${parent.name}"
            }
            tags.update(child.id, null, null, child.kind, parent.id)
        }
        return created.map { it.id }
    }

    private fun key(name: String) = name.replace(Regex("\\s"), "").lowercase()

    // 장소는 넷 다 있어야 한다(DB 제약과 같다). 이름은 address_line1, 주소는 address_line2
    private fun place(node: JsonNode): Address {
        val name = node.path("name").asString("").trim()
        val address = node.path("address").asString("").trim()
        require(name.isNotEmpty() && address.isNotEmpty() && node.path("lat").isNumber && node.path("lng").isNumber) { "장소는 name·address·lat·lng가 모두 필요합니다" }
        return Address(node.path("lat").asDouble(), node.path("lng").asDouble(), name, address)
    }

    // 확장자로 형식을 정하고 어드민 올리기와 같은 검사(로고 PNG·JPEG·WebP 1MB 이하, 커버 350KB 이하)를 거친다
    private fun logoFile(base: Path, file: String) = imageFile(base, file, LogoFile::contentType)

    private fun imageFile(base: Path, file: String, check: (String?, ByteArray) -> String): Pair<String, ByteArray> {
        val path = base.resolve(file).normalize()
        val data = Files.readAllBytes(path)
        val type = when (path.fileName.toString().substringAfterLast('.').lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> null
        }
        return check(type, data) to data
    }

    private companion object {
        const val SET = "SET"
        const val SKIP = "SKIP"
        // curated_fields 이름(core CuratedFields와 같다)
        const val ELIGIBILITY = "eligibility"
        const val TAGS = "tags"
        const val PLACE = "place"
        const val TIMETABLE = "timetable"
        const val LOGO = "logo"
        const val COVER = "cover"
        const val PUBLISH = "publish"
        const val DISMISS = "dismiss"
        const val CREATED = "created"
        const val REUSED = "reused"
        const val SEARCH_SIZE = 50
        // dry run에서 아직 만들지 않은 조직·프로그램
        const val PENDING: Id = -1L
        const val NEW_TAG: Id = 0L
    }
}
