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
import java.time.Instant
import java.time.format.DateTimeParseException

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
    // Text, not LocalDate: a parse error would quote the raw birth date in the log.
    @field:Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])")
    val dateOfBirth: String? = null,
    @field:Size(max = 40) val documentType: String? = null,
    @field:Size(max = 60) val documentNumber: String? = null,
)

data class IngestChecks(
    val documentAuthenticity: Double? = null,
    val faceMatch: Double? = null,
    val liveness: Double? = null,
)

data class IngestSummary(val accepted: Int, val updated: Int)

@RestController
@RequestMapping("/api/ingest")
class IngestController(private val cases: CaseRepository) {

    @PostMapping("/cases")
    @Transactional
    fun ingest(@RequestBody @Size(min = 1, max = 5000) batch: List<@Valid IngestCase>): IngestSummary {
        var accepted = 0
        var updated = 0
        for (c in batch) {
            if (cases.upsert(c, parseCreatedAt(c.createdAt))) accepted++ else updated++
        }
        return IngestSummary(accepted, updated)
    }

    // Unparseable times are stored as unknown; S3 adds the other export formats and a visible data-problem note.
    private fun parseCreatedAt(raw: String?): Instant? =
        try { raw?.let(Instant::parse) } catch (_: DateTimeParseException) { null }
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
