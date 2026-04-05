import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow") version("9.0.1")
    id("com.adarshr.test-logger") version("4.0.0")
    id("java")
    id("org.sonarqube") version "6.2.0.5505"
}

sonar {
    properties {
        property("sonar.projectKey", "beanbeanjuice_SimpleProxyChat_bfdced85-f7af-4c3b-b826-007a5e968233")
        property("sonar.projectName", "AdvancedProxyChat")
    }
}

allprojects {
    group = "com.beanbeanjuice"
    val mockitoAgent by configurations.creating

    apply(plugin = "com.adarshr.test-logger")
    apply(plugin = "java")

    repositories {
        mavenCentral()

        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/content/groups/public/")
        }

        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }

        maven {
            name = "papermc-repo"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }

        maven {
            name = "networkmanager-repo"
            url = uri("https://repo.networkmanager.xyz/repository/maven-public/")
        }

        maven {
            name = "spicord-repo"
            url = uri("https://repo.spicord.org/")
        }

        maven {
            name = "advanced-ban requirement"
            url = uri("https://maven.elmakers.com/repository/")
        }

        maven {
            name = "spigotmc-repo"
            url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        }

        maven {
            name = "placeholder-api-repo"
            url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        }
    }

    dependencies {
        // Lombok
        compileOnly("org.projectlombok", "lombok", "1.18.38")
        annotationProcessor("org.projectlombok", "lombok", "1.18.38")

        // Unit Testing
        testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.13.4") // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
        testImplementation("org.junit.jupiter", "junit-jupiter", "5.13.4") // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // Mockito
        testImplementation("org.mockito", "mockito-core", "5.19.0") // https://mvnrepository.com/artifact/org.mockito/mockito-core
        testImplementation("org.mockito", "mockito-inline", "+") // https://mvnrepository.com/artifact/org.mockito/mockito-core

        mockitoAgent("org.mockito:mockito-core:5.19.0") {
            isTransitive = false
        }
    }

    tasks.withType<Test> {
        // Always re-run tests.
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }

        useJUnitPlatform()

        // Add the mockito agent as a javaagent JVM argument
        jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
    }
}

subprojects {
    apply(plugin = "com.gradleup.shadow")

    tasks.withType<ShadowJar> {
        minimize()
        archiveClassifier.set("")
        archiveBaseName.set(project.name)
        archiveVersion.set(provider { project.version as String })
        destinationDirectory.set(File(rootProject.projectDir, "libs"))

        doLast {
            println("Compiled: " + archiveFileName.get())
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        // Run tests on shadowJar
        dependsOn(tasks.shadowJar)
        classpath = files(tasks.shadowJar.get().archiveFile) + sourceSets.test.get().runtimeClasspath
    }
}

// DELETING FILES AFTER BUILD

tasks.clean {
    doLast {
        file("libs").deleteRecursively()
    }
}

tasks.register("cleanProxyJars") {
    doLast {
        // Collect current versions so we know what to keep.
        val keepNames = mutableSetOf<String>()
        rootProject.subprojects.forEach { sub ->
            val v = sub.version as? String ?: return@forEach
            if (v.isNotEmpty()) keepNames.add("${sub.name}-${v}.jar")
        }

        val filesToDelete = fileTree("libs") {
            include("**/*.jar")
        }.files.filter { jar ->
            // Delete internal aggregation jars and any versioned jar not matching current versions.
            val name = jar.name
            name.matches(Regex("(proxy|server|shared|projects|common)(-.+)?\\.jar")) ||
                (!keepNames.contains(name) && name.matches(Regex(".+-\\d+\\.\\d+\\.\\d+.*\\.jar")))
        }

        if (filesToDelete.isEmpty()) {
            println("No stale jars found.")
        } else {
            filesToDelete.forEach {
                println("Deleting stale jar: ${it.name}")
                it.delete()
            }
        }
    }
}

subprojects {
    tasks.named("shadowJar") {
        finalizedBy(rootProject.tasks.named("cleanProxyJars"))
    }
}
