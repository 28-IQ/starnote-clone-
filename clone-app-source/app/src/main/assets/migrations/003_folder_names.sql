create unique index folders_name on folders (coalesce(parent_id, ''), name collate nocase);
