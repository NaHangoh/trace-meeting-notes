# F2 정리 구현 계획

- 기준 문서: docs/SPEC.md F2, S2·S4, ADR 0002(변경 2026-10-08), ADR 0007, rules/llm.md
- 진행 방식: 작업 단위(W)마다 실패 테스트 → 구현 → 전체 테스트 → reviewer → /commit(security-checker 포함). 단위가 끝나면 멈추고 사용자 확인 후 다음 단위.
- 파일은 편집 도구로만 쓴다. 자동 테스트는 FixtureLlmClient 또는 가짜 HTTP 서버(JDK `com.sun.net.httpserver.HttpServer`)만 쓴다. 실제 LLM 호출은 `@Tag("llm")`.

## 확정된 결정

| 항목 | 결정 | 기록 |
|---|---|---|
| LLM 클라이언트 | Spring AI 없이 JDK HttpClient 또는 Spring `RestClient`로 `/v1/chat/completions` 직접 호출. 구현은 `OpenAiCompatibleLlmClient` + `FixtureLlmClient` | ADR 0001·0002 |
| 실행기 | llama.cpp Vulkan llama-server, `127.0.0.1:8081`, `--api-key`, 키는 `LLM_API_KEY` 환경 변수. Ollama 선택 | ADR 0002 |
| Claude | F2에 넣지 않음 (eval 비교 단계) | ADR 0002 |
| 모델 | 기본 Qwen3-8B Q4_K_M, 비교 Kanana 1.5 8B(공식 원본 직접 변환). EXAONE 제외 | SPEC F2 |
| 생각 모드 | 요청에 `chat_template_kwargs: {"enable_thinking": false}`, 설정 `app.llm.disable-thinking` (Kanana는 끔) | W1 끝 `@Tag("llm")`로 확인 |
| 출력 강제 | 요청에 `response_format`(JSON 스키마). 서버 쪽 스키마 검증 유지 | SPEC F2 |
| 프롬프트 | `summarize.v2.md`를 첫 기준선으로 새로 작성. v1은 "실행·eval한 적 없는 초안, v2로 대체" 표시만 | prompts/README.md |
| 결과 형식 | 평면 `items` + 원문 표현(`assignee`·`due`), 근거 확인은 공백 정규화 후 부분 일치(화자 이름 포함) | SPEC F2 |
| 덩어리 | 최대 6,000자(설정값), 발언 경계. 6,000자 넘는 발언은 잘라 같은 번호로 | SPEC F2 |
| 실패 처리 | 호출 타임아웃 5분, 형식 오류만 재시도 최대 2회, 타임아웃·연결 거부는 바로 실패 | SPEC F2 |
| 동시 작업 | 작업자 `app.summary.workers`(기본 1), 대기열 10(설정값), 초과 429 `QUEUE_FULL`, 재시작 시 `SERVER_RESTART`. `SummaryQueue` 인터페이스 뒤 메모리 구현 | ADR 0007 |
| 조회 | `GET /api/jobs/{id}` IP당 분당 120회(설정값). 형식 틀린·없는·삭제된 ID는 같은 404 | SPEC F2 |
| 결과 화면 | `#/jobs/{id}` 해시 라우팅(새 의존성 없음), 2초 조회(설정값) | SPEC F2 |
| 오류 코드 | 기존 `TOO_MANY_REQUESTS` 유지, `QUEUE_FULL` 추가 | ADR 0007 |

## 작업 단위

