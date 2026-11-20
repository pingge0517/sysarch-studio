-- SysMLTool coordination metadata only. A fresh or rebuilt database MUST start locked.
create table model_command_coordinator (
    singleton integer primary key check (singleton = 1),
    state varchar(24) not null check (state in ('LOCKED', 'READY', 'RECOVERY_REQUIRED')),
    epoch varchar(36),
    constraint ck_command_epoch check (state <> 'READY' or epoch is not null)
);
insert into model_command_coordinator(singleton, state, epoch) values (1, 'LOCKED', null);

create table model_command_gate (
    model_uuid varchar(36) primary key,
    attempt_id varchar(36) not null unique,
    command_id varchar(36) not null,
    request_hash varchar(64) not null,
    actor varchar(128) not null,
    coordinator_epoch varchar(36) not null,
    state varchar(16) not null check (state in ('ACTIVE', 'UNKNOWN')),
    created_at timestamp with time zone not null default CURRENT_TIMESTAMP
);
