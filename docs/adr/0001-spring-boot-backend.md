# 0001. 백엔드는 Spring Boot + Java
- 날짜: 2026-10-07
- 상태: 확정

## 배경
백엔드 언어와 프레임워크 선택.

## 선택지
- Spring Boot + Java: 목표로 하는 중견기업의 Java 수요와 맞음. 한글(HWP) 파일을 다루는 Java 라이브러리(hwplib, hwpxlib, Apache-2.0)가 있음. Spring AI로 여러 LLM을 같은 방식으로 연동 가능.
- NestJS + TypeScript: 프론트와 같은 언어라 빠르지만, 기존 업무에서 이미 쓰는 스택이라 새로 보여주는 것이 적음.

## 결정
Spring Boot 3 + Java 21. Spring AI는 시작 시점의 최신 안정 버전을 확인해 고른다.

## 결과
- 얻는 것: Java 백엔드 역량, HWP 내보내기 경로
- 감수하는 것: 프론트와 언어가 달라 모델(DTO) 타입을 양쪽에서 관리