### W1. LLM 설정과 클라이언트 ✅ 완료 (6b7769f)
- llm 패키지: `LlmProperties`(provider, base-url, model, api-key는 `LLM_API_KEY`, timeout 5분, disable-thinking), `LlmClient`, `OpenAiCompatibleLlmClient`, `FixtureLlmClient`, 호출 결과·실패 종류(형식 오류는 W4에서 판정, 여기서는 `TIMEOUT`, `CONNECTION_REFUSED`, 그 밖의 HTTP 오류)
- 테스트(가짜 HTTP 서버): requestHasResponseFormatAndNoTools, requestHasChatTemplateKwargsWhenThinkingDisabled, apiKeyOnlyInAuthorizationHeader, apiKeyNotInErrorsOrLogs, timeoutIsTimeoutFailure, connectionRefusedIsConnectionFailure, missingApiKeyFailsStartupWithKeyNameOnly(값은 메시지에 넣지 않음)
- 끝에 `@Tag("llm")` 확인: llama-server가 `response_format`과 `chat_template_kwargs`를 받는지 (사용자가 실행기를 띄운 상태에서, 호출 수 1회 사전 확인)
- 결과: 위 테스트 외에 연결 타임아웃 10초(`app.llm.connect-timeout`)·분류, base-url http(s) 검사·끝 슬래시, 5xx·content null·응답 도중 끊김, 키 제어 문자 시작 차단, 시작 실패 출력(FailureAnalyzer 포함)에 키 없음, fixture WARN, 루트 `.env` 읽기(`spring.config.import`, 테스트 프로필 제외)와 `APP_LLM_*` 참조, Gradle `test`에서 fixture 고정·LLM 환경 변수 제거, `llmTest` 작업. 백엔드 193개 통과
- `@Tag("llm")` 실제 확인(2026-10-08, Qwen3-8B Q4_K_M, llama-server `-c 16384 -np 1`): 1차 thinkTagAbsent·schemaValid, 항목 4개, 14.6초 / 2차 reasoningEmpty까지 확인, 항목 5개, 12.3초. 응답 본문은 보지 않음
- 실패 대응 결정(사용자): `HTTP_ERROR` → `LLM_ERROR`(재시도 없음, 로그는 상태 코드만), `MALFORMED_RESPONSE` → `INVALID_OUTPUT`(최대 2회). SPEC F2 반영
- reviewer 반영: 키 제어 문자 시 JDK 예외로 키 노출(중간), 셸 환경 변수가 테스트의 fixture를 덮어씀(높음), 시작 실패 출력 테스트의 환경 의존(중간)
- W6으로 미룸: `InterruptedException`을 `TIMEOUT`으로 분류하는 문제(재시작 처리와 함께)

### W2. 정리 프롬프트 v2 ✅ 완료 (8b9425c)
- `/prompt`로 `prompts/summarize.v2.md` 작성 → prompt-reviewer. 출력은 SPEC F2 스키마
- 프롬프트 로더와 메시지 조립: 지시는 시스템 메시지, 발언은 `[번호] 화자: 내용` 형식으로 요청마다 무작위 구분자 블록 안에
- 테스트: utterancesOnlyInsideDelimiter, contentCannotCloseDelimiter(발언에 구분자와 같은 문자열이 있어도 블록이 닫히지 않음), injectionTextStaysAsData, promptVersionFromConfig
- 결과: summary 패키지(SummaryPromptBuilder: 구분자 `SCRIPT_` + 16자리 16진수 SecureRandom, 발언과 겹치면 다시 뽑기, 줄 구분 문자 `\R`은 공백으로, response_format 스키마 / PromptLoader: classpath `prompts/summarize.vN.md`, 버전은 v숫자만, 앞쪽 주석 제거 / SummaryProperties `app.summary.prompt-version` 기본 v2), LlmRequest null 검사, Gradle processResources로 `prompts/*.md` 복사. 백엔드 218개 통과
- 추가 테스트: delimiterDiffersPerRequest, lineBreaksInsideContentAreFlattened, otherLineSeparatorsCannotCreateFakeUtteranceLines(\r·U+2028·U+2029·U+0085), realPromptDescribesTheBlockTheBuilderMakes, schemaMatchesSpec(maxItems·maxLength 포함), templateWithoutDelimiterPlaceholderIsRejected, PromptLoaderTest 3종, SummaryConfigTest 3종, LlmRequestTest
- prompt-reviewer 반영: 담당자·기한 발언을 TODO 근거에 포함, 본인이 맡으면 화자 이름, 담당자 여럿이면 TODO 분리, DECISION·TODO·AGENDA 경계, 내용 속 가짜 `[번호]`, 화자 이름 위장, 형식 요구는 결정·할 일 아님, null 표기
- 결정(사용자): 근거 최소 개수(minItems) 강제 안 함, 이유는 SPEC F2에 기록
- W4에서 이어갈 것: 빌더를 클라이언트까지 거친 요청 본문 확인(SPEC 수용 기준 "response_format 있음·tools 없음·발언은 구분자 블록 안에만")

