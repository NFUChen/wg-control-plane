package com.app.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

// Forwards SPA routes to index.html. Static files and /api REST handlers take precedence.
@Controller
class SpaController {

    @GetMapping("/")
    fun root(): String = "forward:/index.html"

    @GetMapping("/servers", "/servers/**")
    fun servers(): String = "forward:/index.html"
}
