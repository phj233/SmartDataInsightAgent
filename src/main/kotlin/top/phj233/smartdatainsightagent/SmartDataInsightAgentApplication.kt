package top.phj233.smartdatainsightagent

import org.babyfish.jimmer.client.EnableImplicitApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableImplicitApi
@SpringBootApplication
class SmartDataInsightAgentApplication

fun main(args: Array<String>) {
    runApplication<SmartDataInsightAgentApplication>(*args)
}
