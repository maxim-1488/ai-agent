plugins {
    application
    checkstyle
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "ru.spb"
version = "1.0.0"

repositories {
    mavenCentral()
}

val vertxVersion = "5.1.5"
val jacksonVersion = "2.21.5"
val liquibaseVersion = "4.33.0"
val postgresqlVersion = "42.7.13"
val slf4jVersion = "2.0.18"
val logbackVersion = "1.6.0"
val junitVersion = "5.14.0"
val mockitoVersion = "5.12.0"
val assertjVersion = "3.27.7"
val testcontainersVersion = "2.0.5"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("ru.spb.aiagent.Main")
}

dependencies {
    implementation(platform("io.vertx:vertx-stack-depchain:$vertxVersion"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-pg-client")
    implementation("io.vertx:vertx-web-client")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.liquibase:liquibase-core:$liquibaseVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    constraints {
        implementation("com.ongres.scram:scram-client:3.4") {
            because("Fixes GHSA-p9jg-fcr6-3mhf pulled transitively by vertx-pg-client")
        }
        implementation("com.ongres.scram:scram-common:3.4") {
            because("Fixes GHSA-p9jg-fcr6-3mhf pulled transitively by vertx-pg-client")
        }
        implementation("org.apache.commons:commons-lang3:3.20.0") {
            because("Fixes GHSA-j288-q9x7-2f5v pulled transitively by Liquibase/OpenCSV")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

checkstyle {
    toolVersion = "10.17.0"
    configFile = file("config/checkstyle/checkstyle.xml")
}

tasks.withType<Checkstyle>().configureEach {
    enabled = false
    exclude("**/dto/**")
}

tasks.register("verifyRussianJavadoc") {
    group = "verification"
    description = "Checks that public backend types have Russian Javadoc."
    doLast {
        val missing = fileTree("src/main/java").matching { include("**/*.java") }.files.filter { file ->
            val text = file.readText(Charsets.UTF_8)
            Regex("(?m)^public\\s+(?:final\\s+)?(?:class|interface|enum|record)\\s+").containsMatchIn(text) &&
                !Regex("(?s)/\\*\\*.*?[А-Яа-я].*?\\*/\\s*public\\s+(?:final\\s+)?(?:class|interface|enum|record)\\s+").containsMatchIn(text)
        }
        if (missing.isNotEmpty()) {
            throw GradleException("Missing Russian Javadoc for public types: ${missing.joinToString { it.path }}")
        }
    }
}

tasks.check {
    dependsOn("verifyRussianJavadoc")
}
