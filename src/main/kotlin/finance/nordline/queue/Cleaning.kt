package finance.nordline.queue

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A pushed case after cleaning: parsed values plus plain-English notes on what was wrong. Notes never repeat personal data. */
data class CleanCase(
    val source: IngestCase,
    val createdAt: Instant?,
    val dateOfBirth: LocalDate?,
    val scoreDocument: Double?,
    val scoreFace: Double?,
    val scoreLiveness: Double?,
    val dataProblems: List<String>,
)

private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** The legacy export writes some times without a zone. Central European time gives the earlier deadline, so no case looks safer than it is. */
private val EXPORT_LOCAL_ZONE = ZoneId.of("Europe/Bratislava")

/** Cleans a batch without guessing: duplicates keep the first record, anything unreadable becomes "unknown" with a note. */
fun clean(batch: List<IngestCase>): List<CleanCase> {
    val counts = batch.groupingBy { it.caseId }.eachCount()
    return batch.distinctBy { it.caseId }.map { c ->
        val problems = mutableListOf<String>()
        counts.getValue(c.caseId).takeIf { it > 1 }?.let {
            problems += "Listed $it times in the export; the first record was kept."
        }
        val createdAt = parseCreatedAt(c.createdAt, problems)
        val dateOfBirth = parseDateOfBirth(c.applicant?.dateOfBirth, problems)
        val checks = c.checks
        CleanCase(
            source = c,
            createdAt = createdAt,
            dateOfBirth = dateOfBirth,
            scoreDocument = score("Document authenticity", checks?.documentAuthenticity, explained = false, problems),
            scoreFace = score("Face match", checks?.faceMatch, explained = "FACE_NOT_CAPTURED" in c.flags, problems),
            scoreLiveness = score("Liveness", checks?.liveness, explained = false, problems),
            dataProblems = problems,
        )
    }
}

private fun parseCreatedAt(raw: String?, problems: MutableList<String>): Instant? {
    if (raw.isNullOrBlank()) {
        problems += "Creation time missing; deadline unknown."
        return null
    }
    try {
        return Instant.parse(raw)
    } catch (_: DateTimeParseException) {
    }
    return try {
        LocalDateTime.parse(raw, DAY_MONTH_YEAR).atZone(EXPORT_LOCAL_ZONE).toInstant().also {
            problems += "Creation time had no time zone; read as Central European time (an earlier deadline than UTC). " +
                "Check in the back office."
        }
    } catch (_: DateTimeParseException) {
        problems += "Creation time unreadable; deadline unknown."
        null
    }
}

private fun parseDateOfBirth(raw: String?, problems: MutableList<String>): LocalDate? {
    if (raw == null) return null
    return try {
        LocalDate.parse(raw)
    } catch (_: DateTimeParseException) {
        problems += "Date of birth invalid in the export."
        null
    }
}

/** A score is a number from 0 to 1. Anything else is "missing", never 0. */
private fun score(name: String, raw: Any?, explained: Boolean, problems: MutableList<String>): Double? {
    if (raw == null) {
        if (!explained) problems += "$name score missing in the export."
        return null
    }
    val value = (raw as? Number)?.toDouble()
    if (value == null || !value.isFinite() || value < 0.0 || value > 1.0) {
        problems += "$name score invalid in the export; shown as missing."
        return null
    }
    return value
}
