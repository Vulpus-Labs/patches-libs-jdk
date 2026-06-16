// Root build script. Common configuration for every library module.
//
// Per ADR 0001 (Java 17 update): source is authored with a modern JDK
// (toolchain 21) but the deliverable is Java 17 bytecode (`--release 17`),
// because Voltage Modular's embedded runtime now supports the Java 17
// instruction set.

allprojects {
    group = "io.vulpuslabs.patches"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
        withSourcesJar()
    }

    // The deliverable is Java 17 bytecode. Enforce it on every compile task so
    // a stray Java-18+ API call fails the build rather than the host loader.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.compilerArgs.add("-Xlint:all")
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}
