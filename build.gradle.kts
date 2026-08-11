import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
  base
}

allprojects {
  group = "dev.jensakaa.dynamic-sprites"
  version = providers.gradleProperty("version").orElse("1.0.0-SNAPSHOT").get()

  repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
  }
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "maven-publish")

  extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
    withJavadocJar()
  }

  extensions.configure<PublishingExtension> {
    publications {
      create<MavenPublication>("mavenJava") {
        from(components["java"])
        artifactId = project.name

        pom {
          name.set("Dynamic Sprites ${project.name}")
          description.set(when (project.name) {
            "core" -> "Runtime image decoding, tiling, caching, and texture upload abstractions"
            "mineskin" -> "MineSkin V2 texture upload provider for Dynamic Sprites"
            "paper" -> "Paper and Adventure rendering integration for Dynamic Sprites"
            "resourcepack" -> "Shared shader constants for Dynamic Sprites resource packs"
            else -> "Dynamic Sprites module ${project.name}"
          })
          url.set("https://github.com/MakarME/dynamic-sprites")
          licenses {
            license {
              name.set("GNU General Public License v3.0 only")
              url.set("https://www.gnu.org/licenses/gpl-3.0.html")
              distribution.set("repo")
            }
          }
          scm {
            connection.set("scm:git:https://github.com/MakarME/dynamic-sprites.git")
            developerConnection.set("scm:git:ssh://git@github.com/MakarME/dynamic-sprites.git")
            url.set("https://github.com/MakarME/dynamic-sprites")
          }
        }
      }
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  dependencies {
    "testImplementation"("org.junit.jupiter:junit-jupiter:5.12.2")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.12.2")
  }
}
