package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@IntegrationTest
class SkeletonTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `home page is served`() {
        mockMvc.get("/").andExpect {
            status { isOk() }
            forwardedUrl("index.html")
        }
        mockMvc.get("/index.html").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Morning Queue")) }
        }
    }

    @Test
    fun `browser routes fall back to the single-page app, api paths do not`() {
        mockMvc.get("/cases/VC-1").andExpect { forwardedUrl("/index.html") }
        mockMvc.get("/some-page").andExpect { forwardedUrl("/index.html") }
        mockMvc.get("/api/does-not-exist").andExpect { status { isNotFound() } }
        mockMvc.get("/assets/missing.js").andExpect { status { isNotFound() } }
    }

    @Test
    fun `flyway creates the tables`() {
        val tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String::class.java,
        )
        assertThat(tables).contains("reviewer", "reason_code", "flyway_schema_history")
    }
}
