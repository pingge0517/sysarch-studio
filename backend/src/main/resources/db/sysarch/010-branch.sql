create table branch (
  id           varchar(64) primary key,
  project_id   varchar(64) not null,
  name         varchar(200) not null,
  base_baseline varchar(64) not null references baseline(id),
  created_by   varchar(128) not null,
  created_at   timestamptz not null default now(),
  status       varchar(16) not null default 'OPEN',  -- OPEN / MERGED / ABANDONED
  unique (project_id, name)
);

create table changeset (
  id          varchar(64) primary key,
  branch_id   varchar(64) not null references branch(id) on delete cascade,
  element_id  varchar(64) not null,
  op          varchar(16) not null,   -- ADD / MODIFY / DELETE
  payload     jsonb not null,
  created_at  timestamptz not null default now()
);
