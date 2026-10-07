---
name: docs-sync
description: 기능(F) 단위가 끝난 뒤 README, ARCHITECTURE, SPEC 체크박스를 실제 코드·테스트에 맞춘다.
disable-model-invocation: true
---
대상 기능: $ARGUMENTS (예: F1)

1. 근거를 먼저 모은다. 문서는 아직 고치지 않는다.
   - 계획 파일 docs/plans/<기능>.md, docs/SPEC.md, docs/adr/
   - 실제 코드: backend/src, frontend/src의 패키지·클래스·컴포넌트 구성
   - 버전: backend/build.gradle(.kts), gradle/wrapper/gradle-wrapper.properties, frontend/package.json, package-lock.json. 기억으로 쓰지 않는다.
   - 테스트를 실행한다: `backend/`에서 `./gradlew test`, `frontend/`에서 `npm test`와 `npm run typecheck`. 세 결과(명령, 통과·실패 수, 오류)를 한 표로 함께 그대로 보여준다.
2. 문서별로 확인한다.
   - README.md: 진행 상태(완료한 F·W와 커밋), 실행 방법(CLAUDE.md 명령어와 같은지), 주요 기능(코드에 있는 것만), 기술 스택과 실제 버전, 화면 캡처 위치(docs/images/. 파일이 없으면 "추가 예정"으로 두고 캡처를 만들지 않는다)
   - docs/ARCHITECTURE.md: 구조도와 패키지 구성(실제 패키지 이름), 주요 흐름의 시퀀스 다이어그램(Mermaid `sequenceDiagram`). 아직 없는 구성 요소(예: F2 이후의 llm, evidence)는 "예정"으로 구분해 표시한다
   - docs/SPEC.md: 수용 기준마다 확인한 테스트 이름을 짝짓는다. 그 테스트가 있고 1단계 실행에서 통과했을 때만 `[x]`로 바꾼다. 테스트가 없거나 일부만 확인하면 체크하지 않고 빠진 부분을 적는다
3. 고치기 전에 목록으로 보여주고 확인받는다.
   - 코드와 문서가 다른 곳: 파일·위치, 문서 내용, 실제 코드(파일:줄), 제안(문서 수정 / 코드가 틀림 / 결정 필요)
   - SPEC 체크 표: 수용 기준 | 테스트 이름 | 결과 | 체크 여부
   - 코드가 문서와 다르면 문서를 코드에 맞추지 말고 먼저 묻는다. 코드가 틀렸을 수 있다
4. 확인받은 항목만 고친다.
   - 코드에 없는 기능, 실행하지 않은 명령, 확인하지 않은 버전은 쓰지 않는다
   - 근거 연결·검증 결과 같은 회의 데이터나 실제 회의 내용을 예시로 넣지 않는다. 예시는 가상 데이터만
   - SPEC은 체크박스만 바꾼다. 수용 기준 문장을 고쳐야 하면 따로 묻는다
5. Mermaid 문법이 맞는지 확인한다(블록 시작·끝, 참여자 이름). 렌더링 도구를 새로 설치하지 않는다.
6. 바뀐 내용을 요약해 보여주고 /commit으로 넘긴다(`docs: <기능> 문서 최신화`).
