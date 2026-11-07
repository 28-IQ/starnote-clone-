create table backups (
  id         text primary key,
  provider   text not null check (provider in ('local','gdrive','onedrive')),
  remote_id  text,
  status     text not null check (status in ('running','done','failed')),
  byte_size  integer,
  error      text,
  created_at integer not null
);
-- ;;
create index backups_created on backups (provider, created_at desc);
-- ;;
create table entitlements (
  sku          text primary key,
  state        text not null check (state in ('active','pending','revoked')),
  acknowledged integer not null default 0 check (acknowledged in (0,1)),
  purchased_at integer,
  checked_at   integer not null
);
