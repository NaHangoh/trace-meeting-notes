---
name: commit
description: 변경분을 점검하고 커밋 메시지를 제안한다. 커밋은 사용자 확인 후.
disable-model-invocation: true
---
1. git status와 git diff로 변경분을 정리한다.
2. security-checker 서브에이전트로 비밀값·개인정보·AI 서명을 점검한다 (커밋 명령 때 훅도 스테이징 내용을 다시 검사한다). 문제가 있으면 멈추고 보고한다.
3. 관련 테스트가 통과하는지 확인한다.
4. `.claude/rules/git.md` 형식으로 커밋 메시지를 제안한다. AI 서명은 넣지 않는다.
5. 사용자가 확인하면 커밋한다. push는 하지 않는다.
