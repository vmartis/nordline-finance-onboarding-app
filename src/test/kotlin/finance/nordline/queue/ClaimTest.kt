package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@IntegrationTest
class ClaimTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var claims: ClaimService

    @BeforeEach
    fun clean() {
        jdbc.update("delete from claim")
        jdbc.update("delete from decision_log")
        jdbc.update("delete from verification_case")
    }

    private fun case(id: String, createdAt: String, status: String = "PENDING_REVIEW") = jdbc.update(
        """insert into verification_case (case_id, status, created_at, sla_hours, attempt)
           values (?, ?, ?::timestamptz, 4, 1)""",
        id, status, createdAt,
    )

    private fun takeNext(reviewer: String) = mockMvc.post("/api/claims/next") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"reviewer":"$reviewer"}"""
    }

    @Test
    fun `two reviewers pressing take next at the same moment never get the same case`() {
        (1..8).forEach { case("VC-$it", "2026-10-14T0$it:00:00Z") }
        val reviewers = (1..8).map { "reviewer-$it" }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(reviewers.size)

        val results = reviewers.map { r -> pool.submit<String?> { start.await(); claims.takeNext(r) } }
        start.countDown()
        val taken = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(taken).doesNotContainNull().doesNotHaveDuplicates().hasSize(8)
    }

    @Test
    fun `take next gives the most urgent free case and keeps giving me my own until I decide it`() {
        case("VC-LATER", "2026-10-14T07:00:00Z")
        case("VC-URGENT", "2026-10-14T05:00:00Z")
        case("VC-DONE", "2026-10-14T01:00:00Z", status = "APPROVED")

        takeNext("j.sebo").andExpect { jsonPath("$.caseId") { value("VC-URGENT") } }
        takeNext("t.weber").andExpect { jsonPath("$.caseId") { value("VC-LATER") } }
        takeNext("j.sebo").andExpect { jsonPath("$.caseId") { value("VC-URGENT") } }
        takeNext("a.farkas").andExpect { status { isNoContent() } }

        mockMvc.get("/api/queue").andExpect {
            jsonPath("$[0].caseId") { value("VC-URGENT") }
            jsonPath("$[0].claimedBy") { value("j.sebo") }
        }
    }

    @Test
    fun `after my case is decided, take next gives me a new one`() {
        case("VC-1", "2026-10-14T05:00:00Z")
        case("VC-2", "2026-10-14T06:00:00Z")
        takeNext("j.sebo").andExpect { jsonPath("$.caseId") { value("VC-1") } }

        jdbc.update("update verification_case set status = 'APPROVED' where case_id = 'VC-1'")

        takeNext("j.sebo").andExpect { jsonPath("$.caseId") { value("VC-2") } }
    }

    @Test
    fun `unknown reviewer cannot take a case`() {
        case("VC-1", "2026-10-14T05:00:00Z")
        takeNext("nobody").andExpect { status { isUnprocessableContent() } }
        takeNext("").andExpect { status { isBadRequest() } }
    }
}
