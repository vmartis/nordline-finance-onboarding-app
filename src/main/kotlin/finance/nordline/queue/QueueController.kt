package finance.nordline.queue

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

@RestController
class QueueController(private val cases: CaseRepository, private val clock: Clock) {

    @GetMapping("/api/queue")
    fun queue(): List<QueueItem> = order(cases.findPending(), clock.instant())
}
