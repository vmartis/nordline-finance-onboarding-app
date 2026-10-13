package finance.nordline.queue

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.HandlerInterceptor
import java.security.MessageDigest

/** One case as the legacy system pushes it. Only fields the workflow needs; the rest is ignored. */
data class IngestCase(
    @field:NotBlank @field:Pattern(regexp = "[A-Za-z0-9-]{1,40}")
    val caseId: String,
    @field:Size(max = 40)
    val createdAt: String? = null,
    @field:Valid
    val applicant: IngestApplicant? = null,
    @field:Valid
    val checks: IngestChecks? = null,
    val flags: List<@Pattern(regexp = "[A-Z_]{1,40}") String> = emptyList(),
    @field:Min(1) @field:Max(100)
    val attempt: Int = 1,
    @field:NotBlank @field:Pattern(regexp = "[A-Z_]{1,40}")
    val status: String,
    @field:Min(1) @field:Max(72)
    val slaHours: Int,
    @field:Size(max = 2000)
    val notes: String? = null,
)

data class IngestApplicant(
    @field:Size(max = 200) val fullName: String? = null,
    // Text, not LocalDate: a parse error would quote the raw birth date in the log. Checked in clean().
    @field:Size(max = 40) val dateOfBirth: String? = null,
    @field:Size(max = 40) val documentType: String? = null,
    @field:Size(max = 60) val documentNumber: String? = null,
)

/** Raw values: the export sometimes has text or out-of-range numbers here. clean() decides what is valid. */
data class IngestChecks(
    val documentAuthenticity: Any? = null,
    val faceMatch: Any? = null,
    val liveness: Any? = null,
)

/** accepted = new cases, updated = changed cases, unchanged = same as stored, problems = data-problem notes found. */
data class IngestSummary(val accepted: Int, val updated: Int, val unchanged: Int, val problems: Int)

@RestController
@RequestMapping("/api/ingest")
class IngestController(private val cases: CaseRepository) {

    @PostMapping("/cases")
    @Transactional
    fun ingest(@RequestBody @Size(min = 1, max = 5000) batch: List<@Valid IngestCase>): IngestSummary {
        val cleaned = clean(batch)
        val results = cleaned.map { cases.upsert(it) }
        return IngestSummary(
            accepted = results.count { it == UpsertResult.INSERTED },
            updated = results.count { it == UpsertResult.UPDATED },
            unchanged = results.count { it == UpsertResult.UNCHANGED },
            problems = cleaned.sumOf { it.dataProblems.size },
        )
    }
}

/** The legacy system authenticates with its own key in X-Api-Key. Constant-time compare. */
class IngestApiKeyInterceptor(private val apiKey: String) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val given = request.getHeader("X-Api-Key")
        val ok = apiKey.isNotEmpty() && given != null &&
            MessageDigest.isEqual(given.toByteArray(), apiKey.toByteArray())
        if (!ok) response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
        return ok
    }
}
