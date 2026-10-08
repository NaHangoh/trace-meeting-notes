import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

group = "tracenotes"
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
    // 버전은 Spring Boot BOM으로만 관리한다
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        // 실제 LLM이 필요한 테스트는 기본 실행에서 제외한다
        excludeTags("llm")
    }
    systemProperty("spring.profiles.active", "test")
    // 자동 테스트는 실제 LLM을 부르지 않는다. OS 환경 변수는 application-test.yml보다 우선하므로
    // 시스템 속성(환경 변수보다 우선)으로 fixture를 고정하고, LLM 관련 환경 변수는 테스트 JVM에 넘기지 않는다
    systemProperty("app.llm.provider", "fixture")
    listOf("LLM_API_KEY", "APP_LLM_PROVIDER", "APP_LLM_BASE_URL", "APP_LLM_MODEL").forEach { environment.remove(it) }
}

// 실제 LLM 실행기를 부르는 테스트(@Tag("llm"))만 실행한다. 사용자가 실행기를 띄운 뒤 직접 실행한다.
// 테스트 프로필을 쓰지 않으므로 실행 설정(application.yml + 루트 .env)으로 실행기에 연결한다.
tasks.register<Test>("llmTest") {
    description = "Runs @Tag(\"llm\") tests against a running local LLM server"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("llm")
    }
    outputs.upToDateWhen { false }
}
