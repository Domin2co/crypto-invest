CREATE TABLE market_discussion_vote (
    post_id UUID NOT NULL REFERENCES market_discussion_post(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    vote SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id)
);
CREATE TABLE market_discussion_comment (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES market_discussion_post(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    content VARCHAR(500) NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_market_discussion_comment_post_created ON market_discussion_comment (post_id, created_at, id);
COMMENT ON TABLE market_discussion_vote IS '사용자당 게시글별 추천 또는 비추천 1건';
COMMENT ON TABLE market_discussion_comment IS '종목 토론방 댓글; 작성자 계정 삭제 시 함께 삭제';
COMMENT ON COLUMN market_discussion_vote.vote IS '추천 1, 비추천 -1';
COMMENT ON COLUMN market_discussion_comment.content IS '최대 500자의 일반 텍스트 댓글';