# 회의 정리 노트 (trace-meeting-notes)

회의 스크립트를 회의록으로 정리하고, **모든 항목을 원문 발언에 연결해 근거 없는 내용을 걸러내는** 서비스.

> 상태: 설계 단계. 기능은 docs/SPEC.md 기준으로 개발 중.

## 핵심
- 정리와 검증을 분리한 2단계 LLM 처리, 근거 위치는 코드로 재확인
- 검토 화면에서 항목별 원문 근거 확인, 근거 없음·약함 표시
- 확정된 내용만 워드로 내보내기 (2차: 한글, 엑셀)
- 모델 교체 가능 (Ollama 로컬 모델 / Claude), 모델별 정확도 비교

## 기술
Spring Boot 3, Java 21, Spring AI / React, TypeScript, Vite, Tailwind, PWA

## AI로 개발하는 방식
이 프로젝트는 AI 코딩 에이전트로 개발한다. 규칙, 권한, 차단 훅, 검증 에이전트 구성은 [docs/AI-ENV.md](docs/AI-ENV.md), 작업 기록은 [docs/AI-WORKFLOW.md](docs/AI-WORKFLOW.md).

## 문서
- [요구사항](docs/SPEC.md) · [아키텍처](docs/ARCHITECTURE.md) · [설계 결정](docs/adr/) · [정확도 측정](eval/README.md)
