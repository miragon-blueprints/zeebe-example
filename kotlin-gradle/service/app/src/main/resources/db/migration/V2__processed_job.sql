CREATE TABLE processed_job (
    job_key       BIGINT       NOT NULL,
    processed_at  TIMESTAMP(6) NOT NULL,
    variables     TEXT,
    error_code    VARCHAR(255),
    error_message TEXT,
    CONSTRAINT pk_processed_job PRIMARY KEY (job_key)
);
