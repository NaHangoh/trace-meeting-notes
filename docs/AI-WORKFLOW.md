# AI 활용 기록

AI가 만든 결과를 어떻게 확인하고 고쳤는지 기록한다. `/ai-log`로 추가한다.

## 사용 도구
- Claude Code: 설계 조사, 구현, 테스트 작성, 리뷰(서브에이전트)
- 제품 LLM: Ollama 로컬 모델(개발), Claude API(정확도 측정·시연)

## 작업 기록
| 날짜 | 작업 | AI가 한 것 | 내가 확인·수정한 것 | 검증 방법 |
|---|---|---|---|---|
| 2026-10-07 | W1 발언 분할기(ScriptSplitter) | 테스트 먼저 작성 후 구현, reviewer 서브에이전트 리뷰, 정확성 문제 2건(공백 기준 불일치, 보충 평면 숫자) 수정 | 리뷰에서 넘긴 SPEC 모호 항목 5건 결정: 규칙 (c) 콜론 앞 단어 중 하나라도 머리말이면 화자 아님, 화자 이름 연속 공백 합치기, 규칙 (b) 글자 없으면 화자 아님, 규칙 (a) 콜론 앞뒤가 모두 숫자면 화자 아님, 경계 테스트 4건 추가. SPEC F1 분할 규칙·수용 기준에 반영 | 실패 테스트 확인 후 구현, `./gradlew test` 36개 통과 |
| 2026-10-07 | W3 공통 오류 응답과 413 필터 | 테스트 먼저 작성 후 구현, reviewer 리뷰, 405 Allow 헤더 누락 수정 | 리뷰에서 넘긴 항목 결정: 3MB 요청 테스트 추가(413 또는 연결 종료, 컨트롤러 미호출), SPEC S3에 200KB = 204,800바이트 명시, 발언 0개는 W4, 500 응답에 예외 메시지·클래스 이름·스택 트레이스가 없는지 테스트 추가 | 실패 테스트 확인 후 구현, 500 테스트는 응답에 예외를 넣는 변형으로 실패를 확인, `./gradlew test` 57개 통과 |
| 2026-10-08 | W4 미리보기 API와 설정값 검증 | 테스트 먼저 작성 후 구현(PreviewController·PreviewService, AppProperties 범위·일관성 검증, JSON 여유분 1,024바이트), reviewer 리뷰, Spring 웹 DEBUG 로그로 입력이 새는 경로 3곳 수정(요청 toString, 응답 toString의 화자 이름, 깨진 JSON의 Jackson 오류 메시지) | 깨진 JSON 로그 처리 방식으로 공통 변환기를 선택(로그 수준 고정은 DEBUG를 켜면 다시 새서 제외) | 실패 테스트 16개 확인 후 구현, 세 수정 각각을 빼면 `doesNotLogInputBody`가 실패함을 확인, 재리뷰 후 변환기 구성 테스트 추가, `./gradlew test` 97개 통과 |
| 2026-10-08 | W6 작업 생성, 작업 생성 요청 수 제한, 보관 기간 삭제 | 테스트 먼저 작성 후 구현(ScriptParser 공용화, JdbcTemplate 저장소 batch insert, UUID v4, 시간당 제한기, 보관 기간 스케줄 삭제), reviewer 리뷰, 정확성 문제 2건(보충 평면 문자 줄 저장 500, H2 trace 파일에 본문 기록) 수정, created_at·트랜잭션 롤백 테스트 추가 | content 길이: CLOB이나 100000 대신 VARCHAR(2000000) 선택. H2 trace 파일 끄기(TRACE_LEVEL_FILE=0) 결정. 빈 파일 발견 후 커밋 전에 복원·재검증 지시, 스크립트로 파일 쓰기 금지 규칙 추가 | 실패 테스트 12개 확인 후 구현, 리뷰 반영분은 수정 전 실패 확인, 복원 후 V2·toString 2곳·저장 시각·작업 생성 인터셉터를 되돌리면 해당 테스트 6개가 실패함을 다시 확인, `./gradlew test` 128개 통과 |

