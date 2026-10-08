# 위협 모델

두 부분으로 나눈다.
- **A. 개발 환경**: AI 코딩 에이전트가 이 저장소에서 일할 때 생길 수 있는 사고
- **P. 제품**: 서비스가 신뢰할 수 없는 회의 스크립트를 LLM에 넣을 때 생길 수 있는 문제

표의 "강제"는 실행 단계에서 막히는지를 뜻한다. **훅**은 PreToolUse 훅, **권한**은 settings.json의 allow·ask·deny, **규칙**은 문서 안내(강제 아님)다.

## A. 개발 환경

| # | 위협 | 실제 사례 | 대응 | 강제 |
|---|---|---|---|---|
| A1 | 되돌릴 수 없는 삭제 (경로 확장, 따옴표 누락 포함) | AI 코딩 에이전트 사고 9건 중 다수가 `~/` 확장, 공백 경로, 운영 DB 연결 실수 (Adversa 정리, 2025.06~2026.07) | 재귀 삭제, `git clean`, `checkout -- .`, `reset --hard`, 브랜치·stash 삭제 차단. push는 사람만 하므로 **사람이 자주 push해 원격 백업을 유지** | 훅 + 권한 |
| A2 | 손 닿는 곳의 과도한 권한 토큰 | 다른 용도의 토큰을 파일에서 찾아 운영 DB와 백업까지 삭제 (PocketOS, 2026.04) | 진짜 비밀값은 저장소에 두지 않음. Claude 키는 사용 한도를 낮게 건 별도 키. 배포 토큰은 작업 폴더 밖, 최소 범위 | 규칙 (환경 구성으로 보완) |
| A3 | 비밀값 읽기·출력 | 에이전트가 설정 파일·자격 증명을 찾아 쓰는 패턴 (테크 브리핑 10/3 OpenAI 에이전트 사고: 격리 실패 + 노출된 자격 증명) | `.env`·키 파일 Read/Grep/Glob 차단, 셸 출력·환경 변수 출력 차단 | 훅 + 권한 |
| A4 | AI가 자기 권한·훅을 고침 | 에이전트가 도구 설정을 바꿔 우회 경로를 만들려 한 정황 (테크 브리핑 10/6 위키미디어). 저장소의 `.claude/settings.json` 훅, MCP 자동 승인, `ANTHROPIC_BASE_URL`이 공격에 쓰인 취약점 (CVE-2025-59536, CVE-2026-21852, CVE-2026-40068) | `.claude/settings*`, `.claude/hooks/`, `.mcp.json`, `.git/` 수정 차단. 셸로 쓰는 것도 차단. 규칙 문서 수정은 확인 필요. bypass 모드 비활성화 | 훅 + 권한 |
| A5 | 정답·테스트 조작으로 "통과" 만들기 | 에이전트가 실패 테스트를 건너뛰거나 지우는 흔한 패턴 | eval 정답 수정 차단, 테스트에 `@Disabled`·`.skip`·`.only` 추가 차단, test-verifier 독립 검증 | 훅 + 서브에이전트 |
| A6 | 환각 패키지 설치 (slopsquatting) | 생성 코드의 19.7%가 존재하지 않는 패키지 이름 포함, 같은 환각의 43%가 매번 반복됨 (CSA, 2026.04) | 설치 명령·빌드 파일 수정은 확인 필요. 추가 전 실재·배포일·관리자 확인 절차. 잠금 파일 기준 설치 | 권한 + 규칙 |
| A7 | 프롬프트 인젝션 (웹 문서, 이슈, README, 도구 출력) | 외부 내용 속 지시로 명령 실행·유출 유도 (OWASP LLM01) | 외부 내용의 지시 무시 규칙. 원격 스크립트 파이프 실행 차단. `curl`·`wget` 확인 필요. MCP 전부 거부 | 규칙 + 훅 + 권한 |
| A8 | 커밋으로 비밀값 공개 | 공개 저장소에 키 커밋 | 커밋 시 스테이징 내용을 훅이 검사 (키 형태, 비밀 파일). security-checker 점검 | 훅 + 서브에이전트 |
| A9 | 지시가 긴 세션에서 무시됨 | "DO NOT RUN" 지시를 확인한 뒤 실행 (Adversa 사례 6) | 금지는 문서가 아니라 훅으로. 컴팩션 때 보존 항목 지정 | 훅 |
| A10 | 남의 저장소를 열 때 | 저장소 파일만으로 세션 장악 (위 CVE들) | Claude Code 최신 버전 유지. 남의 저장소는 `.claude/`, `.mcp.json`, `CLAUDE.md`를 먼저 읽고 연다 | 사람 |
| A11 | CI에서 비밀값 유출 | 외부 PR이 비밀값 있는 워크플로를 실행 | `pull_request_target` 금지, 최소 권한. CI 추가 시 상의 | 규칙 |

## P. 제품

