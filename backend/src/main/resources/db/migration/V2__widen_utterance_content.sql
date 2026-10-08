-- 표준 SQL만 사용한다 (ADR 0005).
-- 글자 수 상한은 코드 포인트로 세지만(SPEC S3) H2의 VARCHAR 길이는 UTF-16 단위다.
-- 설정 상한(1,000,000자)을 모두 보충 평면 문자로 채워도 들어가도록 2,000,000으로 늘린다.

ALTER TABLE job_utterance ALTER COLUMN content SET DATA TYPE VARCHAR(2000000);
