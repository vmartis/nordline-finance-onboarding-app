package finance.nordline.queue

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MorningQueueApplication

fun main(args: Array<String>) {
    runApplication<MorningQueueApplication>(*args)
}
