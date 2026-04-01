package com.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WgControlPlaneApplication

fun main(args: Array<String>) {
    runApplication<WgControlPlaneApplication>(*args)
}
