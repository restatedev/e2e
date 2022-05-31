plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
  targetCompatibility = JavaVersion.VERSION_11.toString()
  sourceCompatibility = JavaVersion.VERSION_11.toString()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}