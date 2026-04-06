plugins {
    kotlin("jvm") version "2.4.0-Beta1"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "mc506lw"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://repo.xenondevs.xyz/releases") {
        name = "InvUI"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.github.pylonmc:rebar:0.36.2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.shadowJar {
    exclude("kotlin/**")
    exclude("org/jetbrains/**")
    exclude("META-INF/kotlin/**")
    exclude("META-INF/services/kotlin.*")
}

tasks.register<Copy>("copyToServer") {
    from(tasks.shadowJar)
    into("D:\\我的世界资源库\\服务器\\岚域3.0\\plugins")
    outputs.upToDateWhen { false }
}

tasks.build {
    dependsOn("shadowJar")
    finalizedBy("copyToServer")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
