package finance.nordline.queue

import java.time.Duration
import java.time.Instant

/** A pending case as the queue needs it: no name, document number or birth date. */
data class PendingCase(
    val caseId: String,
    val documentType: String?,
    val flags: List<String>,
    val attempt: Int,
    val createdAt: Instant?,
    val slaHours: Int,
    val dataProblems: List<String> = emptyList(),
    val claimedBy: String? = null,
)

data class QueueItem(
    val caseId: String,
    val documentType: String?,
    val flags: List<String>,
    val attempt: Int,
    val deadline: Instant?,
    /** Negative = overdue. Null = deadline unknown (bad creation time in the export). */
    val minutesLeft: Long?,
    val dataProblems: List<String>,
    /** Reviewer who took the case with "Take next", if anyone. */
    val claimedBy: String?,
)

/** Most urgent first. Unknown deadlines go to the top so nobody overlooks them. */
fun order(cases: List<PendingCase>, now: Instant): List<QueueItem> =
    cases.map { c ->
        val deadline = c.createdAt?.plus(Duration.ofHours(c.slaHours.toLong()))
        QueueItem(
            caseId = c.caseId,
            documentType = c.documentType,
            flags = c.flags,
            attempt = c.attempt,
            deadline = deadline,
            // Round down: 30 seconds past the deadline is already overdue (-1), not "0 min left".
            minutesLeft = deadline?.let { Math.floorDiv(Duration.between(now, it).seconds, 60L) },
            dataProblems = c.dataProblems,
            claimedBy = c.claimedBy,
        )
    }.sortedWith(compareBy<QueueItem, Instant?>(nullsFirst()) { it.deadline }.thenBy { it.caseId })
