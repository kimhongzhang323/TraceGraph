package io.tracegraph.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

@Controller
class UiIndexController {

    @GetMapping({"/tracegraph/ui", "/tracegraph/ui/"})
    RedirectView index() {
        return new RedirectView("/tracegraph/ui/index.html");
    }
}
