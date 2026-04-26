create table analysis_tasks
(
    id                  bigserial
        constraint pk_analysis_tasks_id
        primary key,
    created_time_stamp  bigint not null,
    created_by_id       bigint not null,
    modified_time_stamp bigint not null,
    modified_by_id      bigint not null,
    user_id             bigint not null,
    original_query      text   not null,
    generated_sql       text,
    parameters          jsonb,
    status              text   not null
        constraint analysis_tasks_status_check
            check (status = ANY (ARRAY ['PENDING'::text, 'RUNNING'::text, 'SUCCESS'::text, 'FAILED'::text])),
    result              jsonb,
    execution_time      bigint,
    error_message       text,
    name                text
);

alter table analysis_tasks
    owner to phj233;

create table data_source
(
    id                  bigserial
        constraint pk_data_source_id
        primary key,
    created_time_stamp  bigint  not null,
    created_by_id       bigint  not null,
    modified_time_stamp bigint  not null,
    modified_by_id      bigint  not null,
    user_id             bigint  not null,
    name                text    not null,
    type                text    not null
        constraint data_source_type_check
            check (type = ANY (ARRAY ['MYSQL'::text, 'POSTGRESQL'::text, 'EXCEL'::text, 'CSV'::text])),
    connection_config   jsonb   not null,
    schema_info         jsonb,
    active              boolean not null
);

alter table data_source
    owner to phj233;

create table role
(
    id   bigint not null
        constraint pk_role_id
            primary key,
    name text   not null
);

alter table role
    owner to phj233;

create table "user"
(
    id                  bigserial
        constraint pk_user_id
        primary key,
    created_time_stamp  bigint  not null,
    created_by_id       bigint
        constraint fk_user_created_by
            references "user",
    modified_time_stamp bigint  not null,
    modified_by_id      bigint
        constraint fk_user_modified_by
            references "user",
    username            text    not null,
    password            text    not null,
    email               text    not null
        constraint uk_user_default
            unique,
    enabled             boolean not null,
    avatar              text
);

alter table "user"
    owner to phj233;

create table user_role_mapping
(
    user_id bigint not null
        constraint fk_user_role_mapping_user
            references "user",
    role_id bigint not null
        constraint fk_user_role_mapping_role
            references role,
    constraint pk_user_role_mapping
        primary key (user_id, role_id)
);

alter table user_role_mapping
    owner to phj233;

