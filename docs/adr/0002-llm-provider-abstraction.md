# 0002. LLM은 인터페이스 뒤에 두고 설정으로 교체
- 날짜: 2026-10-07
- 상태: 확정

## 배경
Claude API는 사용량만큼 비용이 든다. 개발 PC는 Intel 내장 그래픽이라 큰 로컬 모델은 느리다.

## 결정
- `LlmClient` 인터페이스 + 구현체 3개: Ollama(개발, 무료), Claude(정확도 측정·시연), Fixture(테스트)
- 개발 중에는 7~8B급 이하 로컬 모델로 흐름을 만들고, 품질 비교와 시연에만 Claude를 짧게 쓴다.

## 결과
- 얻는 것: 개발 비용 거의 없음, 모델별 근거 검증 정확도 비교 가능
- 감수하는 것: 작은 모델은 JSON 형식과 근거 위치를 자주 틀려 재시도·검증 로직이 더 필요

## 변경 (2026-10-08): OpenAI 호환 클라이언트 하나와 llama-server 기본
- 구현체를 `OpenAiCompatibleLlmClient` 하나와 `FixtureLlmClient`(테스트)로 바꾼다. 주소·모델명·키를 설정으로 바꿔 llama.cpp의 llama-server와 Ollama를 같은 클라이언트로 쓴다. 두 실행기의 속도와 근거 탐지율은 eval로 비교한다.
- 기본 실행기는 llama.cpp Vulkan 빌드의 llama-server다. 127.0.0.1에서만 열고 `--api-key`를 쓴다. 키는 `.env`의 변수로만 다루고(`.env.example`에는 이름만), AI는 `.env`를 읽지 않는다. Ollama는 선택 사항이다.
  - llama-server를 기본으로 고른 이유
    - 성능: 이 노트북(Intel Arc 130V 내장 GPU)에서 llama.cpp Vulkan 빌드가 잘 동작하고 SYCL보다 약 2배 빠르다는 사용 후기가 있다(노트북 실측으로 다시 확인). Ollama는 Intel GPU 지원이 제한적이다.
    - 보안: 항상 켜져 있는 백그라운드 서비스가 없고 `--api-key`를 쓸 수 있다. Ollama는 2026년 GGUF 모델 로더 취약점(CVE-2026-7482 "Bleeding Llama", 인증 없는 API로 프로세스 메모리 유출, 0.17.1에서 수정) 사례가 있다. 출처: https://asec.ahnlab.com/en/93699/
    - Ollama는 같은 클라이언트로 쓸 수 있는 선택 사항으로 남긴다.
- 호출은 Spring AI 없이 JDK HttpClient 또는 Spring `RestClient`로 `/v1/chat/completions`를 직접 부른다 (ADR 0001 변경 참고).
- 개발 노트북: Core Ultra 5 228V, 메모리 32GB(GPU 공유, 평소 여유 약 10GB), Arc 130V 내장 GPU. 그래서 모델은 7~9B급 4비트(약 5~6GB)를 기준으로 한다.
- Claude 구현체는 F2에 넣지 않고 eval 비교 단계에서 추가한다. S4의 Claude 하루 호출 상한도 그때 구현한다.
  - Claude를 F2에서 뺀 이유: 모델을 하나로 고정해 흐름 문제가 생겼을 때 원인을 좁히기 위해서다. 비용이 들지 않고 다룰 비밀값이 llama-server 키 하나로 줄어든다. `LlmClient` 인터페이스 뒤에 있으므로 나중에 구현체만 추가하면 된다.
- 모델: 기본 Qwen3-8B, 비교 Kanana 1.5 8B(공식 원본을 직접 GGUF로 변환). EXAONE 3.5는 비상업 라이선스라 제외한다 (SPEC F2 "모델").

## 변경 (2026-10-12): 샘플링 고정
- 배경: 요청에 temperature·seed를 넣지 않아 실행기 기본 샘플링을 썼다. W8 수동 확인에서 같은 스크립트의 결과(안건 유무, 기한 유무)가 실행마다 달라, 품질 문제의 원인을 모델과 검증 중 어디로 볼지 가를 수 없었다.
- 요청마다 `temperature`(`app.llm.temperature`)와 `seed`(`app.llm.seed`)를 보낸다. 범위 밖(temperature 0~2 밖, seed 음수)이면 시작하지 않는다. seed -1(실행기의 무작위 seed)은 고정이 아니라 받지 않는다.
- 기본값 temperature 0.2: 정리는 발언에 있는 내용을 옮기는 작업이라 다양한 표현보다 일관성이 중요해 낮게 둔다. 0(항상 가장 높은 확률만 고름)은 작은 모델에서 같은 문장을 반복하는 문제가 생길 수 있어 피했다(이 모델에서 실측한 것은 아님). W9 eval에서 조정한다.
- 기본값 seed 42: 값 자체에 의미는 없고, 같은 요청을 다시 보냈을 때 같은 결과가 나오게 하려고 고정한다.
- 한계: 요청마다 구분자가 무작위(SummaryPromptBuilder)라 입력 자체가 달라지므로, seed를 고정해도 같은 스크립트의 결과가 항상 같다고 보장하지 않는다. 변동을 줄이는 효과만 기대한다. eval에서 케이스를 여러 번 실행해 변동을 측정한다.
