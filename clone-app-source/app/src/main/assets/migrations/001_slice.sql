create table folders (
  id         text primary key,
  parent_id  text references folders(id) on delete cascade,   -- deleting a folder deletes subfolders; the app asks first
  name       text not null,
  sort_order integer not null default 0,
  created_at integer not null,
  updated_at integer not null
);
-- ;;
create index folders_parent on folders (parent_id, sort_order);
-- ;;
create table assets (
  id         text primary key,
  kind       text not null check (kind in ('pdf','image','sticker','font','paper','cover','audio','theme')),
  source     text not null check (source in ('bundled','imported','downloaded','generated')),
  file_path  text not null,                     -- relative to the app-private assets dir
  mime       text not null,
  sha256     text,
  byte_size  integer not null default 0,
  width      integer,
  height     integer,
  page_count integer,                           -- pdf only
  license    text,                              -- required for bundled and downloaded assets
  created_at integer not null
);
-- ;;
create unique index assets_dedupe on assets (sha256) where sha256 is not null;
-- ;;
create index assets_kind on assets (kind, source);
-- ;;
create table notebooks (
  id                 text primary key,
  folder_id          text references folders(id) on delete restrict,  -- the app moves or deletes notebooks first
  title              text not null default '',
  kind               text not null check (kind in ('paged','infinite')),
  has_cover          integer not null default 1 check (has_cover in (0,1)),
  cover_asset_id     text references assets(id) on delete set null,
  paper_asset_id     text references assets(id) on delete set null,
  paper_color        text,
  page_width_pt      real,
  page_height_pt     real,
  orientation        text check (orientation in ('portrait','landscape')),
  margin_top_pt      real not null default 0,
  margin_right_pt    real not null default 0,
  margin_bottom_pt   real not null default 0,
  margin_left_pt     real not null default 0,
  margin_color       text,
  margin_on_cover    integer not null default 0 check (margin_on_cover in (0,1)),
  auto_add_pages     integer not null default 0 check (auto_add_pages in (0,1)),
  pinned_at          integer,                   -- null = not pinned
  source_asset_id    text references assets(id) on delete set null,  -- imported pdf, if any
  last_opened_at     integer,
  created_at         integer not null,
  updated_at         integer not null
);
-- ;;
create index notebooks_folder on notebooks (folder_id, updated_at desc);
-- ;;
create index notebooks_pinned on notebooks (pinned_at) where pinned_at is not null;
-- ;;
create index notebooks_recent on notebooks (last_opened_at desc);
-- ;;
create table pages (
  id              text primary key,
  notebook_id     text not null references notebooks(id) on delete cascade,
  idx             integer not null,
  kind            text not null default 'fixed' check (kind in ('fixed','infinite')),
  width_pt        real,
  height_pt       real,
  bounds_min_x    real, bounds_min_y real,      -- infinite pages: extent of content
  bounds_max_x    real, bounds_max_y real,
  template_asset_id text references assets(id) on delete set null,
  bg_color        text,
  source_asset_id text references assets(id) on delete set null,
  source_page_no  integer,                      -- 1-based page in the source pdf
  thumbnail_path  text,
  created_at      integer not null,
  updated_at      integer not null,
  check (source_asset_id is null or source_page_no is not null)   -- page number may outlive the asset: deleting the pdf sets source_asset_id to null
);
-- ;;
create unique index pages_order on pages (notebook_id, idx);
-- ;;
create index pages_source on pages (source_asset_id, source_page_no);
-- ;;
create table layers (
  id      text primary key,
  page_id text not null references pages(id) on delete cascade,
  idx     integer not null,
  name    text not null,
  hidden  integer not null default 0 check (hidden in (0,1)),
  locked  integer not null default 0 check (locked in (0,1))
);
-- ;;
create unique index layers_order on layers (page_id, idx);
-- ;;
create trigger layers_max20 before insert on layers
when (select count(*) from layers where page_id = new.page_id) >= 20
begin select raise(abort, 'layer limit (20) reached'); end;
-- ;;
create table recordings (
  id          text primary key,
  notebook_id text not null references notebooks(id) on delete cascade,
  asset_id    text not null references assets(id) on delete restrict,  -- audio file is removed by the asset sweeper
  started_at  integer not null,
  duration_ms integer not null check (duration_ms between 0 and 7200000),  -- 120 minutes
  language    text,
  transcript  text,
  summary     text,
  created_at  integer not null
);
-- ;;
create index recordings_notebook on recordings (notebook_id, started_at);
-- ;;
create table strokes (
  id            integer primary key autoincrement,
  page_id       text not null references pages(id) on delete cascade,
  layer_id      text not null references layers(id) on delete cascade,
  z             integer not null,
  data          blob not null,                  -- androidx.ink storage encoding: brush + inputs
  brush_type    text not null,                  -- denormalized for export and search
  brush_params  text not null default '{}',  -- {kind,color,size,epsilon}; ink-storage holds only the inputs, so the brush is kept here
  min_x real not null, min_y real not null, max_x real not null, max_y real not null,
  recording_id  text references recordings(id) on delete set null,
  rec_offset_ms integer,                        -- ms since recording start, for audio-ink sync
  created_at    integer not null,
  check (recording_id is null or rec_offset_ms is not null)   -- offset may outlive the link: deleting a recording sets recording_id to null
);
-- ;;
create index strokes_page_layer on strokes (page_id, layer_id, z);
-- ;;
create index strokes_recording on strokes (recording_id, rec_offset_ms) where recording_id is not null;
-- ;;
create virtual table stroke_rtree using rtree(id, min_x, max_x, min_y, max_y);
-- ;;
create trigger strokes_ai after insert on strokes begin
  insert into stroke_rtree values (new.id, new.min_x, new.max_x, new.min_y, new.max_y);
end;
-- ;;
create trigger strokes_au after update of min_x,min_y,max_x,max_y on strokes begin
  update stroke_rtree set min_x=new.min_x, max_x=new.max_x, min_y=new.min_y, max_y=new.max_y where id = new.id;
end;
-- ;;
create trigger strokes_ad after delete on strokes begin
  delete from stroke_rtree where id = old.id;
end;
-- ;;
create table brushes (
  id                   text primary key,
  type                 text not null check (type in ('fountain','ballpoint','flat','pencil','highlighter','softpaint','tape')),
  pressure_sensitivity real not null default 0.4 check (pressure_sensitivity between 0 and 1),
  thickness_mm         real not null check (thickness_mm > 0),
  color                text not null,           -- #RRGGBB
  alpha                real not null default 1 check (alpha between 0 and 1),
  stabilization        real not null default 0 check (stabilization between 0 and 1),
  line_style           text not null default 'solid' check (line_style in ('solid','dashed','dotted')),
  created_at           integer not null
);
-- ;;
create table settings (
  key        text primary key,
  value      text not null,
  updated_at integer not null
);
