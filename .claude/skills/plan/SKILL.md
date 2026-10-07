---
name: plan
description: SPEC의 한 기능을 구현 계획으로 쪼갠다. 코드는 쓰지 않는다.
disable-model-invocation: true
---
기능: $ARGUMENTS (docs/SPEC.md의 기능 이름)

1. 코드를 수정하지 않는다. 읽기와 조사만 한다.
2. 관련 코드와 docs/ARCHITECTURE.md, docs/adr/를 읽는다.
3. 계획을 작성한다:
   - 바뀌는 파일과 새로 만드는 파일
   - 작업 단위 (각 단위는 테스트 하나 이상으로 끝나게)
   - 각 작업의 테스트 이름과 확인할 내용
   - 위험한 부분과 선택지 (선택이 필요하면 장단점과 함께)
4. 새 설계 결정이 필요하면 /adr로 기록할 것을 표시한다.
5. 계획을 보여주고 사용자 확인을 받는다.
