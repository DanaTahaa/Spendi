plugins {
    `java-library`
}

// Keep the shared domain usable by Android (17) and the planned JVM API (21).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

dependencies {
    testImplementation(libs.junit)
}
