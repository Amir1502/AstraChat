plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
val detekt by configurations.creating
dependencies {
    add("detekt", "io.gitlab.arturbosch.detekt:detekt-cli:${libs.versions.detekt.get()}:all")
}
tasks.register<JavaExec>("detektCheck") {
    group = "verification"
    description = "Run deterministic non-type-resolving Kotlin bug checks"
    classpath = detekt
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args("--input", "core/src,app/src", "--config", "config/detekt.yml", "--report", "xml:build/reports/detekt/report.xml")
    doFirst { file("build/reports/detekt").mkdirs() }
}
