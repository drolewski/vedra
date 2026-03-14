package io.vedra

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class VedraApplication

fun main(args: Array<String>) {
    runApplication<VedraApplication>(*args)
}