### W3. 응답 검증
- `SummaryValidator`: JSON 파싱, 스키마 밖 필드 버림, `text` 500자 초과·알 수 없는 `type` 항목 버림, 덩어리당 100개 초과 버림, `assignee`·`due`는 TODO에만
- 근거 번호: 존재하지 않거나 덩어리 범위 밖이면 버림, 남은 근거가 없으면 "근거 없음"
- 담당자·기한: 근거 발언의 화자 이름과 내용에 공백 정규화 후 부분 일치할 때만 유지
- 테스트: dropsUnknownFields, dropsOverlongText, dropsUnknownType, capsItemsPerChunk, dropsEvidenceOutsideChunk, itemWithoutEvidenceIsUnsupported, keepsAssigneeFoundInSpeakerOrText, clearsAssigneeAndDueNotInEvidence, whitespaceVariantsMatch(전각 공백은 `Character.toString(0x3000)`), invalidJsonIsFormatError
- ✅ 완료 (6c455d0). 결과: SummaryValidator, SummaryItem(toString에서 내용·담당자 숨김), SummaryItemType, InvalidSummaryOutputException, `minutes.Whitespace` public 전환·`normalize`. 백엔드 244개 통과
- 판정 기준: 최상위 JSON 객체·items 배열이 아니면 형식 오류(INVALID_OUTPUT, 다시 부름), 항목 단위 문제는 그 항목·값만 버림. 근거 번호는 정수이고 덩어리에 있는 번호만, 처음 나온 순서, 중복 제거. 100개 상한은 남긴 항목만 셈
- 결정(사용자, SPEC F2 반영): 담당자·기한은 공백 뺀 2자 이상만, 담당자는 근거 발언 화자 이름 전체 일치 또는 내용 부분 일치, 기한은 내용에서만. 근거 발언 안의 다른 사람 이름은 코드로 못 가려 F3·사람 확인에 맡김
- reviewer 반영: 부분 일치가 느슨함(중간, 위 결정), 낮음 5건(큰 정수·3.0·null 근거, 상한 세는 방식, 값 쪽 공백 정규화, 근거 없는 TODO·객체 아닌 원소, text 앞뒤 공백을 Whitespace.strip으로)

### W4. 덩어리 나누기와 정리 실행 ✅ 완료 (b79780d)
- `ScriptChunker`(6,000자, 발언 경계, 넘는 발언은 잘라 같은 번호), `SummarizeService`(덩어리별 호출 → 검증 → 이어 붙이기, 형식 오류만 재시도 2회)
- 로그: 재시도 횟수와 실패 사유 종류만 (본문·화자·작업 ID 없음)
- 테스트: chunkBoundariesKeepUtteranceNumbers(덩어리 3개), oversizedUtteranceSplitKeepsNumber, invalidOutputRetriesTwiceThenFails(호출 3회), timeoutFailsWithoutRetry(호출 1회), connectionRefusedFailsWithoutRetry, resultsConcatenatedInChunkOrder, logsHaveOnlyRetryCountAndFailureKind
- 완료 조건(W2에서 넘김): 빌더 → 실제 클라이언트를 거친 요청 본문을 가짜 HTTP 서버로 확인. `response_format` 있음, `tools`·`functions`·`tool_choice` 없음, 발언은 구분자 블록 안에만(시스템 메시지에 발언 없음). 덩어리마다 그 덩어리의 발언만 블록에 들어감
- 결과: ScriptChunker(최소 100자, 넘는 발언은 코드 포인트 단위로 잘라 같은 번호·화자), SummarizeService(형식 오류만 덩어리마다 최대 2회, 실패 시 남은 덩어리 안 부름, 실패 종류 분기는 switch 식), SummaryFailure·SummaryFailedException, `app.summary.chunk-size` 기본 6000. SummarizeRequestBodyTest로 완료 조건 확인(요청별 발언 [1,2,3]/[4,5,6]). 백엔드 269개 통과
- reviewer 반영: 잘린 발언 결과의 근거 번호 유지, LLM_ERROR 로그에 상태 코드만(중간 2건, 테스트 추가), 낮음 3건(switch 식, chunk-size 전달 테스트, 요청별 발언 확인). 요청 조립 예외 변환은 W6으로

### W5. 저장 (마이그레이션 V3)
- `job`에 status·failure 열, `summary_item`(작업·순번·종류·내용·담당자·기한), `summary_evidence`(항목·발언 번호). 모두 FK CASCADE. 표준 SQL만 (ADR 0005)
- 테스트: v3MigrationApplies, statusTransitionsPersisted, resultsDeletedWithJobAfterRetention

