plugins {
  `java-library`
}

dependencies {
  api(project(":core"))
  implementation("com.google.code.gson:gson:2.13.2")
}
