package finance.nordline.queue

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** Browser routes of the React app (one or two path parts, no file extension, not under /api) get index.html; React Router takes over. */
@Controller
class SpaController {
    @GetMapping("/{first:^(?!api$)[^.]*}", "/{first:^(?!api$)[^.]*}/{second:[^.]*}")
    fun app() = "forward:/index.html"
}
