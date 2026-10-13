package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class CleaningTest {

    private fun case(
        id: String = "VC-1",
        createdAt: String? = "2026-10-14T07:00:00Z",
        scores: Triple<Any?, Any?, Any?> = Triple(0.9, 0.8, 0.7),
        flags: List<String> = emptyList(),
        dateOfBirth: String? = "1990-01-01",
        attempt: Int = 1,
    ) = IngestCase(
        caseId = id,
        createdAt = createdAt,
        applicant = IngestApplicant(fullName = "Test Person", dateOfBirth = dateOfBirth, documentType = "ID_CARD"),
        checks = IngestChecks(scores.first, scores.second, scores.third),
        flags = flags,
        attempt = attempt,
        status = "PENDING_REVIEW",
        slaHours = 4,
    )

    @Test
    fun `clean record has no problems`() {
        val c = clean(listOf(case())).single()

        assertThat(c.dataProblems).isEmpty()
        assertThat(c.createdAt).isEqualTo(Instant.parse("2026-10-14T07:00:00Z"))
        assertThat(c.dateOfBirth).isEqualTo(LocalDate.parse("1990-01-01"))
    }

    @Test
    fun `duplicate case id keeps the first record and notes it`() {
        val cleaned = clean(listOf(case(attempt = 1), case(id = "VC-2"), case(attempt = 2)))

        assertThat(cleaned.map { it.source.caseId }).containsExactly("VC-1", "VC-2")
        assertThat(cleaned[0].source.attempt).isEqualTo(1)
        assertThat(cleaned[0].dataProblems).containsExactly("Listed 2 times in the export; the first record was kept.")
    }

    @Test
    fun `day-month-year time without zone is read as central european time and noted`() {
        val c = clean(listOf(case(createdAt = "14.10.2026 11:02"))).single()

        assertThat(c.createdAt).isEqualTo(Instant.parse("2026-10-14T09:02:00Z")) // CEST = UTC+2
        assertThat(c.dataProblems).containsExactly(
            "Creation time had no time zone; read as Central European time (an earlier deadline than UTC). Check in the back office.",
        )
    }

    @Test
    fun `unreadable or missing creation time is unknown and noted`() {
        assertThat(clean(listOf(case(createdAt = "yesterday"))).single().let { it.createdAt to it.dataProblems })
            .isEqualTo(null to listOf("Creation time unreadable; deadline unknown."))
        assertThat(clean(listOf(case(createdAt = null))).single().let { it.createdAt to it.dataProblems })
            .isEqualTo(null to listOf("Creation time missing; deadline unknown."))
    }

    @Test
    fun `invalid scores are stored as missing, never as zero, and noted`() {
        val c = clean(listOf(case(scores = Triple("n/a", 1.7, -0.1)))).single()

        assertThat(listOf(c.scoreDocument, c.scoreFace, c.scoreLiveness)).containsOnlyNulls()
        assertThat(c.dataProblems).containsExactly(
            "Document authenticity score invalid in the export; shown as missing.",
            "Face match score invalid in the export; shown as missing.",
            "Liveness score invalid in the export; shown as missing.",
        )
    }

    @Test
    fun `missing face match is explained by FACE_NOT_CAPTURED, otherwise noted`() {
        val explained = clean(listOf(case(scores = Triple(0.9, null, 0.7), flags = listOf("FACE_NOT_CAPTURED")))).single()
        val unexplained = clean(listOf(case(scores = Triple(0.9, null, 0.7)))).single()

        assertThat(explained.scoreFace).isNull()
        assertThat(explained.dataProblems).isEmpty()
        assertThat(unexplained.dataProblems).containsExactly("Face match score missing in the export.")
    }

    @Test
    fun `whole numbers 0 and 1 are valid scores`() {
        val c = clean(listOf(case(scores = Triple(1, 0, 1)))).single()

        assertThat(listOf(c.scoreDocument, c.scoreFace, c.scoreLiveness)).containsExactly(1.0, 0.0, 1.0)
        assertThat(c.dataProblems).isEmpty()
    }

    @Test
    fun `impossible birth date is unknown and noted without repeating the value`() {
        val c = clean(listOf(case(dateOfBirth = "1990-02-31"))).single()

        assertThat(c.dateOfBirth).isNull()
        assertThat(c.dataProblems).containsExactly("Date of birth invalid in the export.")
        assertThat(c.dataProblems.joinToString()).doesNotContain("1990")
    }
}
