---
name: prompt
description: 제품 프롬프트(정리·검증)를 새로 만들거나 고친다. 버전 관리와 eval 비교까지 진행.
disable-model-invocation: true
---
대상과 변경 내용: $ARGUMENTS

1. prompts/의 현재 버전과 .claude/rules/llm.md를 읽는다.
2. 기존 파일을 덮어쓰지 않고 새 버전 파일을 만든다 (예: summarize.v1.md → summarize.v2.md).
3. 파일 맨 위에 변경 이유와 기대 효과를 적는다.
4. prompt-reviewer 서브에이전트로 리뷰하고 지적 사항을 반영한다.
5. 사용자 확인 후 /eval로 이전 버전과 비교한다. 지표가 나빠지면 교체하지 않고 결과를 보고한다.
6. 교체를 확정하면 설정의 프롬프트 버전을 올리고 /adr 또는 /ai-log에 기록할지 묻는다.
