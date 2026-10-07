# AI 개발 환경

이 프로젝트는 AI 코딩 에이전트(Claude Code)로 개발한다. 이 문서는 AI가 **무엇을 따르고, 무엇을 할 수 없고, 결과를 어떻게 검증받는지**를 설명한다.

## 원칙
1. **규칙은 문서로, 금지는 훅으로.** 문서(CLAUDE.md, rules)는 AI가 따르도록 안내하고, 반드시 지켜야 하는 것은 훅이 실행 단계에서 막는다.
2. **만든 AI가 스스로 채점하지 않는다.** 리뷰, 테스트 검증, 보안 점검은 새 컨텍스트의 서브에이전트가 한다.
3. **증거로 완료를 판단한다.** 테스트 실행 결과, eval 수치 없이 "완료"로 보지 않는다.
4. **사람이 결정한다.** 설계 결정, 의존성 추가, 커밋, push는 사람이 확인하거나 직접 한다.

## 구성
| 위치 | 역할 | 강제 여부 |
|---|---|---|
| `CLAUDE.md` | 프로젝트 개요, 핵심 원칙, 작업 흐름 (매 세션 로드) | 안내 |
| `.claude/rules/*.md` | 주제별 규칙: 보안, LLM, 테스트, 백엔드, 프론트엔드, Git | 안내 |
| `.claude/settings.json` | 권한(허용·확인·거부), 훅 연결, 커밋 AI 서명 끄기 | **강제** |
| `.claude/hooks/*.js` | 실행 전 차단: 비밀값, AI 설정 자기 수정, eval 정답·테스트 조작, 되돌릴 수 없는 작업, 커밋 비밀값 검사 | **강제** |
| `docs/THREAT-MODEL.md` | 위협, 실제 사례, 대응, 한계 | 근거 |
| `.claude/skills/` | 반복 작업 절차 (`/spec`, `/plan`, `/implement`, `/eval`, `/prompt`, `/adr`, `/ai-log`, `/commit`) | 절차 |
| `.claude/agents/` | 독립 검증자 (reviewer, test-verifier, security-checker, prompt-reviewer) | 검증 |

## 권한
- **허용(묻지 않음):** 빌드·테스트 실행, git 조회
- **확인 필요:** 커밋, 브랜치 전환·복원, 패키지 설치(npm, npx, pip 등), `curl`·`wget`, docker, 빌드·의존성 파일 수정, `CLAUDE.md`와 규칙·스킬·에이전트 문서 수정, CI 설정 수정
- **거부:** `.env`·키 파일 읽기, AI 권한 설정·훅·`.mcp.json`·`.git/` 수정, eval 정답 수정, `git push`·`remote`·`reset --hard`·`clean`, 모든 MCP 도구
- **bypass 모드 비활성화** (`disableBypassPermissionsMode`), 프로젝트 MCP 자동 승인 끔

## 훅이 막는 것
| 훅 | 막는 것 | 이유 |
|---|---|---|
| guard-files | `.env`, 키 파일 읽기·검색·수정 (Read, Grep, Glob 포함) | 키 노출 방지 |
| guard-files | `.claude/settings*`, `.claude/hooks/`, `.mcp.json`, `.git/` 수정 | AI가 자기 권한을 넓히는 것 방지 |
| guard-files | `eval/cases/*/expected*`, `planted*` 수정 | 정답을 바꿔 정확도를 맞추는 것 방지 |
| guard-files | 테스트에 `@Disabled`, `.skip`, `.only`, `xit` 추가 | 실패 테스트를 건너뛰어 "통과"시키는 것 방지 |
| guard-bash | push, 강제 리셋, clean, 변경 전체 버리기, 브랜치·stash 삭제, 이력 재작성, `--no-verify` | 되돌릴 수 없는 작업은 사람이 |
| guard-bash | 재귀 삭제 (rm, Remove-Item, rmdir /s, del /s, find -delete) | 경로 확장 실수로 인한 대량 삭제 방지 |
| guard-bash | `.env` 다루기, 환경 변수 출력 | 키가 대화 기록에 남는 것 방지 |
| guard-bash | 셸로 AI 설정·훅·git 훅 쓰기, 권한 끄는 옵션, MCP 추가 | 설정 우회 방지 |
| guard-bash | 내려받은 스크립트 바로 실행 (`curl ... \| sh`) | 원격 코드 실행 방지 |
| guard-bash | 커밋 시 스테이징 내용 검사 (키 형태 문자열, 비밀 파일), AI 서명 | 공개 저장소 유출 방지 |

## 막지 못하는 것
훅과 권한 규칙은 명령 문자열과 도구 인자를 본다. 스크립트를 만들어 실행하는 식으로 우회할 수 있다. 실제 경계는 **샌드박스**(macOS, Linux, WSL2만 지원. Windows에서는 WSL2 안에서 실행)와 **진짜 비밀값을 손 닿는 곳에 두지 않는 것**이다. 자세한 위협과 근거: [THREAT-MODEL.md](THREAT-MODEL.md)

## 사람이 할 일
- 사용 한도를 낮게 건 이 프로젝트 전용 Claude API 키를 쓴다.
- 작업 단위마다 직접 push해서 원격 백업을 유지한다 (AI는 push 불가).
- 확인 창은 내용을 보고 누른다. 특히 빌드 파일, 의존성, 규칙 문서 변경.
- Claude Code를 최신 버전으로 유지한다.
- 권한 설정과 훅을 바꿀 때는 직접 수정한다.

## 작업 흐름
```
/spec 기능 → /plan 기능 → /implement 작업 → reviewer → test-verifier → /commit (security-checker)
프롬프트 변경: /prompt → prompt-reviewer → /eval 비교 → 확정
```

## 검증 기록
- AI로 한 작업과 사람이 확인·수정한 부분은 `docs/AI-WORKFLOW.md`에 남긴다.
- 정확도는 `eval/results/`에 모델별로 남긴다.
