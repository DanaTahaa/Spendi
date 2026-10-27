plugins {
    application
    groovy
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
tasks.withType<JavaCompile>().configureEach { options.release = 21 }
application { mainClass = "com.example.spendi.backend.Main" }

val jooqVersion = "3.21.9"
val flywayVersion = "13.11.1"
val jooqCodegen = configurations.create("jooqCodegen")
dependencies {
    implementation(project(":core"))
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.postgresql:postgresql:42.7.14")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")
    implementation("com.nimbusds:nimbus-jose-jwt:10.10")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
    jooqCodegen("org.jooq:jooq-codegen:$jooqVersion")
    jooqCodegen("org.jooq:jooq-meta-extensions:$jooqVersion")
    testImplementation("org.spockframework:spock-core:2.4-groovy-4.0")
    testImplementation("io.zonky.test:embedded-postgres:2.1.1") {
        exclude(group = "io.zonky.test.postgres")
    }
    val osName = System.getProperty("os.name").lowercase()
    val postgresPlatform = when {
        osName.contains("win") -> "windows-amd64"
        osName.contains("mac") -> "darwin-amd64"
        else -> "linux-amd64"
    }
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-$postgresPlatform:17.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val generateJooq = tasks.register<JavaExec>("generateJooq") {
    group = "build"
    description = "Generate jOOQ types from the versioned migration, without a live database"
    classpath = jooqCodegen
    mainClass = "org.jooq.codegen.GenerationTool"
    args("src/main/jooq/codegen.xml")
    workingDir = projectDir
    inputs.files(fileTree("src/main/resources/db/migration"), file("src/main/jooq/codegen.xml"))
    outputs.dir(layout.buildDirectory.dir("generated-src/jooq"))
}
sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated-src/jooq")) }
tasks.compileJava { dependsOn(generateJooq) }

tasks.test {
    useJUnitPlatform { excludeTags("postgres") }
    maxHeapSize = "384m"
}
val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Run mandatory integration specifications against a real PostgreSQL database"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    maxHeapSize = "384m"
    shouldRunAfter(tasks.test)
    doFirst {
        check(!System.getenv("SPENDI_TEST_DB_URL").isNullOrBlank()) {
            "Set SPENDI_TEST_DB_URL, SPENDI_TEST_DB_USER and SPENDI_TEST_DB_PASSWORD for a disposable PostgreSQL database."
        }
    }
}

tasks.register<Test>("embeddedIntegrationTest") {
    description = "Run the PostgreSQL specifications using a temporary real PostgreSQL 17 server (x64)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("postgres") }
    systemProperty("spendi.test.embedded", "true")
    maxHeapSize = "384m"
    shouldRunAfter(tasks.test)
}
tasks.withType<GroovyCompile>().configureEach {
    groovyOptions.forkOptions.memoryMaximumSize = "384m"
}
