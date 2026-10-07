-- 표준 SQL만 사용한다 (배포 시 PostgreSQL로 옮길 수 있게, ADR 0005)

CREATE TABLE job (
    id          CHAR(36)                 NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_job PRIMARY KEY (id)
);

CREATE INDEX idx_job_created_at ON job (created_at);

CREATE TABLE job_utterance (
    job_id        CHAR(36)       NOT NULL,
    utterance_no  INTEGER        NOT NULL,
    speaker       VARCHAR(100)   NOT NULL,
    content       VARCHAR(50000) NOT NULL,
    CONSTRAINT pk_job_utterance PRIMARY KEY (job_id, utterance_no),
    CONSTRAINT fk_job_utterance_job FOREIGN KEY (job_id) REFERENCES job (id) ON DELETE CASCADE
);
