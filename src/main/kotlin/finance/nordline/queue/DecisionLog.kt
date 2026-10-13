package finance.nordline.queue

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class DecisionLog(private val jdbc: JdbcClient, private val cases: CaseRepository) {

    /** Called only after the Case API confirmed. A repeated key (re-sent decision) is logged once. */
    @Transactional
    fun recordConfirmed(caseId: String, decision: DecisionRequest, idempotencyKey: UUID, apiStatus: String) {
        cases.setStatus(caseId, apiStatus)
        jdbc.sql(
            """
            insert into decision_log (case_id, reviewer, decision, reason_code, idempotency_key, api_status)
            values (:caseId, :reviewer, :decision, :reasonCode, :key, :apiStatus)
            on conflict (idempotency_key) do nothing
            """,
        )
            .param("caseId", caseId)
            .param("reviewer", decision.reviewer)
            .param("decision", decision.decision.name)
            .param("reasonCode", decision.reasonCode)
            .param("key", idempotencyKey)
            .param("apiStatus", apiStatus)
            .update()
    }
}
