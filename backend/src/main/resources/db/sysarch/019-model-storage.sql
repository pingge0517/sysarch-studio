-- Registration only: no model snapshot, credentials or remote-write capability is stored here.
create table model_storage_registration (
    sirius_project varchar(64) primary key,
    model_uuid varchar(36) not null unique,
    storage_mode varchar(16) not null default 'LOCAL',
    created_by varchar(128) not null,
    created_at timestamp with time zone not null default CURRENT_TIMESTAMP,
    constraint ck_model_storage_mode check (storage_mode in ('LOCAL', 'POLARION'))
);
