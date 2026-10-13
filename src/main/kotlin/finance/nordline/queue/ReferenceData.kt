package finance.nordline.queue

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.io.File

data class Reviewer(val id: String, val team: String, val shift: String)
data class ReasonCode(val code: String, val label: String)

@Component
class ReferenceData(private val jdbc: JdbcClient) {
    fun reviewers(): List<Reviewer> =
        jdbc.sql("select id, team, shift from reviewer order by id").query(Reviewer::class.java).list().filterNotNull()

    fun reasonCodes(): List<ReasonCode> =
        jdbc.sql("select code, label from reason_code order by code").query(ReasonCode::class.java).list().filterNotNull()

    fun isReviewer(id: String) =
        jdbc.sql("select count(*) from reviewer where id = :id").param("id", id).query(Int::class.java).single() > 0

    fun isReasonCode(code: String) =
        jdbc.sql("select count(*) from reason_code where code = :code").param("code", code).query(Int::class.java).single() > 0
}

/** Loads reviewers and compliance reason codes from their JSON files at startup (upsert, safe to repeat). */
@Component
class ReferenceDataLoader(
    private val props: AppProperties,
    private val jdbc: JdbcClient,
    private val mapper: JsonMapper,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val dir = File(props.referenceDir)
        mapper.readValue<List<Reviewer>>(File(dir, "reviewers.json")).forEach {
            jdbc.sql(
                """insert into reviewer (id, team, shift) values (:id, :team, :shift)
                   on conflict (id) do update set team = excluded.team, shift = excluded.shift""",
            ).param("id", it.id).param("team", it.team).param("shift", it.shift).update()
        }
        mapper.readValue<List<ReasonCode>>(File(dir, "reason_codes.json")).forEach {
            jdbc.sql(
                """insert into reason_code (code, label) values (:code, :label)
                   on conflict (code) do update set label = excluded.label""",
            ).param("code", it.code).param("label", it.label).update()
        }
    }
}

@RestController
class ReferenceController(private val reference: ReferenceData) {
    @GetMapping("/api/reviewers")
    fun reviewers() = reference.reviewers()

    @GetMapping("/api/reason-codes")
    fun reasonCodes() = reference.reasonCodes()
}
