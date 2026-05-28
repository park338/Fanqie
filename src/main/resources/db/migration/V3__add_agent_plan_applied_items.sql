ALTER TABLE agent_plan_drafts
  ADD COLUMN applied_schedule_item_ids_json TEXT NULL AFTER raw_response;
