package com.app.controller

import com.app.security.config.WebProperties
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaController(
    private val webProperties: WebProperties,
) {

    @GetMapping("/")
    fun root(): String {
        val base = webProperties.spaBasePath.trimEnd('/')
        return "redirect:$base/"
    }
}
