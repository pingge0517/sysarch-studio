create table baseline (
  id            varchar(64) primary key,
  project_id    varchar(64) not null,
  label         varchar(200) not null,
  description   text,
  model_rev     integer not null,
  element_count integer not null default 0,
  created_by    varchar(128) not null,
  created_at    timestamptz not null default now()
);
create index idx_baseline_project on baseline(project_id, created_at desc);

-- 基线只存元素快照的引用与关键字段，不做全量拷贝
create table baseline_element (
  baseline_id  varchar(64) not null references baseline(id) on delete cascade,
  element_id   varchar(64) not null,
  element_type varchar(64) not null,
  name         varchar(500),
  layer        varchar(32),
  attributes   jsonb not null default '{}',
  primary key (baseline_id, element_id)
);
