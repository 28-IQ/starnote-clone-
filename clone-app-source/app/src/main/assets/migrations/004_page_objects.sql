create table objects (
  id         text primary key,
  page_id    text not null references pages(id) on delete cascade,
  layer_id   text not null references layers(id) on delete cascade,
  kind       text not null check (kind in ('text','image','sticker','shape','table')),
  asset_id   text references assets(id) on delete set null,
  props      text not null default '{}',
  z          integer not null,
  locked     integer not null default 0 check (locked in (0,1)),
  x real not null, y real not null, w real not null, h real not null, rotation real not null default 0,
  created_at integer not null,
  updated_at integer not null
);
-- ;;
create index objects_page_layer on objects (page_id, layer_id, z);
-- ;;
create index objects_asset on objects (asset_id) where asset_id is not null;