## AI가 틀렸던 사례
| 날짜 | 상황 | AI 결과 | 어떻게 발견했나 | 조치 |
|---|---|---|---|---|
| 2026-10-07 | W1 분할기: 공백 기준 | 앞뒤 공백 제거(`strip`)는 전각 공백(U+3000)을 공백으로 보고, 단어 수 세기(`split("\\s+")`)는 공백으로 보지 않음. `김민수\u3000팀장\u3000님:`이 1단어로 세져 화자로 인정됨 | reviewer 서브에이전트 리뷰 | 단어 나누기를 `Character.isWhitespace` 기준으로 통일, 테스트 `ideographicSpaceCountsAsWordSeparator` 추가 |
| 2026-10-07 | W1 분할기: 숫자 판정 | 숫자 판정을 UTF-16 단위(`charAt`, `chars()`)로 해서 보충 평면 숫자(예: 𝟐𝟎)를 숫자로 보지 못함 | reviewer 서브에이전트 리뷰 | 코드 포인트 단위(`codePointAt`, `codePoints()`)로 변경, 테스트 `supplementaryPlaneDigitsAreDigits` 추가 |
| 2026-10-07 | W1 완료 보고: 테스트 개수 | W1 보고에서 테스트 개수를 "계획 목록 12개 중 11개"로 잘못 보고. 매개변수화 테스트를 실행 8회로만 세고 계획 항목 1개로 세지 않음 | 사용자가 계산으로 확인해 정정 | 테스트 이름별 존재 여부와 테스트 결과 XML의 testcase 목록으로 12개 모두 있음을 확인 |
| 2026-10-07 | W1·W2 테스트: 보이지 않는 문자 | 테스트에 쓴 `\u00A0`·`\u3000` 이스케이프가 편집 도구를 거치며 실제 문자(NBSP, 전각 공백)로 저장됨 (W1·W2 테스트 11곳) | security-checker가 보이지 않는 문자를 지적, 바이트 덤프(`od -c`)로 확인 | 이스케이프로 복원, 실제 문자 0개 확인, 동작 변화 없음(테스트 49개 통과) |
| 2026-10-07 | W3 공통 오류 처리: 405 헤더 | 마지막 예외 처리기가 상태 코드만 유지하고 `ErrorResponse`의 헤더를 버려 405 응답에 `Allow` 헤더가 빠짐 | reviewer 서브에이전트 리뷰 | `errorResponse.getHeaders()`를 응답에 넘김, 테스트 `methodNotAllowedKeepsStatusAndAllowHeader` 추가 |
| 2026-10-07 | W3 설정: YAML 주석 | application.yml 주석에 쓴 `\n`이 편집 도구를 거치며 실제 줄바꿈으로 저장되어 YAML이 깨지고 컨텍스트 로딩 실패 (보이지 않는 문자 이스케이프 문제와 같은 원인) | 테스트 실행 시 컨텍스트 로딩 실패로 발견 | 주석 문구를 이스케이프 없이 다시 씀 |
| 2026-10-08 | W4 미리보기: 응답 로그 | 요청 객체 toString만 막고 응답 객체(`Preview`)는 그대로 둠. Spring 웹 DEBUG 로그에 입력에서 온 화자 이름이 남음. 로그 테스트도 표식을 발언 내용 자리에만 넣어 잡지 못함 | reviewer 서브에이전트 리뷰, 화자 자리에 표식을 넣은 테스트로 재현 | `Preview.toString`에 개수만 남김, 테스트에 화자 자리 표식 추가 |
| 2026-10-08 | W4 미리보기: 깨진 JSON 로그 | Jackson 오류 메시지("Unrecognized token '...'")에 든 입력 토큰이 DEBUG 로그에 남는 경로를 놓침. 첫 재현 테스트는 한글 표식이라 UTF-8 해석 단계에서 먼저 실패해 이 경로를 시험하지 못함 | reviewer 지적, ASCII 표식 테스트로 재현 | 공통 변환기(`JsonConfig`)가 읽기 오류를 원인·입력 없이 다시 던짐 |
| 2026-10-08 | W4 공통 JSON 변환기: 등록 방식 | 조용한 JSON 변환기를 `JacksonJsonHttpMessageConverter` 빈으로 등록함. Boot 기본 변환기를 대체하지 못하고 맨 앞 커스텀 변환기로 끼어들어, Jackson 변환기가 둘이 되고 String 요청·응답이 JSON으로 처리됨(클라이언트 쪽 변환기에도 들어감). 로그 테스트만으로는 드러나지 않음 | reviewer 재리뷰(변환기 목록 실측, String 엔드포인트로 재현) | 서버 커스터마이저(`ServerHttpMessageConvertersCustomizer`)에서 JSON 자리만 교체, 테스트 `JsonConfigTest` 2개(변환기 하나, String 변환기 뒤) 추가 |
| 2026-10-08 | W6 작업 생성: 길이 단위 | 글자 수 상한은 코드 포인트로 세는데 `job_utterance.content`는 `VARCHAR(50000)`이고 H2는 UTF-16 단위로 셈. 보충 평면 문자 25,001개 넘는 한 줄은 검사를 통과하고 저장에서 500 | reviewer 리뷰(H2에서 직접 재현) | V2 마이그레이션으로 `VARCHAR(2000000)`, 테스트 `storesMaxLengthLineOfSupplementaryCharacters` 추가 |
| 2026-10-08 | W6 작업 생성: H2 trace 파일 | H2 파일 DB가 기본 설정에서 SQL 오류와 실패한 값(본문)을 `trace.db`에 남기는 경로를 놓침. 이 파일은 보관 기간 삭제 대상이 아님 | reviewer 리뷰(파일 DB에서 표식이 남는 것을 확인) | 데이터소스 URL에 `TRACE_LEVEL_FILE=0`, ADR 0005 기록, 테스트 `H2TraceFileDisabledTest` 추가 |
| 2026-10-08 | W6 테스트: 중첩 @Configuration | `@SpringBootTest` 클래스 안에 `@Configuration` 중첩 클래스를 둬서 Spring이 애플리케이션 대신 그것을 설정으로 씀. 컨트롤러가 없어 404 | 테스트 실행 시 404로 발견, 요청 URI로 원인 확인 | 설정값 테스트를 별도 클래스(`RateLimitPropertiesTest`)로 옮김 |
| 2026-10-08 | W6 테스트: 스크립트로 파일 쓰기 | Python으로 테스트 파일을 고치다 `\u` 이스케이프 인코딩 오류로 중단되어 `JobControllerTest.java`가 빈 파일이 됨. 편집 도구를 거치지 않아 훅 검사도 우회됨 | 다음 편집 시도에서 파일이 비어 있음을 발견 | 편집 도구로 다시 작성, 스크립트로 쓴 W6 파일들도 편집 도구로 다시 씀, 크기·@Test 개수·보이지 않는 문자 확인, 핵심 테스트의 수정 전 실패 재확인. 규칙 추가 제안 |
