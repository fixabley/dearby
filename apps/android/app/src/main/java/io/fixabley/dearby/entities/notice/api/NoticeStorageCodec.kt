package io.fixabley.dearby.entities.notice.api

import io.fixabley.dearby.entities.notice.model.NoticeModel
import io.fixabley.dearby.entities.notice.model.NoticeApplication
import io.fixabley.dearby.entities.notice.model.NoticeContext
import io.fixabley.dearby.entities.notice.model.NoticeLocation
import io.fixabley.dearby.entities.notice.model.NoticePhase
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.entities.notice.model.VenueCoordinates
import io.fixabley.dearby.entities.notice.model.NoticeSource
import io.fixabley.dearby.entities.notice.model.NoticeEvidence
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Per-notice versioned binary payload, never a catalog blob or organization object graph. */
internal object NoticeStorageCodec {
    const val VERSION = 1
    fun encode(value: NoticeModel): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(VERSION)
            out.string(value.id); out.string(value.title); out.string(value.aiDescription); out.optional(value.organizationId)
            out.string(value.targetUser); out.string(value.participationCondition)
            with(value.applicationInformation) {
                out.string(summary); out.optional(opensAt); out.optional(opensOn); out.optional(closesAt); out.optional(closesOn)
                out.string(timezone); out.optional(url); out.strings(methods); out.strings(requiredDocuments); out.strings(submissionLocations)
            }
            with(value.location) {
                out.string(summary); out.string(mode); out.string(status)
                out.list(venues) { venue ->
                    optional(venue.phase); optional(venue.name); optional(venue.address); writeBoolean(venue.coordinates != null)
                    venue.coordinates?.let { writeDouble(it.latitude); writeDouble(it.longitude) }
                }
            }
            out.list(value.schedules) { phase ->
                string(phase.phase); optional(phase.startsAt); optional(phase.startsOn); optional(phase.endsAt); optional(phase.endsOn)
                string(phase.timezone); string(phase.mode); optional(phase.onlineUrl)
            }
            out.strings(value.benefits); out.strings(value.issues); out.string(value.sourceURL); out.strings(value.categoryPath)
            out.roles(value.contexts); out.writeBoolean(value.edition != null); value.edition?.let(out::writeInt)
            out.roles(value.organizationLinks)
            out.list(value.sources) { source ->
                string(source.id); optional(source.url); optional(source.kind); optional(source.checkedAt); optional(source.access); optional(source.note)
            }
            out.list(value.evidence) { evidence -> string(evidence.sourceId); string(evidence.locator); string(evidence.fieldPath); optional(evidence.sourceURL) }
            out.string(value.descriptionProvenance)
        }
    }.toByteArray()

    fun decode(payload: ByteArray): NoticeModel = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        require(input.readInt() == VERSION) { "Unsupported notice codec" }
        val id = input.string(); val title = input.string(); val description = input.string(); val organizationId = input.optional()
        val target = input.string(); val condition = input.string()
        val application = NoticeApplication(input.string(), input.optional(), input.optional(), input.optional(), input.optional(),
            input.string(), input.optional(), input.strings(), input.strings(), input.strings())
        val location = NoticeLocation(input.string(), input.string(), input.string(), input.list {
            NoticeVenue(optional(), optional(), optional(), if (readBoolean()) VenueCoordinates(readDouble(), readDouble()) else null)
        })
        val phases = input.list { NoticePhase(string(), optional(), optional(), optional(), optional(), string(), string(), optional()) }
        val benefits = input.strings(); val issues = input.strings(); val sourceURL = input.string(); val categories = input.strings()
        val contexts = input.roles(); val edition = if (input.readBoolean()) input.readInt() else null
        val links = input.roles()
        val sources = input.list { NoticeSource(string(), optional(), optional(), optional(), optional(), optional()) }
        val evidence = input.list { NoticeEvidence(string(), string(), string(), optional()) }
        val provenance = input.string()
        require(input.available() == 0) { "Trailing notice payload" }
        NoticeModel(id, title, description, organizationId, target, condition, application, location, phases, benefits, issues,
            sourceURL, categories, contexts, edition, links, sources, evidence, provenance)
    }

    private fun DataOutputStream.string(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.string(): String { val size = readInt(); require(size in 0..available()); return ByteArray(size).also(::readFully).toString(Charsets.UTF_8) }
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); value?.let { string(it) } }
    private fun DataInputStream.optional(): String? = if (readBoolean()) string() else null
    private fun <T> DataOutputStream.list(values: List<T>, write: DataOutputStream.(T) -> Unit) { writeInt(values.size); values.forEach { write(it) } }
    private fun <T> DataInputStream.list(read: DataInputStream.() -> T): List<T> { val size = readInt(); require(size in 0..100_000); return List(size) { read.invoke(this) } }
    private fun DataOutputStream.strings(values: List<String>) = list(values) { string(it) }
    private fun DataInputStream.strings() = list { string() }
    private fun DataOutputStream.roles(values: List<NoticeContext>) = list(values) { string(it.organizationId); string(it.role) }
    private fun DataInputStream.roles() = list { NoticeContext(string(), string()) }
}
