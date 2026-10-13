package finance.nordline.queue

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** What we read back from the Case API after a decision. */
data class CaseApiCase(val caseId: String, val status: String)

@Component
class CaseApiClient(builder: RestClient.Builder, props: AppProperties) {

    private val client = builder
        .baseUrl(props.caseApi.url)
        .defaultHeader("X-Api-Key", props.caseApi.key)
        // The legacy server reads exactly Content-Length bytes and cannot read a chunked body.
        .bufferContent { _, _ -> true }
        .build()

    /** Throws a RestClientException if the Case API does not confirm. */
    fun decide(caseId: String, decision: DecisionRequest): CaseApiCase =
        client.post()
            .uri("/cases/{caseId}/decision", caseId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(decision)
            .retrieve()
            .body(CaseApiCase::class.java)
            ?: throw IllegalStateException("Case API answered without a body")
}
