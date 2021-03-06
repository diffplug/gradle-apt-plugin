plugins {
    `kotlin-dsl`
}
repositories {
    mavenCentral()
}
kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

val compileJava by project(":").tasks.existing(JavaCompile::class)

dependencies {
    implementation(files(compileJava.map { it.destinationDir }).builtBy(compileJava))
}
