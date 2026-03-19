package io.vedra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class VedraApplication

fun main(args: Array<String>) {
    runApplication<VedraApplication>(*args)
}
