# 제품 프롬프트

- 파일 이름: `<단계>.v<버전>.md` (예: summarize.v1.md, verify.v1.md)
- 기존 버전은 지우지 않는다. 새 버전을 만들고 `/eval`로 비교한 뒤 설정에서 버전을 바꾼다.
- 변경은 `/prompt` 스킬로 하고, `prompt-reviewer` 리뷰를 거친다.

| 파일 | 단계 | 상태 |
|---|---|---|
| summarize.v1.md | 정리 | 실행·eval한 적 없는 초안, v2로 대체 (출력 스키마가 SPEC F2와 다름) |
| summarize.v2.md | 정리 | 사용 중 (app.summary.prompt-version: v2), 첫 기준선 eval/results/2026-10-13_qwen3-8b.md. v3·v4와 비교 후 F2 정리 프롬프트로 확정 |
| summarize.v3.md | 정리 | v2 기준선 문제(인젝션 확정, 결정 값·본인 할 일·기한 누락) 수정안. v2와 비교(eval/results/2026-10-13_qwen3-8b_v2-v3.md)에서 인젝션 확정이 그대로(5/5)이고 AGENDA·근거·담당자 지표가 나빠져 설정 전환 안 함 |
| summarize.v4.md | 정리 | v2에서 두 줄만 더함(결정 값 포함, 스스로 맡은 일은 TODO). F2 마지막 시도. v2와 비교(eval/results/2026-10-13_qwen3-8b_v2-v4.md)에서 결정 값 포함은 올랐지만 인젝션 확정·근거 정확 일치·검증기 제거가 나빠져 v2로 확정 |
| verify.v1.md | 검증 | 초안 |
