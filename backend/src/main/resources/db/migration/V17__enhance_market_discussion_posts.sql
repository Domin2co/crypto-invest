ALTER TABLE market_discussion_post
    ADD COLUMN title VARCHAR(120) NOT NULL DEFAULT 'Untitled' CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    ADD COLUMN font_family VARCHAR(10) NOT NULL DEFAULT 'system' CHECK (font_family IN ('system', 'serif', 'mono')),
    ADD COLUMN font_size SMALLINT NOT NULL DEFAULT 16 CHECK (font_size BETWEEN 14 AND 24),
    ADD COLUMN text_align VARCHAR(6) NOT NULL DEFAULT 'left' CHECK (text_align IN ('left', 'center', 'right')),
    ADD COLUMN image_type VARCHAR(20),
    ADD COLUMN image_data BYTEA,
    ADD CONSTRAINT ck_market_discussion_image_pair CHECK ((image_type IS NULL) = (image_data IS NULL)),
    ADD CONSTRAINT ck_market_discussion_image_type CHECK (image_type IS NULL OR image_type IN ('image/png', 'image/jpeg', 'image/webp'));

UPDATE market_discussion_post SET title = left(content, 120);

COMMENT ON COLUMN market_discussion_post.title IS 'Post title, up to 120 characters';
COMMENT ON COLUMN market_discussion_post.font_family IS 'Restricted plain-text display font selector';
COMMENT ON COLUMN market_discussion_post.font_size IS 'Display font size from 14 to 24 pixels';
COMMENT ON COLUMN market_discussion_post.text_align IS 'Plain-text alignment: left, center, or right';
COMMENT ON COLUMN market_discussion_post.image_data IS 'Optional raster image, maximum 512 KiB, with MIME signature checked by API';
