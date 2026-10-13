package finance.nordline.queue

import com.nimbusds.jose.jwk.source.ImmutableSecret
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val ISSUER = "morning-queue"

/**
 * Slows down password guessing: after 5 wrong passwords for a name, that name is locked for 15 minutes.
 * In memory, so it resets on restart; enough for one app instance on an internal network.
 */
@Component
class LoginThrottle {
    private data class Failures(val count: Int, val since: Instant)

    private val failures = ConcurrentHashMap<String, Failures>()
    private val window = Duration.ofMinutes(15)

    fun isLocked(username: String): Boolean {
        val f = failures[username.lowercase()] ?: return false
        if (f.since.plus(window).isBefore(Instant.now())) {
            failures.remove(username.lowercase())
            return false
        }
        return f.count >= 5
    }

    fun recordFailure(username: String) {
        failures.merge(username.lowercase(), Failures(1, Instant.now())) { old, _ ->
            if (old.since.plus(window).isBefore(Instant.now())) Failures(1, Instant.now()) else old.copy(count = old.count + 1)
        }
    }

    fun recordSuccess(username: String) {
        failures.remove(username.lowercase())
    }

    fun reset() = failures.clear()
}

/** Login with name + password; the API then needs a JWT bearer token. All parsing and checking is Spring Security's. */
@Configuration
class SecurityConfig(props: AppProperties) {

    private val key: SecretKey = props.auth.jwtSecret.toByteArray().let {
        require(it.size >= 32) { "app.auth.jwt-secret must be at least 32 bytes" }
        SecretKeySpec(it, "HmacSHA256")
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() } // bearer tokens in a header, no cookies: no CSRF risk
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/ingest/**").permitAll() // the legacy system uses its own API key
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll() // the page itself and its static files
            }
            .oauth2ResourceServer { it.jwt { } }
            .build()

    /** Accepts only our own tokens: our key and issuer, an expiry, and a user who still exists. */
    @Bean
    fun jwtDecoder(jdbc: JdbcClient): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    JwtClaimValidator<Instant>(JwtClaimNames.EXP) { it != null },
                    OAuth2TokenValidator { jwt ->
                        val exists = jdbc.sql("select count(*) from app_user where username = :u")
                            .param("u", jwt.subject ?: "").query(Int::class.java).single() > 0
                        if (exists) OAuth2TokenValidatorResult.success()
                        else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Unknown user", null))
                    },
                ),
            )
        }

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun userDetailsService(jdbc: JdbcClient) = UserDetailsService { username ->
        jdbc.sql("select password_hash from app_user where username = :username")
            .param("username", username).query(String::class.java).optional()
            .map { User.withUsername(username).password(it).roles("REVIEWER").build() }
            .orElseThrow { UsernameNotFoundException("unknown user") }
    }

    @Bean
    fun authenticationManager(users: UserDetailsService, encoder: PasswordEncoder): AuthenticationManager =
        ProviderManager(DaoAuthenticationProvider(users).apply { setPasswordEncoder(encoder) })
}

data class LoginRequest(
    @field:NotBlank @field:Size(max = 40) val username: String,
    @field:NotBlank @field:Size(max = 200) val password: String,
)

data class LoginResult(val token: String, val username: String, val expiresAt: Instant)

@RestController
class AuthController(
    private val authentication: AuthenticationManager,
    private val jwt: JwtEncoder,
    private val props: AppProperties,
    private val throttle: LoginThrottle,
) {

    @PostMapping("/api/auth/login")
    fun login(@RequestBody @Valid request: LoginRequest): LoginResult {
        if (throttle.isLocked(request.username)) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many wrong passwords for this name. Try again in 15 minutes.",
            )
        }
        val user = try {
            authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.username, request.password))
        } catch (_: AuthenticationException) {
            throttle.recordFailure(request.username)
            // Same answer for unknown user and wrong password, so names can't be probed.
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong name or password.")
        }
        throttle.recordSuccess(request.username)
        // Real time, not the simulated clock: token expiry must be checked against the server's clock.
        val now = Instant.now()
        val expiresAt = now.plus(Duration.ofHours(props.auth.tokenHours))
        val claims = JwtClaimsSet.builder()
            .issuer(ISSUER).subject(user.name).issuedAt(now).expiresAt(expiresAt).build()
        val token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        return LoginResult(token, user.name, expiresAt)
    }
}
