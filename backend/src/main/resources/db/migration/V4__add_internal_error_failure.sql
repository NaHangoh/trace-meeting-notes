-- 표준 SQL만 사용한다 (ADR 0005). SPEC F2 실패 처리: 예상 밖 서버 오류(결과 저장 실패 등)를 INTERNAL_ERROR로 저장한다.

ALTER TABLE job DROP CONSTRAINT ck_job_failure;

-- 실패 사유는 FAILED일 때만, FAILED이면 반드시 있다.
-- IS NOT NULL이 없으면 NULL IN (...)이 UNKNOWN이 되고, CHECK는 UNKNOWN을 통과시킨다
ALTER TABLE job ADD CONSTRAINT ck_job_failure
    CHECK ((status = 'FAILED' AND failure IS NOT NULL AND failure IN ('INVALID_OUTPUT', 'TIMEOUT', 'CONNECTION_REFUSED',
                                                                      'LLM_ERROR', 'SERVER_RESTART', 'INTERNAL_ERROR'))
        OR (status <> 'FAILED' AND failure IS NULL));
