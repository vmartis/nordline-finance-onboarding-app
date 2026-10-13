package finance.nordline.queue

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@IntegrationTest
class DecisionTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var caseApi: WireMockServer

    @BeforeEach
    fun setUp() {
        caseApi.resetAll()
        jdbc.update("delete from decision_log")
        jdbc.update("delete from verification_case")
        jdbc.update(
            """insert into verification_case (case_id, status, created_at, sla_hours, full_name, document_type,
               document_number, date_of_birth, attempt, flags, score_document, score_face, score_liveness, notes)
               values ('VC-1', 'PENDING_REVIEW', '2026-10-14T07:00:00Z', 4, 'Test Person', 'ID_CARD',
               'X1', '1990-01-01', 1, '{LOW_FACE_MATCH}', 0.9, null, 0.8, '')""",
        )
    }

    private val key = "6f1c2a7e-3b4d-4e5f-8a9b-0c1d2e3f4a5b"

    private fun decide(body: String, idempotencyKey: String? = key) = mockMvc.post("/api/cases/VC-1/decision") {
        contentType = MediaType.APPLICATION_JSON
        content = body
        if (idempotencyKey != null) header("Idempotency-Key", idempotencyKey)
    }

    private fun logRows() = jdbc.queryForList("select case_id, reviewer, decision, reason_code, api_status from decision_log")

    /** Same shape as the real Case API reply: the whole case, with the decision inside (fictional values). */
    private val caseApiReply = """
        {"caseId":"VC-1","createdAt":"2026-10-14T07:00:00Z","channel":"web",
         "applicant":{"fullName":"Test Person","dateOfBirth":"1990-01-01","nationality":"SK",
                      "documentType":"ID_CARD","documentNumber":"X1"},
         "checks":{"documentAuthenticity":0.9,"faceMatch":null,"liveness":0.8},
         "flags":["LOW_FACE_MATCH"],"attempt":1,"status":"APPROVED","assignedTo":null,"slaHours":4,"notes":"",
         "decision":{"status":"APPROVED","decision":"APPROVE","reasonCode":"VERIFIED_MANUAL","reviewer":"j.sebo",
                     "comment":"Face matches on closer look","decidedAt":"2026-10-14T08:01:00Z"}}
    """

    private val validDecision =
        """{"reviewer":"j.sebo","decision":"APPROVE","reasonCode":"VERIFIED_MANUAL","comment":"Face matches on closer look"}"""

    @Test
    fun `decision goes to the case api first, then the case is decided here`() {
        caseApi.stubFor(
            post(urlEqualTo("/cases/VC-1/decision")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody(caseApiReply),
            ),
        )

        decide(validDecision).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("APPROVED") }
        }

        caseApi.verify(
            postRequestedFor(urlEqualTo("/cases/VC-1/decision"))
                .withHeader("X-Api-Key", equalTo("test-case-api-key"))
                .withHeader("Idempotency-Key", equalTo(key))
                // The legacy server reads exactly Content-Length bytes; a chunked body arrives as empty.
                .withHeader("Content-Length", matching("[1-9][0-9]*"))
                .withRequestBody(equalToJson(validDecision)),
        )
        assertThat(statusOf("VC-1")).isEqualTo("APPROVED")
        mockMvc.get("/api/queue").andExpect { jsonPath("$.length()") { value(0) } }
        assertThat(logRows()).containsExactly(
            mapOf("case_id" to "VC-1", "reviewer" to "j.sebo", "decision" to "APPROVE",
                "reason_code" to "VERIFIED_MANUAL", "api_status" to "APPROVED"),
        )
    }

    @Test
    fun `the same decision sent twice with the same key is logged once`() {
        caseApi.stubFor(post(urlEqualTo("/cases/VC-1/decision")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(caseApiReply),
        ))

        decide(validDecision).andExpect { status { isOk() } }
        decide(validDecision).andExpect { status { isOk() } }

        assertThat(logRows()).hasSize(1)
    }

    @Test
    fun `503 then ok gives exactly one decision, sent with the same idempotency key`() {
        val url = urlEqualTo("/cases/VC-1/decision")
        caseApi.stubFor(post(url).inScenario("flaky").whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"))
        caseApi.stubFor(post(url).inScenario("flaky").whenScenarioStateIs("recovered")
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(caseApiReply)))

        decide(validDecision).andExpect { status { isOk() } }

        caseApi.verify(2, postRequestedFor(url).withHeader("Idempotency-Key", equalTo(key)))
        assertThat(logRows()).hasSize(1)
        assertThat(statusOf("VC-1")).isEqualTo("APPROVED")
    }

    @Test
    fun `already decided elsewhere refreshes the case from the case api and says who decided`() {
        caseApi.stubFor(post(urlEqualTo("/cases/VC-1/decision"))
            .willReturn(aResponse().withStatus(409).withBody("""{"error":"case is ESCALATED"}""")))
        caseApi.stubFor(get(urlEqualTo("/cases/VC-1")).willReturn(
            aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(caseApiReply.replace("APPROVED", "ESCALATED").replace("\"reviewer\":\"j.sebo\"", "\"reviewer\":\"t.weber\"")),
        ))

        decide(validDecision).andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("This case was already decided in the back office (ESCALATED by t.weber). Nothing new was saved.") }
        }

        assertThat(statusOf("VC-1")).isEqualTo("ESCALATED")
        assertThat(logRows()).isEmpty()
    }

    @Test
    fun `case api refusing the decision (422) is shown and nothing is saved`() {
        caseApi.stubFor(post(urlEqualTo("/cases/VC-1/decision"))
            .willReturn(aResponse().withStatus(422).withBody("""{"error":"decision, reasonCode and reviewer are required"}""")))

        decide(validDecision).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.detail") { value("The back office refused this decision or reason code. Nothing was saved; check the choices.") }
        }

        assertThat(statusOf("VC-1")).isEqualTo("PENDING_REVIEW")
        assertThat(logRows()).isEmpty()
    }

    @Test
    fun `empty reason or comment and a missing idempotency key are rejected before calling the case api`() {
        decide(validDecision.replace("VERIFIED_MANUAL", "")).andExpect { status { isBadRequest() } }
        decide(validDecision.replace("Face matches on closer look", "  ")).andExpect { status { isBadRequest() } }
        decide(validDecision, idempotencyKey = null).andExpect { status { isBadRequest() } }
        decide(validDecision, idempotencyKey = "not-a-uuid").andExpect { status { isBadRequest() } }

        assertThat(caseApi.allServeEvents).isEmpty()
    }

    @Test
    fun `when the case api does not confirm, the case stays pending`() {
        caseApi.stubFor(post(urlEqualTo("/cases/VC-1/decision")).willReturn(aResponse().withStatus(503)))

        decide(validDecision).andExpect { status { isBadGateway() } }

        caseApi.verify(3, postRequestedFor(urlEqualTo("/cases/VC-1/decision")).withHeader("Idempotency-Key", equalTo(key)))
        assertThat(statusOf("VC-1")).isEqualTo("PENDING_REVIEW")
        assertThat(logRows()).isEmpty()
    }

    @Test
    fun `an answer for another case or another decision (replayed key) changes nothing here`() {
        val url = urlEqualTo("/cases/VC-1/decision")
        for (reply in listOf(
            caseApiReply.replace("\"caseId\":\"VC-1\"", "\"caseId\":\"VC-OTHER\""),
            caseApiReply.replace("\"reviewer\":\"j.sebo\"", "\"reviewer\":\"t.weber\""),
        )) {
            caseApi.resetAll()
            caseApi.stubFor(post(url).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(reply),
            ))

            decide(validDecision).andExpect { status { isBadGateway() } }

            assertThat(statusOf("VC-1")).isEqualTo("PENDING_REVIEW")
            assertThat(logRows()).isEmpty()
        }
    }

    @Test
    fun `an unexpected status from the case api is not stored`() {
        caseApi.stubFor(
            post(urlEqualTo("/cases/VC-1/decision")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                    .withBody(caseApiReply.replace("\"status\":\"APPROVED\",\"assignedTo\"", "\"status\":\"WEIRD\",\"assignedTo\"")),
            ),
        )

        decide(validDecision).andExpect { status { isBadGateway() } }

        assertThat(statusOf("VC-1")).isEqualTo("PENDING_REVIEW")
    }

    @Test
    fun `unknown reason code or reviewer is refused before calling the case api`() {
        decide(validDecision.replace("VERIFIED_MANUAL", "MADE_UP")).andExpect { status { isUnprocessableContent() } }
        decide(validDecision.replace("j.sebo", "nobody")).andExpect { status { isUnprocessableContent() } }
        decide(validDecision.replace("APPROVE", "MAYBE")).andExpect { status { isBadRequest() } }

        assertThat(caseApi.allServeEvents).isEmpty()
    }

    @Test
    fun `opened case shows scores and applicant, missing score stays missing`() {
        mockMvc.get("/api/cases/VC-1").andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
            jsonPath("$.caseId") { value("VC-1") }
            jsonPath("$.fullName") { value("Test Person") }
            jsonPath("$.scores.documentAuthenticity") { value(0.9) }
            jsonPath("$.scores.faceMatch") { value(null as Any?) }
            jsonPath("$.flags[0]") { value("LOW_FACE_MATCH") }
        }
        mockMvc.get("/api/cases/VC-404").andExpect { status { isNotFound() } }
    }

    @Test
    fun `reviewers and reason codes come from the reference files`() {
        mockMvc.get("/api/reviewers").andExpect { jsonPath("$[?(@.id == 'j.sebo')].shift") { value("06-14") } }
        mockMvc.get("/api/reason-codes").andExpect { jsonPath("$[?(@.code == 'VERIFIED_MANUAL')]") { exists() } }
    }

    private fun statusOf(id: String) =
        jdbc.queryForObject("select status from verification_case where case_id = ?", String::class.java, id)
}
