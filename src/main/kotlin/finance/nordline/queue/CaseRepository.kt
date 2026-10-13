package finance.nordline.queue

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate

enum class UpsertResult { INSERTED, UPDATED, UNCHANGED }

@Repository
class CaseRepository(private val jdbc: JdbcClient) {

    /**
     * Insert or update by case id. An identical record is left alone, so sending the same data again changes nothing.
     * A pending push never reopens a decided case; a decided push is taken.
     */
    fun upsert(c: CleanCase): UpsertResult {
        val inserted = jdbc.sql(
            """
            insert into verification_case (case_id, status, created_at, sla_hours, full_name, document_type,
                document_number, date_of_birth, attempt, flags, score_document, score_face, score_liveness, notes,
                data_problems)
            values (:caseId, :status, :createdAt, :slaHours, :fullName, :documentType,
                :documentNumber, :dateOfBirth, :attempt, :flags, :scoreDocument, :scoreFace, :scoreLiveness, :notes,
                :dataProblems)
            on conflict (case_id) do update set
                status = case when excluded.status = 'PENDING_REVIEW' then verification_case.status
                              else excluded.status end,
                created_at = excluded.created_at, sla_hours = excluded.sla_hours,
                full_name = excluded.full_name, document_type = excluded.document_type,
                document_number = excluded.document_number, date_of_birth = excluded.date_of_birth,
                attempt = excluded.attempt, flags = excluded.flags, score_document = excluded.score_document,
                score_face = excluded.score_face, score_liveness = excluded.score_liveness, notes = excluded.notes,
                data_problems = excluded.data_problems, updated_at = now()
            where (case when excluded.status = 'PENDING_REVIEW' then verification_case.status else excluded.status end,
                   excluded.created_at, excluded.sla_hours, excluded.full_name, excluded.document_type,
                   excluded.document_number, excluded.date_of_birth, excluded.attempt, excluded.flags,
                   excluded.score_document, excluded.score_face, excluded.score_liveness, excluded.notes,
                   excluded.data_problems)
                is distinct from
                  (verification_case.status, verification_case.created_at, verification_case.sla_hours,
                   verification_case.full_name, verification_case.document_type, verification_case.document_number,
                   verification_case.date_of_birth, verification_case.attempt, verification_case.flags,
                   verification_case.score_document, verification_case.score_face, verification_case.score_liveness,
                   verification_case.notes, verification_case.data_problems)
            returning (xmax = 0) as inserted
            """,
        )
            .param("caseId", c.source.caseId)
            .param("status", c.source.status)
            .param("createdAt", c.createdAt?.let(Timestamp::from))
            .param("slaHours", c.source.slaHours)
            .param("fullName", c.source.applicant?.fullName)
            .param("documentType", c.source.applicant?.documentType)
            .param("documentNumber", c.source.applicant?.documentNumber)
            .param("dateOfBirth", c.dateOfBirth)
            .param("attempt", c.source.attempt)
            .param("flags", c.source.flags.toTypedArray())
            .param("scoreDocument", c.scoreDocument)
            .param("scoreFace", c.scoreFace)
            .param("scoreLiveness", c.scoreLiveness)
            .param("notes", c.source.notes)
            .param("dataProblems", c.dataProblems.toTypedArray())
            .query(Boolean::class.java)
            .optional()
        return when {
            inserted.isEmpty -> UpsertResult.UNCHANGED
            inserted.get() -> UpsertResult.INSERTED
            else -> UpsertResult.UPDATED
        }
    }

    fun findDetail(caseId: String): CaseDetail? =
        jdbc.sql(
            """
            select case_id, status, created_at, sla_hours, full_name, document_type, document_number, date_of_birth,
                   attempt, flags, score_document, score_face, score_liveness, notes, data_problems
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
                dataProblems = (rs.getArray("data_problems").array as Array<String>).toList(),
            )
        }.optional().orElse(null)

    fun setStatus(caseId: String, status: String) {
        jdbc.sql("update verification_case set status = :status, updated_at = now() where case_id = :caseId")
            .param("status", status).param("caseId", caseId).update()
    }

    fun findPending(): List<PendingCase> =
        jdbc.sql(
            """
            select v.case_id, v.document_type, v.flags, v.attempt, v.created_at, v.sla_hours, v.data_problems,
                   c.reviewer as claimed_by
            from verification_case v left join claim c on c.case_id = v.case_id
            where v.status = 'PENDING_REVIEW'
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
                dataProblems = (rs.getArray("data_problems").array as Array<String>).toList(),
                claimedBy = rs.getString("claimed_by"),
            )
        }.list()
}
