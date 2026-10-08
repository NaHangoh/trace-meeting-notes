# F1 스크립트 입력 구현 계획

- 기준 문서: docs/SPEC.md F1, S1~S4
- 진행 방식: 작업 단위(W)마다 실패 테스트 → 구현 → 전체 테스트 → reviewer → /commit(security-checker 포함). 단위가 끝나면 멈추고 사용자 확인 후 다음 단위.

## 확정된 결정

| 항목 | 결정 | 기록 |
|---|---|---|
| JDK | 시스템 기본은 17, 이 프로젝트는 Gradle 툴체인으로 Java 21(Temurin). JDK 자동 다운로드 끔 | |
| 백엔드 | Spring Boot 4.1.x (W0 기준 4.1.1), Gradle 9.8.0 wrapper + sha256 검증 | ADR 0001 |
| Spring AI | F2에서 2.x 추가 (Boot 4.0/4.1 기반) | ADR 0001 |
| DB | H2 파일 모드(./data/) + Flyway. 표준 SQL만. H2 콘솔 모듈 미포함, 설정 false | ADR 0005 |
| 프론트 | React 19, TypeScript 5.x, Vite 8, Tailwind 4, Vitest, React Query. Node >=22.22.2 | |
| 글자 수 | 줄바꿈을 `\n`으로 정규화한 뒤 유니코드 코드 포인트 수. 상한 50,000자(설정값) | S3 |
| 요청 본문 상한 | 200KB(설정값), 초과 시 413 | S3 |
| 미리보기 요청 수 | IP당 분당 60회, 초과 시 429 | S4 |
| 작업 생성 요청 수 | IP당 시간당 20회(개발 프로필 200회), 초과 시 429. 미리보기와 별도 설정 | S4 |
| 요청 수 제한 방식 | 서버 메모리, 고정 1분/1시간 창, getRemoteAddr만 사용, 서버 한 대 전제 | ADR 0006 |
| 보관 기간 | 24시간(설정값), 스케줄 작업으로 삭제 | S2 |
| 작업 ID | UUID v4 | S1 |
| 화자 구분자 | 반각 콜론(`:`)만. 전각 `：`는 범위 밖 | |
| 응답 코드 | 빈 입력·글자 수 초과·NUL → 400, 바이트 초과 → 413, 요청 수 초과 → 429 | |

## 분할 규칙 요약 (기준은 SPEC F1)

- 줄마다 발언 번호를 따로 붙인다. 빈 줄·공백만 있는 줄은 번호 없음.
- 화자 없는 줄은 앞 화자를 이어받는다. 이어받을 화자가 없으면 "미상".
- 화자만 있고 내용이 빈 줄은 번호 없이 이후 줄의 화자만 바꾼다.
- 화자 인정: 줄 맨 앞에서 첫 콜론까지 1~20자, 공백 기준 2단어 이하. 다만 아래는 화자 아님.
  - (a) 콜론 바로 앞 글자와 바로 뒤 글자가 모두 숫자 (예: "시간은 10:30으로")
  - (b) 콜론 앞에 글자(Character.isLetter)가 하나도 없음 (예: "10.5:", "2024 1:")
  - (c) 콜론 앞 단어 중 하나라도 머리말 목록에 있음 (예: "결정 사항:", "회의 요약:")
- 화자 이름의 연속 공백(전각 포함)은 한 칸으로 합친다. 숫자·글자 판정은 코드 포인트 단위.

## 작업 단위

### W0. 골격과 의존성 ✅ 완료 (1a3db2d)
- 테스트: ApplicationTests.contextLoads, H2ConsoleDisabledTest(2), FlywayMigrationTest(3), smoke.test.ts, textDecoderEucKr.test.ts(4)

### W1. ScriptSplitter ✅ 완료 (c2a2f96)
- 패키지 tracenotes.minutes: ScriptSplitter, Utterance, HeaderWords (순수 Java)
- 테스트: splitsEachLineWithSequentialNumbers, continuationLineInheritsSpeaker, firstLineWithoutSpeakerIsUnknown, speakerOnlyLineSetsSpeakerWithoutNumber, colonFollowedByDigitIsNotSpeaker, digitsOnlyPrefixIsNotSpeaker, headerWordIsNotSpeaker(매개변수화), prefixOver20CharsIsNotSpeaker, prefixOver2WordsIsNotSpeaker, twoWordSpeakerIsRecognized, handlesCrlf, speakersInFirstAppearanceOrderWithUnknownLast, headerWordsMatchSpec, ideographicSpaceCountsAsWordSeparator, supplementaryPlaneDigitsAreDigits, digitAfterColonWithLetterBeforeIsSpeaker, prefixWithLetterAndDigitIsSpeaker, headerWordAnywhereInPrefixIsNotSpeaker, consecutiveSpacesInSpeakerAreCollapsed, leadingWhitespaceBeforeSpeakerIsAllowed, colonAtLineStartIsNotSpeaker, whitespaceOnlyAfterColonSetsSpeakerWithoutNumber, speakerOnlyLastLineCreatesNoUtterance
- reviewer 반영: 공백 기준 불일치(전각 공백), 보충 평면 숫자 판정

