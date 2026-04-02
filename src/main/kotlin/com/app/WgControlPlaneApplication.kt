package com.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableConfigurationProperties
@ConfigurationPropertiesScan
@EnableAsync
class WgControlPlaneApplication

fun main(args: Array<String>) {
    runApplication<WgControlPlaneApplication>(*args)
}
