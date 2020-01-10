select current_database(), current_schema(), current_user;

drop table log_table;
drop table log_instances;

create table log_instances (
  start_log_id bigserial not null
  , name varchar(100) not null
  , start_ts timestamp(6) not null
  , end_ts timestamp(6)
  , status varchar(1) not null
  , constraint log_instances_pk primary key(start_log_id)
  , constraint log_instances_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/)));

create index log_instances_name_idx on log_instances(name);

create table log_table (
  action_name varchar(64) not null,
  log_id  bigserial not null,
  parent_log_id bigint,
  start_ts timestamp(6) not null,
  end_ts timestamp(6),
  status varchar(1) not null,
  row_count bigint,
  comments varchar(4000),
  exception_message varchar(4000),
  large_text text,
  constraint log_table_pk primary key (log_id),
  constraint log_table_status_chck check (status in ('C'/*completed*/, 'R'/*running*/, 'F'/*failed*/)),
  constraint log_table_cild_parent_fk foreign key (parent_log_id) references log_table(log_id)
);

create index log_table_parent_id_idx on log_table(parent_log_id);

create index log_table_action_name_idx on log_table(action_name);

create table t (id int);

select * from t;