-- 표준 SQL만 사용한다 (ADR 0005). SPEC F2 저장: 작업 상태·실패 사유와 결과 항목.
-- 결과는 작업에 ON DELETE CASCADE로 묶여 보관 기간 삭제(S2) 때 함께 지워진다.

-- 이미 있는 작업(F1에서 만든 것)은 QUEUED가 된다. 대기열에 없으므로 시작 시 SERVER_RESTART로 바뀐다 (W6).
ALTER TABLE job ADD COLUMN status VARCHAR(20) DEFAULT 'QUEUED' NOT NULL;
ALTER TABLE job ADD COLUMN failure VARCHAR(30);

ALTER TABLE job ADD CONSTRAINT ck_job_status
    CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED'));
-- 실패 사유는 FAILED일 때만, FAILED이면 반드시 있다.
-- IS NOT NULL이 없으면 NULL IN (...)이 UNKNOWN이 되고, CHECK는 UNKNOWN을 통과시킨다
ALTER TABLE job ADD CONSTRAINT ck_job_failure
    CHECK ((status = 'FAILED' AND failure IS NOT NULL AND failure IN ('INVALID_OUTPUT', 'TIMEOUT', 'CONNECTION_REFUSED', 'LLM_ERROR',
                                               'SERVER_RESTART'))
        OR (status <> 'FAILED' AND failure IS NULL));

-- 글자 수 상한은 코드 포인트(내용 500자, 담당자·기한 100자), VARCHAR 길이는 UTF-16 단위라 두 배로 둔다
CREATE TABLE summary_item (
    job_id     CHAR(36)      NOT NULL,
    item_no    INTEGER       NOT NULL,
    item_type  VARCHAR(20)   NOT NULL,
    content    VARCHAR(1000) NOT NULL,
    assignee   VARCHAR(200),
    due        VARCHAR(200),
    CONSTRAINT pk_summary_item PRIMARY KEY (job_id, item_no),
    CONSTRAINT fk_summary_item_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE,
    CONSTRAINT ck_summary_item_type CHECK (item_type IN ('AGENDA', 'DISCUSSION', 'DECISION', 'TODO'))
);

-- 근거는 그 작업에 실제로 있는 발언만 가리킨다. 근거가 없는 항목은 행이 없다("근거 없음")
CREATE TABLE summary_evidence (
    job_id          CHAR(36) NOT NULL,
    item_no         INTEGER  NOT NULL,
    evidence_order  INTEGER  NOT NULL,
    utterance_no    INTEGER  NOT NULL,
    CONSTRAINT pk_summary_evidence PRIMARY KEY (job_id, item_no, evidence_order),
    CONSTRAINT fk_summary_evidence_item FOREIGN KEY (job_id, item_no)
        REFERENCES summary_item (job_id, item_no) ON DELETE CASCADE,
    CONSTRAINT fk_summary_evidence_utterance FOREIGN KEY (job_id, utterance_no)
        REFERENCES job_utterance (job_id, utterance_no) ON DELETE CASCADE
);
