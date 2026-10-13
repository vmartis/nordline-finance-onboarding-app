package finance.nordline.queue

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

data class Scores(val documentAuthenticity: Double?, val faceMatch: Double?, val liveness: Double?)

/** The opened case. The only place that shows name, document number and birth date. */
data class CaseDetail(
    val caseId: String,
    val status: String,
    val createdAt: Instant?,
    val slaHours: Int,
    val fullName: String?,
    val documentType: String?,
    val documentNumber: String?,
    val dateOfBirth: LocalDate?,
    val attempt: Int,
    val flags: List<String>,
    val scores: Scores,
    val notes: String?,
    val dataProblems: List<String>,
)

enum class Decision { APPROVE, REJECT, REQUEST_RECAPTURE, ESCALATE }

/** Sent to the Case API as is. */
data class DecisionRequest(
    @field:NotBlank @field:Size(max = 40) val reviewer: String,
    val decision: Decision,
    @field:NotBlank @field:Size(max = 40) val reasonCode: String,
    @field:NotBlank @field:Size(max = 500) val comment: String,
)

data class DecisionResult(val caseId: String, val status: String)

private const val CASE_ID = "[A-Za-z0-9-]{1,40}"

/** What the Case API may answer after a decision (see its docstring). */
private val DECIDED_STATUSES = setOf("APPROVED", "REJECTED", "RECAPTURE_REQUESTED", "ESCALATED")

@RestController
@RequestMapping("/api/cases")
class CaseController(
    private val cases: CaseRepository,
    private val reference: ReferenceData,
    private val caseApi: CaseApiClient,
) {

    /** Holds personal data, so the browser must not keep a copy (shared reviewer PCs). */
    @GetMapping("/{caseId}")
    fun case(@PathVariable @Pattern(regexp = CASE_ID) caseId: String): ResponseEntity<CaseDetail> {
        val detail = cases.findDetail(caseId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.")
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail)
    }

    /** The Case API is the official record: our copy changes only after it confirms. */
    @PostMapping("/{caseId}/decision")
    fun decide(
        @PathVariable @Pattern(regexp = CASE_ID) caseId: String,
        @RequestBody @Valid decision: DecisionRequest,
    ): DecisionResult {
        cases.findDetail(caseId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.")
        if (!reference.isReviewer(decision.reviewer)) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reviewer.")
        }
        if (!reference.isReasonCode(decision.reasonCode)) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reason code.")
        }
        val confirmed = try {
            caseApi.decide(caseId, decision)
        } catch (_: RestClientException) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The back office did not confirm the decision, so nothing was saved. Try again.",
            )
        }
        if (confirmed.status !in DECIDED_STATUSES) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The back office gave an unexpected answer. Check the case in the back office before deciding again.",
            )
        }
        cases.setStatus(caseId, confirmed.status)
        return DecisionResult(caseId, confirmed.status)
    }
}
