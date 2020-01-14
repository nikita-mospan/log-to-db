select current_database(), current_schema(), current_user;

drop table log_table;
drop table log_instances;

create table log_instances
(
    start_log_id bigserial    not null,
    name         varchar(100) not null,
    start_ts     timestamp(6) not null,
    end_ts       timestamp(6),
    status       varchar(1)   not null,
    constraint log_instances_pk primary key (start_log_id),
    constraint log_instances_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/))
);

create index log_instances_name_idx on log_instances (name);

create table log_table
(
    action_name       varchar(64)  not null,
    log_id            bigserial    not null,
    parent_log_id     bigint,
    start_ts          timestamp(6) not null,
    end_ts            timestamp(6),
    status            varchar(1)   not null,
    row_count         bigint,
    comments          text,
    exception_message varchar(4000),
    constraint log_table_pk primary key (log_id),
    constraint log_table_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/)),
    constraint log_table_cild_parent_fk foreign key (parent_log_id) references log_table (log_id)
);

create index log_table_parent_id_idx on log_table (parent_log_id);

create index log_table_action_name_idx on log_table (action_name);

WITH RECURSIVE log AS (
    SELECT 1                as level,
           ARRAY [l.log_id] AS path,
           l.log_id,
           l.action_name,
           l.parent_log_id,
           l.start_ts,
           l.end_ts,
           l.status,
           l.row_count,
           l.comments,
           l.exception_message
    FROM log_table l
    WHERE l.log_id = 16801
    UNION ALL
    SELECT l.level + 1 as level,
           path || l1.log_id,
           l1.log_id,
           l1.action_name,
           l1.parent_log_id,
           l1.start_ts,
           l1.end_ts,
           l1.status,
           l1.row_count,
           l1.comments,
           l1.exception_message
    FROM log_table l1
             INNER JOIN log l ON l.log_id = l1.parent_log_id
)
SELECT
       lpad(' ', (l.level - 1) * 2) || l.log_id as log_id,
       l.action_name,
       l.start_ts,
       l.end_ts,
       l.status,
       l.row_count,
       l.comments,
       l.exception_message
FROM log l
order by l.path, l.start_ts;
