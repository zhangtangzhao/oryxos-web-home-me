-- OryxOS SQLite 基础表（幂等建表，替代 hibernate ddl-auto=update）。
-- 为什么不用 update：hibernate 6 对 SQLite 已有表做 schema 提取时，xerial
-- 返回空 COLUMN_DEF → AbstractInformationExtractorImpl 抛 NoSuchElementException，
-- 第二次启动即崩；且 update 对已有表本来就无法演进列（CLAUDE.md 常见陷阱）。
-- 列类型与历史 update 生成物逐列一致，迁移此设置对既有库零影响。

CREATE TABLE IF NOT EXISTS sessions (
    session_id varchar(255) not null,
    profile_name varchar(255) not null,
    channel varchar(255) not null,
    user_id varchar(255) not null,
    messages_json TEXT,
    status varchar(255) not null check (status in ('ACTIVE','ARCHIVED')),
    created_at timestamp not null,
    last_active_at timestamp not null,
    archived_at timestamp,
    primary key (session_id)
);

CREATE TABLE IF NOT EXISTS tool_invocations (
    id integer primary key,
    session_id varchar(255) not null,
    tool_name varchar(255) not null,
    input_json TEXT,
    result_json TEXT,
    success boolean not null,
    error_message TEXT,
    duration_ms bigint,
    created_at timestamp not null
);

CREATE TABLE IF NOT EXISTS llm_calls (
    id integer primary key,
    session_id varchar(255) not null,
    provider varchar(255) not null,
    model varchar(255) not null,
    prompt_tokens integer,
    completion_tokens integer,
    total_tokens integer,
    duration_ms bigint,
    created_at timestamp not null
);

CREATE TABLE IF NOT EXISTS kb_knowledge_bases (
    name varchar(255) not null,
    description TEXT,
    embedding_model varchar(255),
    embedding_dimensions integer,
    created_at timestamp not null,
    updated_at timestamp not null,
    primary key (name)
);

CREATE TABLE IF NOT EXISTS kb_documents (
    id integer primary key,
    kb_name varchar(255) not null,
    doc_path varchar(255) not null,
    content_hash varchar(255),
    size_bytes bigint,
    status varchar(255) not null check (status in ('PENDING','READY','FAILED')),
    error_message TEXT,
    chunk_count integer,
    ingested_at timestamp
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_documents_kb_doc
    ON kb_documents (kb_name, doc_path);

CREATE TABLE IF NOT EXISTS kb_chunks (
    id integer primary key,
    document_id bigint not null,
    kb_name varchar(255) not null,
    chunk_ordinal integer not null,
    heading_path TEXT,
    content TEXT not null,
    embedding TEXT
);

CREATE INDEX IF NOT EXISTS idx_kb_chunks_document ON kb_chunks (document_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunks_kb ON kb_chunks (kb_name);
