COMMENT ON TABLE market_discussion_post IS '종목별 공개 토론 게시글; 계정 삭제 시 사용자 게시글 삭제';
COMMENT ON COLUMN market_discussion_post.id IS '게시글 고유 식별자';
COMMENT ON COLUMN market_discussion_post.symbol IS '토론 대상 가상자산 종목 기호';
COMMENT ON COLUMN market_discussion_post.user_id IS '작성자 계정 ID; 공개 응답에는 노출하지 않음';
COMMENT ON COLUMN market_discussion_post.content IS '1,000자 이하 일반 텍스트 게시글';
COMMENT ON COLUMN market_discussion_post.created_at IS '게시글 생성 시각';