package finance.nordline.queue

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.postgresql.PostgreSQLContainer

/** Full app against a real Postgres in a container. Same settings everywhere, so all tests share one context. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(properties = ["app.ingest.api-key=test-ingest-key", "app.clock.now=2026-10-14T08:00:00Z"])
@AutoConfigureMockMvc
@Import(TestDatabase::class)
annotation class IntegrationTest

@TestConfiguration(proxyBeanMethods = false)
class TestDatabase {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:17-alpine")
}
