create table edit_lock (
  id            varchar(64) primary key,
  project_id    varchar(64) not null,
  element_id    varchar(64) not null,
  owner_user    varchar(128) not null,
  lease_seconds integer not null default 90,
  acquired_at   timestamptz not null default now(),
  expires_at    timestamptz not null,
  unique (project_id, element_id)
);
create index idx_lock_expire on edit_lock(expires_at);

create table edit_session (
  id          varchar(64) primary key,
  project_id  varchar(64) not null,
  user_id     varchar(128) not null,
  opened_at   timestamptz not null default now(),
  closed_at   timestamptz,
  client_info varchar(200)
);

-- 回收任务：每分钟清理过期锁
