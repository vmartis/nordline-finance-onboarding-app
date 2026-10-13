package finance.nordline.queue

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
    /** Other cases with the same document number (earlier and later attempts), in attempt order. */
    val otherAttempts: List<OtherAttempt> = emptyList(),
)

/** Another attempt by the same applicant. No personal data: the document number is only used to find it. */
data class OtherAttempt(val caseId: String, val attempt: Int, val status: String, val createdAt: Instant?, val flags: List<String>)

enum class Decision { APPROVE, REJECT, REQUEST_RECAPTURE, ESCALATE }

/** Sent to the Case API. The reviewer is always the logged-in user; a name sent by the browser is ignored. */
data class DecisionRequest(
    @field:Size(max = 40) val reviewer: String = "",
    val decision: Decision,
    @field:NotBlank @field:Size(max = 40) val reasonCode: String,
    @field:NotBlank @field:Size(max = 500) val comment: String,
)

data class DecisionResult(val caseId: String, val status: String)

private const val CASE_ID = "[A-Za-z0-9-]{1,40}"
private const val UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

/** What the Case API may answer after a decision (see its docstring). */
private val DECIDED_STATUSES = setOf("APPROVED", "REJECTED", "RECAPTURE_REQUESTED", "ESCALATED")

@RestController
@RequestMapping("/api/cases")
class CaseController(
    private val cases: CaseRepository,
    private val reference: ReferenceData,
    private val caseApi: CaseApiClient,
    private val decisionLog: DecisionLog,
) {

    /** Holds personal data, so the browser must not keep a copy (shared reviewer PCs). */
    @GetMapping("/{caseId}")
    fun case(@PathVariable @Pattern(regexp = CASE_ID) caseId: String): ResponseEntity<CaseDetail> {
        val detail = cases.findDetail(caseId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.")
        val withAttempts = detail.copy(otherAttempts = cases.findOtherAttempts(caseId))
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(withAttempts)
    }

    /**
     * The Case API is the official record: our copy changes only after it confirms.
     * The browser sends one idempotency key per decision, so retries and double clicks never save twice.
     */
    @PostMapping("/{caseId}/decision")
    fun decide(
        @PathVariable @Pattern(regexp = CASE_ID) caseId: String,
        @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) idempotencyKey: String,
        @RequestBody @Valid body: DecisionRequest,
        user: Authentication,
    ): DecisionResult {
        val decision = body.copy(reviewer = user.name)
        cases.findDetail(caseId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found.")
        if (!reference.isReviewer(decision.reviewer)) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reviewer.")
        }
        if (!reference.isReasonCode(decision.reasonCode)) {
            throw ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown reason code.")
        }
        val key = UUID.fromString(idempotencyKey)
        val confirmed = try {
            caseApi.decide(caseId, decision, key)
        } catch (_: HttpClientErrorException.Conflict) {
            throw alreadyDecided(caseId)
        } catch (_: HttpClientErrorException.UnprocessableContent) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "The back office refused this decision or reason code. Nothing was saved; check the choices.",
            )
        } catch (_: RestClientException) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The back office did not confirm the decision. It is safe to press Save again: " +
                    "the same decision is sent with the same key and can never be saved twice.",
            )
        }
        // The legacy API answers a known key with that key's original case, whatever the URL says.
        // Only an answer for this case and this exact decision counts as confirmation.
        val d = confirmed.decision
        val matches = confirmed.caseId == caseId && d != null && d.reviewer == decision.reviewer &&
            d.decision == decision.decision.name && d.reasonCode == decision.reasonCode
        if (!matches || confirmed.status !in DECIDED_STATUSES) {
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The back office gave an unexpected answer. Check the case in the back office before deciding again.",
            )
        }
        decisionLog.recordConfirmed(caseId, decision, key, confirmed.status)
        return DecisionResult(caseId, confirmed.status)
    }

    /** 409: someone decided it elsewhere (e.g. in the old tool). Refresh our copy and say who. */
    private fun alreadyDecided(caseId: String): ResponseStatusException {
        val fresh = try { caseApi.case(caseId) } catch (_: RestClientException) { null }
        if (fresh != null && fresh.status in DECIDED_STATUSES) cases.setStatus(caseId, fresh.status)
        val who = fresh?.let { f -> " (${f.status}" + (f.decision?.reviewer?.let { " by $it" } ?: "") + ")" } ?: ""
        return ResponseStatusException(
            HttpStatus.CONFLICT,
            "This case was already decided in the back office$who. Nothing new was saved.",
        )
    }
}
