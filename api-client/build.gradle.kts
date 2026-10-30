plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach { options.release = 17 }
dependencies {
    implementation(project(":core"))
    // Android supplies this API. Do not package a second org.json implementation in the APK.
    compileOnly("org.json:json:20260814")
    testImplementation("org.json:json:20260814")
    testImplementation(libs.junit)
}
