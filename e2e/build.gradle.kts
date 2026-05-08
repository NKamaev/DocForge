plugins {
    java
}

group = "com.docforge"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Selenium WebDriver
    testImplementation("org.seleniumhq.selenium:selenium-java:4.27.0")
    testImplementation("org.seleniumhq.selenium:selenium-firefox-driver:4.27.0")

    // WebDriverManager — автоскачивание ChromeDriver / GeckoDriver
    testImplementation("io.github.bonigarcia:webdrivermanager:5.9.2")

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // AssertJ
    testImplementation("org.assertj:assertj-core:3.27.2")

    // HTTP-клиент для подготовки тестовых данных через API
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Таймаут на один тест — 2 минуты (браузерные тесты медленные)
    systemProperty("junit.jupiter.execution.timeout.default", "2m")

    // Передаём env-переменные в JVM тестов
    systemProperty("app.base-url", System.getenv("APP_BASE_URL") ?: "http://localhost:5173")
    systemProperty("app.api-url", System.getenv("APP_API_URL") ?: "http://localhost:8080")
    systemProperty("headless", System.getenv("HEADLESS") ?: "true")

    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
