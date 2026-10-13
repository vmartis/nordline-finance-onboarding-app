package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SkeletonTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")
    }

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
    fun `flyway creates the tables`() {
        val tables = jdbc.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String::class.java,
        )
        assertThat(tables).contains("reviewer", "reason_code", "flyway_schema_history")
    }
}
