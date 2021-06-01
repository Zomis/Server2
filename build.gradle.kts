import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("multiplatform") version "1.4.32"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "net.zomis"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io") // KLogging
}

val jacksonVersion = "2.12.3"
val jupiterVersion = "5.7.1"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
            }
        }
        withJava()
    }
    js {
        browser {

        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("games-core/src/main/kotlin")
            resources.srcDir("games-core/src/main/resources")
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3")
//                implementation("com.github.lewik.klog:klog-metadata:2.0.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            kotlin.srcDir("games-server/src/main/kotlin")
            resources.srcDir("games-server/src/main/resources")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.4.3")
                implementation("com.github.lewik.klog:klog-jvm:2.0.2")
                implementation(kotlin("reflect"))

                // For Kotlin scripting inside a shadow jar (some of these dependencies may be removed)
                implementation(kotlin("compiler-embeddable"))
                implementation(kotlin("scripting-compiler-embeddable"))
                implementation(kotlin("scripting-compiler-impl-embeddable"))
                implementation(kotlin("scripting-jsr223"))
                implementation(kotlin("scripting-common"))
                implementation(kotlin("script-runtime"))
                implementation(kotlin("script-util"))

                implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
                implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
                implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

                implementation("com.beust:jcommander:1.81")
                implementation("org.java-websocket:Java-WebSocket:1.5.2")
                implementation("log4j:log4j:1.2.17")
                implementation("org.slf4j:slf4j-log4j12:1.7.30")
                implementation("io.javalin:javalin:3.13.7")
                implementation("com.github.kittinunf.fuel:fuel:2.3.1")
                implementation("com.amazonaws:aws-java-sdk-dynamodb:1.11.1000")
                implementation("org.postgresql:postgresql:42.2.19")
                implementation("com.zaxxer:HikariCP:4.0.3")
                implementation("com.github.ben-manes.caffeine:caffeine:3.0.2")

                implementation("ai.djl:api:0.3.0")
                implementation("ai.djl:model-zoo:0.3.0")
                runtimeOnly("ai.djl.mxnet:mxnet-engine:0.3.0")
                runtimeOnly("ai.djl.mxnet:mxnet-native-auto:1.6.0")
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("games-server/src/test/kotlin")
            resources.srcDir("games-server/src/test/resources")
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
                implementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.4.3")
            }
        }
        val jsMain by getting {
            kotlin.srcDir("games-js/src/main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.4.3")
                implementation("com.github.lewik.klog:klog-js:2.0.2")
            }
        }
    }

}

tasks {
    val shadowCreate by creating(ShadowJar::class) {
        manifest {
            attributes["Main-Class"] = "net.zomis.games.server2.Main"
        }
        archiveClassifier.set("all")
        from(kotlin.jvm().compilations.getByName("main").output)
        configurations =
            mutableListOf(kotlin.jvm().compilations.getByName("main").compileDependencyFiles as Configuration)
    }
    val build by existing {
        dependsOn(shadowCreate)
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<Delete> {
        delete("/documentation/INVITES.md")
        delete("/documentation/LOGIN_AND_LOBBY.md")
    }

}
