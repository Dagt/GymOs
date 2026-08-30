import org.gradle.api.tasks.bundling.Zip
import java.security.MessageDigest

plugins {
    java
    application
}

group = "com.gymfort"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.gymfort.connector.ConnectorMain"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = project.version
    }
}

val connectorJar = tasks.named<Jar>("jar")
val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val jpackageOutput = layout.buildDirectory.dir("jpackage/output")
val applicationImage = jpackageOutput.map { it.dir("GymFortConnector") }
val packageVersion = version.toString()
val javaLauncher = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(25)
}

val prepareJpackageInput by tasks.registering(Sync::class) {
    dependsOn(connectorJar)
    from(connectorJar)
    from(configurations.runtimeClasspath)
    into(jpackageInput)
}

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds a self-contained GymFort Connector application image."
    dependsOn(prepareJpackageInput)
    inputs.dir(jpackageInput)
    outputs.dir(applicationImage)

    doFirst {
        delete(jpackageOutput)
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val executableName = if (windows) "jpackage.exe" else "jpackage"
        val executable = javaLauncher.get().metadata.installationPath
            .file("bin/$executableName").asFile.absolutePath
        val arguments = mutableListOf(
            executable,
            "--type", "app-image",
            "--name", "GymFortConnector",
            "--app-version", packageVersion,
            "--vendor", "GymFort",
            "--description", "GymFort Local Physical Access Connector",
            "--input", jpackageInput.get().asFile.absolutePath,
            "--main-jar", connectorJar.get().archiveFileName.get(),
            "--main-class", application.mainClass.get(),
            "--dest", jpackageOutput.get().asFile.absolutePath,
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )
        if (windows) {
            arguments.add("--win-console")
        }
        commandLine(arguments)
    }
}

val pilotBundle by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Assembles the application image, installer and site runbook."
    dependsOn(jpackageImage)
    into(layout.buildDirectory.dir("pilot/GymFortConnector-pilot"))
    from(applicationImage) {
        into("app")
    }
    from("scripts") {
        into("scripts")
    }
    from("scripts/INICIAR-PRUEBA-LOCAL.cmd")
    from("connector.example.properties") {
        into("config")
    }
    from("config/hikcentral-profile.example.json") {
        into("config")
    }
    from("docs") {
        into("docs")
    }
    from("README.md")
}

val pilotZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Creates the self-contained pilot installation ZIP."
    dependsOn(pilotBundle)
    from(layout.buildDirectory.dir("pilot/GymFortConnector-pilot")) {
        into("GymFortConnector-pilot")
    }
    archiveFileName = "GymFortConnector-${packageVersion}-${System.getProperty("os.name").replace(" ", "-")}-${System.getProperty("os.arch")}.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
}

tasks.register("pilotChecksum") {
    group = "distribution"
    description = "Writes a SHA-256 checksum next to the pilot ZIP."
    dependsOn(pilotZip)
    val archive = pilotZip.flatMap { it.archiveFile }
    val checksum = archive.map { it.asFile.resolveSibling(it.asFile.name + ".sha256") }
    inputs.file(archive)
    outputs.file(checksum)
    doLast {
        val archiveFile = archive.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        archiveFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        checksum.get().writeText("$hex  ${archiveFile.name}\n")
    }
}
