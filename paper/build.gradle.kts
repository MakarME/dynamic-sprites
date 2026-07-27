plugins {
  `java-library`
}

dependencies {
  api(project(":core"))
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  compileOnly("net.skinsrestorer:skinsrestorer-api:15.9.0")
}