### W2. ScriptValidator ✅ 완료 (2c7cbe5)
- 패키지 tracenotes.minutes: ScriptValidator, InvalidScriptException(사유 BLANK, TOO_LONG, CONTAINS_NUL, NO_UTTERANCES), Whitespace(검사·분할 공통 공백 기준) (순수 Java)
- 테스트: rejectsBlank(매개변수화, NBSP 포함), rejectsNullAsBlank, rejectsOverMaxLength, acceptsExactlyMaxLength, rejectsNul, countsCodePointsAfterCrlfNormalization, exceptionMessageDoesNotContainInput(NUL·TOO_LONG), ScriptSplitterTest.nbspOnlyLineGetsNoNumber
- reviewer 후 결정: 발언 0개는 400(NO_UTTERANCES, 검사는 W4·W6 서비스에서 분할 뒤), 공백 기준을 Whitespace로 통일(NBSP 포함)

### W3. 공통 오류 응답과 413 필터 ✅ 완료 (ed8517a)
- common: ApiError, ApiExceptionHandler, RequestSizeLimitFilter, AppProperties, CorsConfig (+ @ConfigurationPropertiesScan, app.* 설정)
- 테스트(RequestLimitAndErrorTest, 시험용 ProbeController): bodyOver200KbReturns413WithContentLength, bodyOver200KbReturns413WhenChunked, body3MbIsRejectedBeforeController, bodyOfExactly200KbIsAccepted, errorResponseDoesNotEchoInput, unhandledExceptionReturns500WithoutDetails, methodNotAllowedKeepsStatusAndAllowHeader, corsAllowsOnlyConfiguredOrigin
- 200KB = 204,800바이트 (SPEC S3). W7 프론트도 같은 값
- 3MB 요청 실제 동작: Content-Length·chunked 모두 413, 컨트롤러 호출 0회
- reviewer 반영: 405 응답의 Allow 헤더 유지
- 후속(W3 커밋 뒤): CORS 허용 주소에 `*`가 있으면 시작 실패(AppProperties), 테스트 AppPropertiesTest(corsWildcardFailsStartup, explicitOriginsStartNormally). 3MB 테스트는 필터를 끄면 실패함을 확인

### W4. 미리보기 API ✅ 완료 (ff46260)
- 결과: `POST /api/preview` (`{"text"}` → `{utteranceCount, speakers}`), PreviewService, AppProperties 범위·일관성 검증(JSON 여유분 1,024바이트), JsonConfig(서버 JSON 변환기 교체), 테스트 PreviewControllerTest 8 · AppPropertiesTest 추가분 · JsonConfigTest 2, 전체 97개 통과
- reviewer 반영: 요청·응답 toString에 본문·화자 이름 제외, 깨진 JSON의 Jackson 오류 메시지가 DEBUG 로그에 남지 않게 공통 변환기(사용자 결정), 변환기를 빈이 아닌 서버 커스터마이저로 JSON 자리만 교체
- 알려진 한계: 바이트 상한 일관성 검사는 JSON 이스케이프로 늘어나는 바이트(`\r\n`, 제어 문자)를 넣지 않음
- PreviewController: `{ utteranceCount, speakers }`만 반환, 저장·본문 로그 없음
- 테스트: returnsCountAndSpeakersOnly, blankReturns400, overLengthReturns400, nulReturns400, noUtterancesReturns400, doesNotPersistAnything, doesNotLogInputBody
- 설정값 검증(AppProperties): invalidLimitsFailStartup(매개변수화) — maxScriptChars는 1~1,000,000, maxRequestBytes는 1~10,485,760(10MB)을 벗어나면(0·음수·초과) 시작 실패
- 설정값 검증(AppProperties): limitsAreConsistent — maxRequestBytes가 maxScriptChars × 4 + 여유분(JSON 감싸는 부분)보다 작으면 시작 실패. 기본값 50,000자·204,800바이트는 통과해야 한다. 여유분 값은 구현 때 정해 보고한다