### W6. 대기열·작업자와 다시 시도
- `SummaryQueue` 인터페이스와 메모리 구현(작업자 수 `app.summary.workers` 기본 1, 상한 10), 작업 생성 시 자리를 먼저 확보한 뒤 저장(가득 차면 작업을 만들지 않음), 시작 시 `QUEUED`·`RUNNING` → `FAILED(SERVER_RESTART)`
- 확장 경로(ADR 0007): 서버 여러 대는 DB 구현(`SELECT … FOR UPDATE SKIP LOCKED`)으로 교체, 요청 수 제한기는 Redis나 DB로
- `POST /api/jobs/{id}/retry`: `FAILED`만(그 밖 409), 작업 생성과 같은 S4 제한기·대기열 상한
- W4에서 넘김: 요청 조립(`SummaryPromptBuilder`)의 `IllegalStateException` 등 정리 중 예상 밖 예외를 작업 실패로 바꿀지 결정하고 처리 (지금은 `SummaryFailedException`으로 바뀌지 않음)
- W5에서 넘김: `SummaryRepository.saveDone`이 DB 제약 위반 등으로 실패하면(롤백되어 `RUNNING`에 남음) `markFailed`로 작업 실패 처리
- 테스트: workersFollowConfig, queuedRunningDoneLifecycle, queueFullReturns429QueueFullAndCreatesNoJob, concurrentCreationNeverExceedsQueueLimit, retryOnlyForFailed409Otherwise, retryCountsTowardJobLimit, retryRespectsQueueLimit, restartMarksQueuedAndRunningFailed, restartFailedJobCanRetry

### W7. 조회 API
- `GET /api/jobs/{id}` → `{status, failure?, items?}` (발언 본문 없음), 조회 제한기(GET, 분당 120회), 404 통일, 요청·응답 toString에서 값 숨김
- 테스트: statusResponseHasNoUtteranceText, request121InSameMinuteReturns429(고정 시계), malformedUnknownAndDeletedIdSame404, statusPollingDoesNotCountTowardJobLimit, jobIdAndItemsNotInLogs

### W8. 프론트 상태 화면
- `#/jobs/{id}` 해시 라우팅(새 의존성 없음), React Query `refetchInterval` 2초(DONE·FAILED에서 멈춤), 종류별 항목 목록, 근거 없음 표시, 실패 문구와 다시 시도, 입력 화면은 생성 성공 시 이동
- 테스트(가짜 타이머, fireEvent): showsQueuedRunningDoneFailedWithIconAndText, stopsPollingWhenDoneOrFailed, rendersItemsAndSpeakerAsPlainText(`<script>` 포함), unsupportedItemShowsIconAndText, retryButtonOnFailure, queueFullAndRateLimitMessagesDiffer, navigatesToJobAfterCreation, unknownJobShowsNotFound

### W9. eval 준비
- `eval/cases/`에 가상 회의 `script.txt` 초안과 인젝션 케이스. `expected.json`·`planted.json`은 사용자가 작성(훅이 AI 수정을 막음)
- 실행기: 케이스를 실제 llama-server로 정리해 JSON 성공률, 근거 위치 정확도, 처리 시간을 계산. 실제 호출 전 예상 호출 수를 보여주고 확인받는다
- 테스트: 지표 계산 단위 테스트(Fixture 결과로)

## 위험과 확인할 것
- llama-server가 `response_format`(JSON 스키마)과 `chat_template_kwargs`를 받는지: W1 끝 `@Tag("llm")`로 확인
- Ollama의 OpenAI 호환 엔드포인트가 `response_format` JSON 스키마를 받는지: 비교할 때 확인, 안 되면 한계로 기록
- 덩어리 6,000자와 `-c 16384`가 노트북에서 5분 안에 끝나는지: 실측으로 조정
- 주의할 점(W4 security-checker): `SummarizeService`는 실행기 오류(`LLM_ERROR`) 때 `LlmCallException` 메시지를 WARN 로그에 남긴다. 지금은 상태 코드만 담은 고정 문구지만, `OpenAiCompatibleLlmClient` 예외 메시지를 바꿀 때(응답 본문 추가 등) 로그 노출을 다시 확인한다

## F2 완료 후
- /docs-sync F2 (README 실행 명령 실측 반영, ARCHITECTURE에 llm 패키지와 정리 흐름 시퀀스)
- /eval로 Qwen3-8B와 Kanana 1.5 8B 비교, 덩어리 크기·`-c` 조정
- Claude 구현과 하루 호출 상한은 eval 비교 단계에서 결정
- 공개 전 예시·테스트 화자 이름을 가상 표기(화자1 등)로 바꿀지 검토 (SPEC 예시와 F1 테스트의 김민수·이영희·박준호 등, security-checker 참고 의견)
