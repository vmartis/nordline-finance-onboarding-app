package finance.nordline.queue

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Repository
class CaseRepository(private val jdbc: JdbcClient) {

    /** Insert or update by case id. Returns true if the case was new. */
    fun upsert(c: IngestCase, createdAt: Instant?): Boolean =
        jdbc.sql(
            """
            insert into verification_case (case_id, status, created_at, sla_hours, full_name, document_type,
                document_number, date_of_birth, attempt, flags, score_document, score_face, score_liveness, notes)
            values (:caseId, :status, :createdAt, :slaHours, :fullName, :documentType,
                :documentNumber, :dateOfBirth, :attempt, :flags, :scoreDocument, :scoreFace, :scoreLiveness, :notes)
            on conflict (case_id) do update set
                -- a pending push never reopens a decided case; a decided push is taken
                status = case when excluded.status = 'PENDING_REVIEW' then verification_case.status
                              else excluded.status end,
                created_at = excluded.created_at, sla_hours = excluded.sla_hours,
                full_name = excluded.full_name, document_type = excluded.document_type,
                document_number = excluded.document_number, date_of_birth = excluded.date_of_birth,
                attempt = excluded.attempt, flags = excluded.flags, score_document = excluded.score_document,
                score_face = excluded.score_face, score_liveness = excluded.score_liveness, notes = excluded.notes,
                updated_at = now()
            returning (xmax = 0) as inserted
            """,
        )
            .param("caseId", c.caseId)
            .param("status", c.status)
            .param("createdAt", createdAt?.let(Timestamp::from))
            .param("slaHours", c.slaHours)
            .param("fullName", c.applicant?.fullName)
            .param("documentType", c.applicant?.documentType)
            .param("documentNumber", c.applicant?.documentNumber)
            .param("dateOfBirth", parseDate(c.applicant?.dateOfBirth))
            .param("attempt", c.attempt)
            .param("flags", c.flags.toTypedArray())
            .param("scoreDocument", c.checks?.documentAuthenticity)
            .param("scoreFace", c.checks?.faceMatch)
            .param("scoreLiveness", c.checks?.liveness)
            .param("notes", c.notes)
            .query(Boolean::class.java)
            .single()

    // Impossible dates (e.g. 31 Feb) are stored as unknown; S3 adds the visible data-problem note.
    private fun parseDate(raw: String?): LocalDate? =
        try { raw?.let(LocalDate::parse) } catch (_: DateTimeParseException) { null }

    fun findDetail(caseId: String): CaseDetail? =
        jdbc.sql(
            """
            select case_id, status, created_at, sla_hours, full_name, document_type, document_number, date_of_birth,
                   attempt, flags, score_document, score_face, score_liveness, notes
            from verification_case where case_id = :caseId
            """,
        ).param("caseId", caseId).query { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            CaseDetail(
                caseId = rs.getString("case_id"),
                status = rs.getString("status"),
                createdAt = rs.getTimestamp("created_at")?.toInstant(),
                slaHours = rs.getInt("sla_hours"),
                fullName = rs.getString("full_name"),
                documentType = rs.getString("document_type"),
                documentNumber = rs.getString("document_number"),
                dateOfBirth = rs.getObject("date_of_birth", LocalDate::class.java),
                attempt = rs.getInt("attempt"),
                flags = (rs.getArray("flags").array as Array<String>).toList(),
                scores = Scores(
                    documentAuthenticity = rs.getBigDecimal("score_document")?.toDouble(),
                    faceMatch = rs.getBigDecimal("score_face")?.toDouble(),
                    liveness = rs.getBigDecimal("score_liveness")?.toDouble(),
                ),
                notes = rs.getString("notes"),
            )
        }.optional().orElse(null)

    fun setStatus(caseId: String, status: String) {
        jdbc.sql("update verification_case set status = :status, updated_at = now() where case_id = :caseId")
            .param("status", status).param("caseId", caseId).update()
    }

    fun findPending(): List<PendingCase> =
        jdbc.sql(
            """
            select case_id, document_type, flags, attempt, created_at, sla_hours
            from verification_case where status = 'PENDING_REVIEW'
            """,
        ).query { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            PendingCase(
                caseId = rs.getString("case_id"),
                documentType = rs.getString("document_type"),
                flags = (rs.getArray("flags").array as Array<String>).toList(),
                attempt = rs.getInt("attempt"),
                createdAt = rs.getTimestamp("created_at")?.toInstant(),
                slaHours = rs.getInt("sla_hours"),
            )
        }.list()
}
