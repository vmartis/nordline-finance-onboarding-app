package finance.nordline.queue

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Service
class ClaimService(private val jdbc: JdbcClient) {

    /**
     * My open case if I have one; otherwise the most urgent pending case nobody has taken (unknown deadlines first).
     * `for update skip locked` makes parallel callers pick different rows; the claim's primary key is the last guard.
     */
    @Transactional
    fun takeNext(reviewer: String): String? {
        myOpenCase(reviewer)?.let { return it }
        // A lost race (someone claimed the row between our snapshot and our lock) means one case fewer is free,
        // so this loop always ends; it returns null only when no free case is left.
        while (true) {
            val caseId = jdbc.sql(
                """
                select v.case_id from verification_case v
                where v.status = 'PENDING_REVIEW'
                  and not exists (select 1 from claim c where c.case_id = v.case_id)
                order by v.created_at + v.sla_hours * interval '1 hour' asc nulls first, v.case_id
                limit 1
                for update of v skip locked
                """,
            ).query(String::class.java).optional().orElse(null) ?: return null
            val inserted = jdbc.sql(
                "insert into claim (case_id, reviewer) values (:caseId, :reviewer) on conflict (case_id) do nothing",
            ).param("caseId", caseId).param("reviewer", reviewer).update()
            if (inserted == 1) return caseId
        }
    }

    private fun myOpenCase(reviewer: String): String? =
        jdbc.sql(
            """
            select c.case_id from claim c join verification_case v on v.case_id = c.case_id
            where c.reviewer = :reviewer and v.status = 'PENDING_REVIEW'
            order by c.claimed_at limit 1
            """,
        ).param("reviewer", reviewer).query(String::class.java).optional().orElse(null)
}

data class ClaimRequest(@field:NotBlank @field:Size(max = 40) val reviewer: String)
data class ClaimResult(val caseId: String)

@RestController
class ClaimController(private val claims: ClaimService, private val reference: ReferenceData) {

    /** 200 with the case to work on, or 204 when every pending case is taken. */
    @PostMapping("/api/claims/next")
    fun takeNext(@RequestBody @Valid request: ClaimRequest): ResponseEntity<ClaimResult> {
        if (!reference.isReviewer(request.reviewer)) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reviewer.")
        }
        val caseId = claims.takeNext(request.reviewer) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(ClaimResult(caseId))
    }
}
