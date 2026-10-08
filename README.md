# 회의 정리 노트 (trace-meeting-notes)

회의 스크립트를 회의록으로 정리하고, **모든 항목을 원문 발언에 연결해 근거 없는 내용을 걸러내는** 서비스.

> 상태: F1(스크립트 입력) 완료. F2(정리)부터 개발 중. 기능 기준은 [docs/SPEC.md](docs/SPEC.md).

## 주요 기능

### 구현됨 (F1 스크립트 입력)
- 스크립트 붙여 넣기 또는 .txt 파일 열기 (브라우저에서 읽음, UTF-8·EUC-KR(CP949) 판별, 업로드 API 없음)
- `이름:` 화자 표시 인식과 줄 단위 발언 번호 부여 (분할은 서버에서만)
- 입력이 멈추고 0.5초 뒤 미리보기 한 줄: `발언 N개 · 화자 M명 (…)`
- 작업 생성 (작업 ID는 UUID v4), 보관 기간(기본 24시간)이 지나면 삭제
- 서버 검사: 50,000자 상한, 요청 본문 200KB 상한, NUL 문자 거부, IP별 요청 수 제한 (미리보기 분당 60회, 작업 생성 시간당 20회)
- 입력 본문·작업 ID를 로그에 남기지 않음, 화면은 일반 텍스트로만 렌더링

### 예정 (F2~F5)
- 정리와 검증을 분리한 2단계 LLM 처리, 근거 위치는 코드로 재확인
- 검토 화면에서 항목 아래 근거 발언(번호·화자·내용) 확인, 근거 없음·약함 표시
- 확정된 내용만 워드(docx)로 내보내기
- 모델 교체 가능 (Ollama 로컬 모델 / Claude), 모델별 정확도 비교

## 기술 스택
| 구분 | 사용 기술 (버전) |
|---|---|
| 백엔드 | Java 21 (Gradle 툴체인), Gradle 9.8.0, Spring Boot 4.1.1, H2(파일 DB) + Flyway (버전은 Spring Boot BOM 관리) |
| 프론트엔드 | Node 22.22.2 이상, React 19.3.0, TypeScript 5.9.3, Vite 8.3.3, Tailwind CSS 4.3.3, TanStack Query 5.104.1 |
| 테스트 | JUnit 5 (Spring Boot Test), Vitest 5.0.3, Testing Library |
| 예정 | Spring AI (F2), PWA |

## 실행 방법 (Windows PowerShell)

요구 사항: JDK 21, Node.js 22.22.2 이상

백엔드 (`http://127.0.0.1:8080`)
```powershell
cd backend
.\gradlew.bat bootRun
```

프론트엔드 (`http://localhost:5173`, `/api` 요청은 Vite 프록시가 백엔드로 넘김)
```powershell
cd frontend
npm ci
npm run dev
```

테스트
```powershell
cd backend
.\gradlew.bat test

cd ..\frontend
npm test
npm run typecheck
```

## 화면 캡처
- `docs/images/` (추가 예정)

## AI로 개발하는 방식
이 프로젝트는 AI 코딩 에이전트로 개발한다. 규칙, 권한, 차단 훅, 검증 에이전트 구성은 [docs/AI-ENV.md](docs/AI-ENV.md), 작업 기록은 [docs/AI-WORKFLOW.md](docs/AI-WORKFLOW.md).

## 문서
- [요구사항](docs/SPEC.md) · [아키텍처](docs/ARCHITECTURE.md) · [설계 결정](docs/adr/) · [정확도 측정](eval/README.md)
