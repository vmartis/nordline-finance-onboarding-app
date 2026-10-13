package finance.nordline.queue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ConfigurationProperties("app")
data class AppProperties(val ingest: Ingest, val clock: ClockSettings, val caseApi: CaseApi, val referenceDir: String) {
    /** The legacy back-office Case API. The key never leaves the server. */
    data class CaseApi(val url: String, val key: String)

    /** Empty key = ingest refuses every call. */
    data class Ingest(val apiKey: String = "")

    /** Simulated "now", because the export is dated tomorrow. */
    data class ClockSettings(val now: Instant)
}

@Configuration
@EnableConfigurationProperties(AppProperties::class)
class AppConfig(private val props: AppProperties) : WebMvcConfigurer {

    @Bean
    fun clock(): Clock = Clock.fixed(props.clock.now, ZoneOffset.UTC)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(IngestApiKeyInterceptor(props.ingest.apiKey)).addPathPatterns("/api/ingest/**")
    }
}
