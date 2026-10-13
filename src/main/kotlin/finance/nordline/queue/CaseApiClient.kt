package finance.nordline.queue

import org.springframework.core.retry.RetryException
import org.springframework.core.retry.RetryPolicy
import org.springframework.core.retry.RetryTemplate
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

/** What we read back from the Case API: the case status and, once decided, who decided it. */
data class CaseApiCase(val caseId: String, val status: String, val decision: CaseApiDecision? = null)
data class CaseApiDecision(val reviewer: String? = null, val decision: String? = null, val reasonCode: String? = null)

@Component
class CaseApiClient(builder: RestClient.Builder, props: AppProperties) {

    private val client = builder
        .baseUrl(props.caseApi.url)
        .defaultHeader("X-Api-Key", props.caseApi.key)
        // The legacy server speaks plain HTTP/1.1 and is sometimes slow (~1.5 s); never wait forever.
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(5)) },
        )
        // The legacy server reads exactly Content-Length bytes and cannot read a chunked body.
        .bufferContent { _, _ -> true }
        .build()

    /** Retry only when the answer is "unavailable" or missing; the same idempotency key makes a retry safe. */
    private val retry = RetryTemplate(
        RetryPolicy.builder()
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            .multiplier(2.0)
            .includes(HttpServerErrorException.ServiceUnavailable::class.java, ResourceAccessException::class.java)
            .build(),
    )

    /** Throws the last RestClientException if the Case API does not confirm. */
    fun decide(caseId: String, decision: DecisionRequest, idempotencyKey: UUID): CaseApiCase =
        try {
            retry.execute {
                client.post()
                    .uri("/cases/{caseId}/decision", caseId)
                    .header("Idempotency-Key", idempotencyKey.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(decision)
                    .retrieve()
                    .body(CaseApiCase::class.java)
                    // An empty 200 tells us nothing; treat it like no answer and retry (safe with the same key).
                    ?: throw ResourceAccessException("Case API answered without a body")
            }
        } catch (e: RetryException) {
            throw e.cause as? RestClientException ?: e
        }

    fun case(caseId: String): CaseApiCase? =
        client.get().uri("/cases/{caseId}", caseId).retrieve().body(CaseApiCase::class.java)
}
