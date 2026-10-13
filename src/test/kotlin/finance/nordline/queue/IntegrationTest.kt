package finance.nordline.queue

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.postgresql.PostgreSQLContainer

/** Full app against a real Postgres and a fake Case API. Same settings everywhere, so all tests share one context. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        "app.ingest.api-key=test-ingest-key",
        "app.clock.now=2026-10-14T08:00:00Z",
        "app.case-api.key=test-case-api-key",
        "app.reference-dir=data",
        "app.auth.jwt-secret=test-jwt-secret-that-is-at-least-32-bytes-long",
    ],
)
@AutoConfigureMockMvc
@Import(TestDependencies::class)
annotation class IntegrationTest

@TestConfiguration(proxyBeanMethods = false)
class TestDependencies {
    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer("postgres:17-alpine")

    /** Stands in for the legacy Case API, which speaks plain HTTP/1.1 (no HTTP/2 upgrade). */
    @Bean(destroyMethod = "stop")
    fun caseApi() = WireMockServer(options().dynamicPort().http2PlainDisabled(true)).apply { start() }

    @Bean
    fun caseApiUrl(caseApi: WireMockServer) = DynamicPropertyRegistrar { it.add("app.case-api.url", caseApi::baseUrl) }
}
