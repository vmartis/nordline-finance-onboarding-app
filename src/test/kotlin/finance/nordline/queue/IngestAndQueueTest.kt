package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@IntegrationTest
@WithMockUser(username = "j.sebo")
@ExtendWith(OutputCaptureExtension::class)
class IngestAndQueueTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun clean() {
        jdbc.update("delete from claim")
        jdbc.update("delete from decision_log")
        jdbc.update("delete from verification_case")
    }

    private fun caseJson(id: String, createdAt: String, sla: Int, status: String = "PENDING_REVIEW") = """
        {"caseId":"$id","createdAt":"$createdAt","channel":"web",
         "applicant":{"fullName":"Test Person","dateOfBirth":"1990-01-01","nationality":"SK",
                      "documentType":"${if (sla == 8) "PASSPORT" else "ID_CARD"}","documentNumber":"X1"},
         "checks":{"documentAuthenticity":0.9,"faceMatch":null,"liveness":0.8},
         "flags":["LOW_FACE_MATCH"],"attempt":1,"status":"$status","assignedTo":null,"slaHours":$sla,"notes":""}
    """

    private fun ingest(body: String, key: String? = "test-ingest-key") = mockMvc.post("/api/ingest/cases") {
        contentType = MediaType.APPLICATION_JSON
        content = body
        if (key != null) header("X-Api-Key", key)
    }

    @Test
    fun `ingest needs the api key`() {
        val body = "[${caseJson("VC-1", "2026-10-14T07:00:00Z", 4)}]"
        ingest(body, key = null).andExpect { status { isUnauthorized() } }
        ingest(body, key = "wrong").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `ingest is an upsert and reports accepted and updated`() {
        val body = "[${caseJson("VC-1", "2026-10-14T07:00:00Z", 4)},${caseJson("VC-2", "2026-10-14T06:00:00Z", 4)}]"

        ingest(body).andExpect {
            status { isOk() }
            jsonPath("$.accepted") { value(2) }
            jsonPath("$.updated") { value(0) }
        }
        ingest(body.replace("\"attempt\":1", "\"attempt\":2")).andExpect {
            jsonPath("$.accepted") { value(0) }
            jsonPath("$.updated") { value(2) }
        }
    }

    @Test
    fun `sending the same data again changes nothing`() {
        val body = "[${caseJson("VC-1", "14.10.2026 07:00", 4)},${caseJson("VC-2", "2026-10-14T06:00:00Z", 4)}]"
        ingest(body).andExpect { jsonPath("$.accepted") { value(2) } }
        val before = jdbc.queryForList("select case_id, updated_at from verification_case order by case_id")

        ingest(body).andExpect {
            jsonPath("$.accepted") { value(0) }
            jsonPath("$.updated") { value(0) }
            jsonPath("$.unchanged") { value(2) }
            jsonPath("$.problems") { value(3) } // zone-less date on VC-1 + face match missing on both
        }
        assertThat(jdbc.queryForList("select case_id, updated_at from verification_case order by case_id"))
            .isEqualTo(before)
    }

    @Test
    fun `data problems are visible in the queue and on the opened case`() {
        val body = "[${caseJson("VC-1", "2026-10-14T07:00:00Z", 4)},${caseJson("VC-1", "2026-10-14T07:00:00Z", 4)}]"
        ingest(body).andExpect {
            jsonPath("$.accepted") { value(1) }
            jsonPath("$.problems") { value(2) } // duplicate + face match missing without FACE_NOT_CAPTURED
        }

        mockMvc.get("/api/queue").andExpect {
            jsonPath("$[0].dataProblems.length()") { value(2) }
        }
        mockMvc.get("/api/cases/VC-1").andExpect {
            jsonPath("$.dataProblems[0]") { value("Listed 2 times in the export; the first record was kept.") }
        }
    }

    @Test
    fun `a pending push never reopens a decided case, but a decided push is taken`() {
        ingest("[${caseJson("VC-1", "2026-10-14T07:00:00Z", 4, status = "APPROVED")}]")
        ingest("[${caseJson("VC-1", "2026-10-14T07:00:00Z", 4)}]").andExpect { status { isOk() } }
        assertThat(statusOf("VC-1")).isEqualTo("APPROVED")

        ingest("[${caseJson("VC-2", "2026-10-14T07:00:00Z", 4)}]")
        ingest("[${caseJson("VC-2", "2026-10-14T07:00:00Z", 4, status = "REJECTED")}]")
        assertThat(statusOf("VC-2")).isEqualTo("REJECTED")
    }

    private fun statusOf(id: String) =
        jdbc.queryForObject("select status from verification_case where case_id = ?", String::class.java, id)

    @Test
    fun `rejected personal data never reaches the log`(output: CapturedOutput) {
        val badDob = caseJson("VC-1", "2026-10-14T07:00:00Z", 4).replace("1990-01-01", "PIIMARKER-DOB")
        val badNumber = caseJson("VC-2", "2026-10-14T07:00:00Z", 4).replace("\"X1\"", "{\"x\":\"PIIMARKER-DOC\"}")
        ingest("[$badDob]").andExpect { status { isOk() } } // S3: marked as a data problem, not rejected
        ingest("[$badNumber]").andExpect { status { isBadRequest() } }
        val impossibleDob = caseJson("VC-3", "2026-10-14T07:00:00Z", 4).replace("1990-01-01", "1990-02-31")
        ingest("[$impossibleDob]").andExpect { status { isOk() } }
        mockMvc.get("/api/cases/VC-1").andExpect { jsonPath("$.dataProblems") { value(org.hamcrest.Matchers.hasItem("Date of birth invalid in the export.")) } }

        assertThat(output.all).doesNotContain("PIIMARKER", "1990-02-31")
    }

    @Test
    fun `ingest rejects a case without id`() {
        ingest("""[{"caseId":"","status":"PENDING_REVIEW","slaHours":4,"attempt":1}]""")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `queue lists only pending cases, most urgent first, without personal data`() {
        ingest(
            "[${caseJson("VC-LATER", "2026-10-14T07:30:00Z", 4)}," +
                "${caseJson("VC-OVERDUE", "2026-10-14T03:00:00Z", 4)}," +
                "${caseJson("VC-DONE", "2026-10-14T01:00:00Z", 4, status = "APPROVED")}]",
        ).andExpect { status { isOk() } }

        mockMvc.get("/api/queue").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].caseId") { value("VC-OVERDUE") }
            jsonPath("$[0].minutesLeft") { value(-60) }
            jsonPath("$[1].caseId") { value("VC-LATER") }
            jsonPath("$[0].fullName") { doesNotExist() }
            jsonPath("$[0].documentNumber") { doesNotExist() }
        }
    }
}