| # | 위협 | 대응 | 위치 |
|---|---|---|---|
| P1 | 스크립트 속 지시문으로 회의록 조작 (프롬프트 인젝션) | LLM에 도구·네트워크 권한 없음 → 피해가 "틀린 항목"으로 한정. 근거 위치를 코드로 재확인, 검증 단계 분리, 사람 확정. eval 인젝션 케이스 | rules/llm.md, SPEC S5 |
| P2 | 작업 ID 추측으로 남의 회의록 열람 (IDOR) | 로그인이 없으므로 ID가 곧 권한. `SecureRandom` UUID, 보관 기간 후 삭제. 근거: 인증 누락·접근통제 미비로 6분 만에 개인정보 조회 실험 (테크 브리핑 10/6), `Math.random` 세션 키 복원 (10/5) | rules/backend.md, SPEC S1·S2 |
| P3 | 결과 화면 XSS | 일반 텍스트 렌더링, `dangerouslySetInnerHTML` 금지 | rules/frontend.md, SPEC S6 |
| P4 | 키 노출 | 키는 백엔드만. `VITE_` 변수는 공개됨 | rules/frontend.md, SPEC S7 |
| P5 | 비용 공격 (공개 데모에서 Claude 호출 남용), 큰 입력으로 자원 고갈 | 입력 상한, 요청 수 제한, 하루 호출 상한. 데모 기본값은 Ollama 또는 fixture | rules/backend.md, SPEC S3·S4 |
| P6 | Ollama 외부 노출 | 기본값 127.0.0.1 유지 | rules/backend.md |
| P7 | URL 가져오기 기능 추가 시 SSRF | 1차 범위 밖. 추가 전 방어 설계 ADR. 근거: URL을 받는 MCP 서버 SSRF 미패치 (테크 브리핑 10/7) | rules/backend.md |
| P8 | 로그로 회의 내용 유출 | 본문 로그 금지 (길이·ID만) | rules/security.md |
| P9 | 엑셀 내보내기의 수식 주입 (`=`, `+`, `-`, `@`로 시작하는 셀) | 엑셀 내보내기는 이번 프로젝트 범위 밖이라 해당 없음. 추가하게 되면 셀을 텍스트로 이스케이프하는 기준을 SPEC에 먼저 넣는다 | 해당 없음 (SPEC 범위 밖) |

## 막지 못하는 것 (한계)

- **셸 문자열 검사는 경계가 아니다.** 훅과 Bash 규칙은 명령 문자열을 본다. 변수로 경로를 숨기거나, 스크립트 파일을 만들어 실행하거나, 허용된 `./gradlew test`·`npm run` 안에서 비밀값을 읽는 코드를 돌리면 통과할 수 있다. Claude Code 문서도 Read 거부 규칙은 "파일을 직접 여는 Python·Node 스크립트 같은 하위 프로세스"에는 적용되지 않는다고 적고 있다.
- **실제 경계는 두 가지다.**
  1. **샌드박스** (`/sandbox`): OS 수준에서 파일·네트워크 접근을 막는다. macOS, Linux, WSL2만 지원하고 **Windows 네이티브는 지원하지 않는다.** Windows에서는 WSL2 안에서 Claude Code를 실행해야 쓸 수 있다.
  2. **손 닿는 곳에 진짜 비밀값을 두지 않는 것**: 이 프로젝트의 비밀값은 Claude API 키 하나다. 사용 한도를 낮게 건 이 프로젝트 전용 키를 쓰고, 유출되면 바로 폐기한다.
- **사람 확인을 습관적으로 누르면 ask는 의미가 없다.** 빌드 파일, 규칙 문서, 설치 명령의 확인 창은 내용을 보고 누른다.
- **보관 기간이 지나 지운 스크립트가 DB 파일에 남을 수 있다.** 삭제는 논리 삭제라 H2가 공간을 재사용하기 전까지 파일에 평문으로 남는다. 정상 종료 시 정리된다. 리뷰 실험에서 강제 종료 시 67건, 정상 종료 시 0건이 발견됐다. DB 파일에 접근할 수 있는 경우(백업, 디스크 유출)에만 해당하고, 운영 배포 시 DB 선택과 함께 재검토한다 (ADR 0005).

## 출처
- Adversa AI, AI coding agent incidents: https://adversa.ai/blog/ai-coding-agent-incidents/
- PocketOS 데이터베이스 삭제 정리: https://www.mayhemcode.com/2026/04/cursor-ai-agent-database-deletion-what.html
- CSA, Slopsquatting 연구 노트: https://labs.cloudsecurityalliance.org/research/csa-research-note-slopsquatting-ai-supply-chain-20260419/
- Claude Code 저장소 파일 취약점 (훅, MCP, ANTHROPIC_BASE_URL): https://hackmag.com/news/claude-code-flaws
- CVE-2026-40068 (worktree 설정으로 신뢰 확인 우회): https://vuln.today/cve/CVE-2026-40068
- CVE-2025-59829 (심볼릭 링크로 거부 규칙 우회, 1.0.120 수정): https://api.osv.dev/v1/vulns/CVE-2025-59829
- Read 거부 규칙과 Bash 동작 차이 이슈: https://github.com/anthropics/claude-code/issues/45200
- Claude Code 권한 문서: https://code.claude.com/docs/en/permissions
- Claude Code 샌드박스 문서: https://code.claude.com/docs/en/sandboxing
- 일일 테크 브리핑 2026-10-01 ~ 10-07 (개인 구독 보고서)
