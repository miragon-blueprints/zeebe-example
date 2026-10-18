CREATE TABLE process_message_outbox (
    id              UUID         NOT NULL,
    message_name    VARCHAR(255) NOT NULL,
    correlation_key VARCHAR(255) NOT NULL,
    variables       TEXT         NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_process_message_outbox PRIMARY KEY (id)
);
