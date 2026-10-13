package finance.nordline.queue

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
        jdbc.update("delete from verification_case")
        jdbc.update(
            """insert into verification_case (case_id, status, created_at, sla_hours, full_name, document_type,
               document_number, date_of_birth, attempt, flags, score_document, score_face, score_liveness, notes)
               values ('VC-1', 'PENDING_REVIEW', '2026-10-14T07:00:00Z', 4, 'Test Person', 'ID_CARD',
               'X1', '1990-01-01', 1, '{LOW_FACE_MATCH}', 0.9, null, 0.8, '')""",
        )
    }

    private fun decide(body: String) = mockMvc.post("/api/cases/VC-1/decision") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

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
                // The legacy server reads exactly Content-Length bytes; a chunked body arrives as empty.
                .withHeader("Content-Length", matching("[1-9][0-9]*"))
                .withRequestBody(equalToJson(validDecision)),
        )
        assertThat(statusOf("VC-1")).isEqualTo("APPROVED")
        mockMvc.get("/api/queue").andExpect { jsonPath("$.length()") { value(0) } }
    }

    @Test
    fun `when the case api does not confirm, the case stays pending`() {
        caseApi.stubFor(post(urlEqualTo("/cases/VC-1/decision")).willReturn(aResponse().withStatus(503)))

        decide(validDecision).andExpect { status { isBadGateway() } }

        assertThat(statusOf("VC-1")).isEqualTo("PENDING_REVIEW")
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
