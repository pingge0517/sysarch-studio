create table project_binding (
  polarion_project varchar(255) primary key,
  sirius_project   varchar(64)  not null,
  created_by       varchar(128) not null,
  created_at       timestamptz  not null default now(),
  updated_by       varchar(128),
  updated_at       timestamptz
);
create index idx_project_binding_sirius on project_binding(sirius_project);
