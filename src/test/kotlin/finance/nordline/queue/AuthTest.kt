package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper

@IntegrationTest
class AuthTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: JsonMapper

    @BeforeEach
    fun users() {
        throttle.reset()
        jdbc.update("delete from app_user")
        // Same as scripts/add-users.sh: bcrypt hash made by Postgres (pgcrypto).
        jdbc.update("insert into app_user (username, password_hash) values ('j.sebo', crypt('12345', gen_salt('bf', 10)))")
    }

    private fun login(username: String, password: String) = mockMvc.post("/api/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"username":"$username","password":"$password"}"""
    }

    @Test
    fun `the api needs a login, the page and the ingest do not`() {
        mockMvc.get("/api/queue").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/cases/VC-1").andExpect { status { isUnauthorized() } }
        mockMvc.get("/").andExpect { status { isOk() } }
        mockMvc.post("/api/ingest/cases") {
            contentType = MediaType.APPLICATION_JSON
            content = "[]"
            header("X-Api-Key", "test-ingest-key")
        }.andExpect { status { isBadRequest() } } // reached the endpoint (empty batch), not blocked by login
    }

    @Test
    fun `correct password gives a token that opens the api`() {
        val body = login("j.sebo", "12345").andExpect {
            status { isOk() }
            jsonPath("$.username") { value("j.sebo") }
        }.andReturn().response.contentAsString
        val token = mapper.readTree(body).get("token").asString()

        mockMvc.get("/api/queue") { header("Authorization", "Bearer $token") }.andExpect { status { isOk() } }
        mockMvc.get("/api/queue") { header("Authorization", "Bearer ${token}x") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `wrong password or unknown user gives the same plain answer`() {
        login("j.sebo", "wrong").andExpect {
            status { isUnauthorized() }
            jsonPath("$.detail") { value("Wrong name or password.") }
        }
        login("nobody", "12345").andExpect {
            status { isUnauthorized() }
            jsonPath("$.detail") { value("Wrong name or password.") }
        }
    }

    @Test
    fun `with a real token, take next is made as the user in the token`() {
        jdbc.update("delete from claim")
        jdbc.update("delete from decision_log")
        jdbc.update("delete from verification_case")
        jdbc.update(
            """insert into verification_case (case_id, status, created_at, sla_hours, attempt)
               values ('VC-1', 'PENDING_REVIEW', '2026-10-14T05:00:00Z', 4, 1)""",
        )
        val body = login("j.sebo", "12345").andReturn().response.contentAsString
        val token = mapper.readTree(body).get("token").asString()

        mockMvc.post("/api/claims/next") { header("Authorization", "Bearer $token") }
            .andExpect { jsonPath("$.caseId") { value("VC-1") } }

        assertThat(jdbc.queryForObject("select reviewer from claim where case_id = 'VC-1'", String::class.java))
            .isEqualTo("j.sebo")
    }

    @Autowired lateinit var jwtEncoder: JwtEncoder
    @Autowired lateinit var throttle: LoginThrottle

    private fun signed(claims: JwtClaimsSet.Builder) =
        jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).tokenValue

    @Test
    fun `tokens without expiry, from another issuer or for an unknown user are refused`() {
        val now = Instant.now()
        val noExpiry = signed(JwtClaimsSet.builder().issuer("morning-queue").subject("j.sebo").issuedAt(now))
        val otherIssuer = signed(JwtClaimsSet.builder().issuer("someone-else").subject("j.sebo").issuedAt(now).expiresAt(now.plusSeconds(600)))
        val unknownUser = signed(JwtClaimsSet.builder().issuer("morning-queue").subject("zzz-nobody").issuedAt(now).expiresAt(now.plusSeconds(600)))
        val good = signed(JwtClaimsSet.builder().issuer("morning-queue").subject("j.sebo").issuedAt(now).expiresAt(now.plusSeconds(600)))

        for (token in listOf(noExpiry, otherIssuer, unknownUser)) {
            mockMvc.get("/api/queue") { header("Authorization", "Bearer $token") }.andExpect { status { isUnauthorized() } }
        }
        mockMvc.get("/api/queue") { header("Authorization", "Bearer $good") }.andExpect { status { isOk() } }
    }

    @Test
    fun `after 5 wrong passwords the name is locked for a while, even with the right password`() {
        throttle.reset()
        repeat(5) { login("j.sebo", "wrong").andExpect { status { isUnauthorized() } } }

        login("j.sebo", "12345").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.detail") { value("Too many wrong passwords for this name. Try again in 15 minutes.") }
        }
        throttle.reset()
        login("j.sebo", "12345").andExpect { status { isOk() } }
    }
}
