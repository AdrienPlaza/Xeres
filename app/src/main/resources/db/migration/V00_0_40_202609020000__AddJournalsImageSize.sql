--
-- Add image size columns to journal_message
--
ALTER TABLE journal_message ADD COLUMN image_width INT NOT NULL DEFAULT 0;
ALTER TABLE journal_message ADD COLUMN image_height INT NOT NULL DEFAULT 0;