### W5. IpRateLimiter와 미리보기 제한
- common: IpRateLimiter, ClockConfig(시계 Bean)
- 테스트: 61stRequestInSameMinuteReturns429, resetsAfterWindow(시계 주입), separateLimitPerIp, previewAndJobLimitsAreIndependent
- ADR 0006 작성

### W6. 작업 생성, 작업 생성 요청 수 제한, 보관 기간 삭제
- JobController, JobService, Job, JobUtterance, 저장소, RetentionCleanupJob
- 작업 생성: createsJobWithUuidV4AndStoresUtterances, previewMatchesJobSplit, createJobRejectsBlankOverLengthNul, createJobRejectsNoUtterances
- 요청 수 제한(S4): jobCreationOverLimitReturns429, jobLimitUsesSeparateConfig
- 보관 기간(S2): deletesJobsAndUtterancesOlderThanRetention(24시간 1분), keepsJobsWithinRetention(23시간 59분), retentionIsConfigurable. 스케줄 메서드를 직접 호출해 테스트
- 로그에는 작업 ID 대신 경로 패턴만 남긴다 (작업 ID가 곧 열람 권한). 테스트: jobIdNotInLogs
- 요청·응답 객체의 toString에 본문·화자 이름·작업 ID를 넣지 않는다 (Spring 웹 DEBUG 로그가 toString으로 남김, W4와 같은 처리). 테스트: jobCreationDoesNotLogInputBody — 웹 DEBUG 로그를 켜고 발언 내용·화자 자리 표식, 깨진 JSON(ASCII 표식)이 로그에 없는지 확인
- 정리(LLM) 시작은 F2에서 붙인다

### W7. 파일 읽기 (프론트)
- src/features/input/readScriptFile.ts, limits.ts
- 인코딩: TextDecoder('utf-8', {fatal:true}) → 실패 시 TextDecoder('euc-kr', {fatal:true}) → 둘 다 실패 시 "읽을 수 없는 인코딩"
- NUL 검사는 디코딩 전 바이트 단계
- 테스트: decodesUtf8, stripsUtf8Bom, decodesCp949SameAsUtf8, rejectsUndecodableBytes, rejectsNonTxt, rejectsOver200Kb, rejectsNulBytes

### W8. 입력 화면 (프론트)
- ScriptInputPage.tsx, usePreview.ts(React Query, 500ms 디바운스), api/client.ts
- 테스트: startDisabledWithGuidanceWhenBlank, callsPreviewOnceAfter500msIdle, showsSummaryLine, overLimitShowsCountAndBlocksStart, filePickFillsTextarea, fileErrorShowsReason, jobCreation429ShowsRetryMessage
- 미리보기 API가 400·429를 돌려주면 요약 줄을 숨긴다
- 빈 입력 최종 판정은 미리보기 응답 기준이다 (프론트 trim()과 서버 공백 기준이 다를 수 있음)

## F1 완료 후
- /docs-sync F1 실행 (README, ARCHITECTURE, SPEC 체크박스를 코드·테스트에 맞춤)
- 배포 시 CORS 주소를 프로필·환경 변수로 지정 (기본값 http://localhost:5173은 개발용)

### 배포 시 점검 (CORS 설정을 잘못 쓴 경우, 시작 검증으로 잡히지 않음)
- 오타·잘못된 포트·http/https 혼동: 실제 프론트가 막히는데 시작은 정상으로 된다
- 출처 형식이 아닌 값(`localhost:5173`, `http://host/path`): 아무것과도 일치하지 않아 실수를 알아채기 어렵다
- 운영 설정에 http 주소나 공유·제3자 도메인(호스팅 하위 도메인 등)이 남지 않았는지
- 같은 주소의 중복(`http://a`와 `http://a/`)
- 비ASCII·punycode로 만든 비슷한 도메인, 값 안의 제어 문자
- `app.cors-allowed-origins=`처럼 빈 값: 빈 목록(허용 없음)이 되거나 시작 실패
- 환경 변수 `APP_CORS_ALLOWED_ORIGINS`나 프로필별 yml이 기대한 값을 덮어쓰지 않았는지
- 신뢰 프록시 설정과 X-Forwarded-For 처리 (ADR 0006)
- IPv6는 주소 대신 /64 대역 단위로 세는 방식 검토 (ADR 0006)

- 클라우드 리뷰 1회 (명령 이름은 그때 확인. 분할 규칙, 요청 수 제한, 보관 기간 삭제 집중)
- F2 계획 시 로컬 LLM 실행기 결정: OpenAI 호환 로컬 클라이언트 하나로 llama.cpp(Vulkan)와 Ollama를 설정으로 바꿔 끼우고 eval로 속도·근거 탐지율 비교 (ADR 0002 갱신)
