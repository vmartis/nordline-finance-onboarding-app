package finance.nordline.queue

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@IntegrationTest
class CaseViewTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbc.update("delete from claim")
        jdbc.update("delete from decision_log")
        jdbc.update("delete from verification_case")
        fun case(id: String, doc: String?, attempt: Int, status: String, createdAt: String) = jdbc.update(
            """insert into verification_case (case_id, status, created_at, sla_hours, document_number, attempt, flags)
               values (?, ?, ?::timestamptz, 4, ?, ?, '{REPEAT_ATTEMPT}')""",
            id, status, createdAt, doc, attempt,
        )
        case("VC-1", "DOC-A1", 1, "REJECTED", "2026-10-14T05:00:00Z")
        case("VC-2", "DOC-A1", 2, "PENDING_REVIEW", "2026-10-14T06:00:00Z")
        case("VC-3", "DOC-A1", 3, "PENDING_REVIEW", "2026-10-14T07:00:00Z")
        case("VC-OTHER", "DOC-B2", 1, "PENDING_REVIEW", "2026-10-14T05:30:00Z")
        case("VC-NODOC", null, 1, "PENDING_REVIEW", "2026-10-14T05:45:00Z")
        case("VC-PH1", "UNKNOWN", 1, "PENDING_REVIEW", "2026-10-14T05:50:00Z")
        case("VC-PH2", "UNKNOWN", 1, "PENDING_REVIEW", "2026-10-14T05:51:00Z")
        case("VC-BLANK1", " ", 1, "PENDING_REVIEW", "2026-10-14T05:52:00Z")
        case("VC-BLANK2", " ", 1, "PENDING_REVIEW", "2026-10-14T05:53:00Z")
    }

    @Test
    fun `placeholder or blank document numbers never link strangers`() {
        mockMvc.get("/api/cases/VC-PH1").andExpect { jsonPath("$.otherAttempts.length()") { value(0) } }
        mockMvc.get("/api/cases/VC-BLANK1").andExpect { jsonPath("$.otherAttempts.length()") { value(0) } }
    }

    @Test
    fun `opened repeat attempt shows the other attempts with the same document, in attempt order`() {
        mockMvc.get("/api/cases/VC-2").andExpect {
            jsonPath("$.otherAttempts.length()") { value(2) }
            jsonPath("$.otherAttempts[0].caseId") { value("VC-1") }
            jsonPath("$.otherAttempts[0].attempt") { value(1) }
            jsonPath("$.otherAttempts[0].status") { value("REJECTED") }
            jsonPath("$.otherAttempts[1].caseId") { value("VC-3") }
            jsonPath("$.otherAttempts[0].documentNumber") { doesNotExist() }
        }
    }

    @Test
    fun `a case without a document number has no other attempts`() {
        mockMvc.get("/api/cases/VC-NODOC").andExpect { jsonPath("$.otherAttempts.length()") { value(0) } }
        mockMvc.get("/api/cases/VC-OTHER").andExpect { jsonPath("$.otherAttempts.length()") { value(0) } }
    }
}
