package finance.nordline.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class QueueOrderTest {

    private val now = Instant.parse("2026-10-14T08:00:00Z")

    private fun case(id: String, createdAt: String?, slaHours: Int) =
        PendingCase(id, "ID_CARD", emptyList(), 1, createdAt?.let(Instant::parse), slaHours)

    @Test
    fun `most urgent case comes first, passports use their own longer sla`() {
        val queue = order(
            listOf(
                case("passport-old", "2026-10-14T01:00:00Z", 8), // due 09:00 -> 60 min left
                case("id-new", "2026-10-14T07:30:00Z", 4),       // due 11:30 -> 210 min left
                case("id-overdue", "2026-10-14T03:30:00Z", 4),   // due 07:30 -> 30 min overdue
            ),
            now,
        )

        assertThat(queue.map { it.caseId }).containsExactly("id-overdue", "passport-old", "id-new")
        assertThat(queue.map { it.minutesLeft }).containsExactly(-30L, 60L, 210L)
        assertThat(queue[0].deadline).isEqualTo(Instant.parse("2026-10-14T07:30:00Z"))
    }

    @Test
    fun `case with unknown creation time is shown first, never hidden`() {
        val queue = order(
            listOf(case("known", "2026-10-14T03:30:00Z", 4), case("unknown", null, 4)),
            now,
        )

        assertThat(queue.map { it.caseId }).containsExactly("unknown", "known")
        assertThat(queue[0].deadline).isNull()
        assertThat(queue[0].minutesLeft).isNull()
    }

    @Test
    fun `equal deadlines keep a stable order by case id`() {
        val queue = order(
            listOf(case("B", "2026-10-14T06:00:00Z", 4), case("A", "2026-10-14T06:00:00Z", 4)),
            now,
        )

        assertThat(queue.map { it.caseId }).containsExactly("A", "B")
    }

    @Test
    fun `a case 30 seconds past its deadline counts as overdue`() {
        val queue = order(listOf(case("late", "2026-10-14T04:00:00Z", 4)), now.plusSeconds(30))

        assertThat(queue.single().minutesLeft).isEqualTo(-1L)
    }

    @Test
    fun `the simulated clock setting changes time left`() {
        fun minutesLeftAt(setting: String): Long? {
            val props = AppProperties(
                ingest = AppProperties.Ingest(),
                clock = AppProperties.ClockSettings(Instant.parse(setting)),
                caseApi = AppProperties.CaseApi("http://unused", ""),
                referenceDir = "data",
            )
            val clock = AppConfig(props).clock()
            return order(listOf(case("c", "2026-10-14T07:00:00Z", 4)), clock.instant()).single().minutesLeft
        }

        assertThat(minutesLeftAt("2026-10-14T08:00:00Z")).isEqualTo(180L)
        assertThat(minutesLeftAt("2026-10-14T10:30:00Z")).isEqualTo(30L)
    }
}
