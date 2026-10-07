# 0005. 1차 DB는 H2 파일 모드 + Flyway
- 날짜: 2026-10-07
- 상태: 확정

## 배경
작업, 발언, 결과를 보관 기간(기본 24시간) 동안 저장할 DB가 필요하다. 1차는 로그인과 보관함이 없고 서버 한 대로 운영한다. 개발 PC에서 설치 없이 바로 실행되어야 한다.

## 선택지
- H2 파일 모드 + Flyway: 설치가 필요 없고 테스트는 메모리 DB로 빠르다. 대신 배포할 때 다른 DB로 옮기는 작업이 생긴다.
- PostgreSQL(docker) + Flyway: 운영 환경과 같다. 대신 docker가 필요하고, 테스트에 Testcontainers까지 쓰면 무겁다.

## 결정
- H2 파일 모드를 쓴다. DB 파일은 실행 위치 기준 `./data/` 아래에 두고 `.gitignore`에 넣는다. 테스트는 메모리 H2를 쓴다.
- 스키마는 Flyway 마이그레이션(`db/migration/V*__*.sql`)으로만 바꾼다. Hibernate는 `ddl-auto: validate`로 두어 DDL을 실행하지 않는다.
- 배포 시 PostgreSQL로 옮길 수 있게 마이그레이션에는 표준 SQL만 쓴다. H2 전용 문법(`IDENTITY`, `AUTO_INCREMENT`, 호환 모드)을 쓰지 않는다. ID는 UUID 문자열을 쓴다.
- H2 웹 콘솔은 쓰지 않는다. 콘솔 모듈을 의존성에 넣지 않고, 설정도 `spring.h2.console.enabled=false`로 둔다. 테스트로 확인한다.

## 결과
- 얻는 것: 설치 없이 실행할 수 있고 테스트가 빠르다. PostgreSQL로 옮길 경로가 열려 있다.
- 감수하는 것: H2와 PostgreSQL의 세부 동작 차이(타입, 대소문자, 시간대)를 배포 전에 다시 확인해야 한다. PostgreSQL로 옮길 때는 `flyway-database-postgresql` 모듈을 추가한다.
