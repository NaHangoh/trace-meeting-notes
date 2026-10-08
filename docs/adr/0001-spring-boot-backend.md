# 0001. 백엔드는 Spring Boot + Java
- 날짜: 2026-10-07
- 상태: 확정

## 배경
백엔드 언어와 프레임워크 선택.

## 선택지
- Spring Boot + Java: 목표로 하는 중견기업의 Java 수요와 맞음. 한글(HWP) 파일을 다루는 Java 라이브러리(hwplib, hwpxlib, Apache-2.0)가 있음. Spring AI로 여러 LLM을 같은 방식으로 연동 가능.
- NestJS + TypeScript: 프론트와 같은 언어라 빠르지만, 기존 업무에서 이미 쓰는 스택이라 새로 보여주는 것이 적음.

## 결정
Spring Boot 4.1 + Java 21 (Gradle 툴체인). Spring AI는 F2에서 Boot 4에 맞는 버전을 확인해 추가한다.

## 변경 (2026-10-07): Spring Boot 3 → 4
- 처음 결정은 Spring Boot 3이었다. 구현을 시작하며 아래 이유로 4로 바꿨다.
- 이유 1: Spring Boot 3.5의 오픈소스 지원이 2026-06-30에 끝났다. 구현 시작 시점(2026-10)에는 보안 패치를 받을 수 없다.
- 이유 2: Spring AI 2.0은 Spring Boot 4를 기반으로 한다. Boot 3에 머물면 F2에서 Spring AI 최신 버전을 쓸 수 없다.
- 출처
  - Spring Boot 공식 지원 일정 (3.5.x OSS 지원 종료 2026-06-30): https://spring.io/projects/spring-boot#support
  - Spring Boot 3.5 지원 종료 해설: https://www.herodevs.com/blog-posts/spring-boot-3-5-is-officially-end-of-life-here-is-what-that-means-for-teams-still-running-it
  - Spring AI 2.0 GA (Spring Boot 4.0/4.1 기반): https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/
- 시작 버전: Spring Boot 4.1.1 (Maven Central 기준 최신 정식 버전), Gradle 9.8.0 wrapper(배포본 sha256 검증).
- Java 21은 Gradle 툴체인으로 고정한다. 시스템 기본 JDK와 관계없이 빌드한다. 툴체인 자동 다운로드는 끈다.
- Spring Boot 4는 자동 설정이 모듈로 나뉘어 기술별 스타터(`spring-boot-starter-webmvc`, `spring-boot-starter-flyway` 등)와 테스트 스타터(`*-test`)를 쓴다.
- Spring AI는 Boot 4에 맞는 2.x를 F2에서 추가한다.

## 결과
- 얻는 것: Java 백엔드 역량, HWP 내보내기 경로
- 감수하는 것: 프론트와 언어가 달라 모델(DTO) 타입을 양쪽에서 관리

## 변경 (2026-10-08)
- HWP 내보내기를 범위 밖으로 변경(2026-10-08), HWP 라이브러리 선택 이유는 해당 없음
