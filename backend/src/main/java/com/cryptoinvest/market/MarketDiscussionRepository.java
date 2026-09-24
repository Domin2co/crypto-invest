package com.cryptoinvest.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketDiscussionRepository {
    private final JdbcTemplate jdbc;
    public MarketDiscussionRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public PostPage findLatest(String symbol,int page) {
        long total=jdbc.queryForObject("SELECT count(*) FROM market_discussion_post p JOIN app_user u ON u.id=p.user_id WHERE p.symbol=? AND u.enabled=TRUE AND p.moderation_hidden=FALSE",Long.class,symbol);
        List<PostSummary> posts=jdbc.query("""
                WITH ordered AS (SELECT p.id,p.title,u.nickname,p.created_at,row_number() OVER (ORDER BY p.created_at DESC,p.id DESC) AS post_number
                FROM market_discussion_post p JOIN app_user u ON u.id=p.user_id WHERE p.symbol=? AND u.enabled=TRUE AND p.moderation_hidden=FALSE)
                SELECT o.id,o.post_number,o.title,o.nickname,o.created_at,(SELECT count(*) FROM market_discussion_vote v WHERE v.post_id=o.id AND v.vote=1) AS up_votes
                FROM ordered o ORDER BY o.post_number LIMIT 10 OFFSET ?
                """,(rs,n)->new PostSummary(UUID.fromString(rs.getString("id")),rs.getLong("post_number"),rs.getString("title"),rs.getString("nickname"),rs.getTimestamp("created_at").toInstant(),rs.getLong("up_votes")),symbol,(long)page*10);
        return new PostPage(posts,page,10,total,(int)((total+9)/10));
    }

    public Post create(UUID userId,String symbol,MarketDiscussionController.PostRequest r) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO market_discussion_post (id,symbol,user_id,title,content,font_family,font_size,text_align,image_type,image_data) VALUES (?,?,?,?,?,?,?,?,?,?)",id,symbol,userId,r.title().strip(),r.content().strip(),r.fontFamily(),r.fontSize(),r.textAlign(),r.imageType(),r.imageData());
        return findById(id,userId).orElseThrow();
    }

    public boolean updatePost(UUID user,UUID id,String symbol,MarketDiscussionController.PostRequest r) {
        return jdbc.update("""
                UPDATE market_discussion_post SET title=?,content=?,font_family=?,font_size=?,text_align=?,
                    image_type=COALESCE(?,image_type),image_data=COALESCE(?,image_data),updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND symbol=? AND user_id=? AND EXISTS (SELECT 1 FROM app_user WHERE id=? AND enabled=TRUE)
                """,r.title().strip(),r.content().strip(),r.fontFamily(),r.fontSize(),r.textAlign(),r.imageType(),r.imageData(),id,symbol,user,user)==1;
    }

    public boolean deletePost(UUID user,UUID id,String symbol) {
        return jdbc.update("DELETE FROM market_discussion_post WHERE id=? AND symbol=? AND user_id=?",id,symbol,user)==1;
    }

    public Optional<PostDetail> findDetail(String symbol,UUID id,UUID viewer,boolean reveal) {
        return jdbc.query("""
                SELECT p.id,p.symbol,p.user_id,u.nickname,p.title,p.content,p.font_family,p.font_size,p.text_align,p.image_type IS NOT NULL AS has_image,p.created_at,p.updated_at,p.moderation_hidden,
                (SELECT count(*) FROM market_discussion_vote v WHERE v.post_id=p.id AND v.vote=1) AS up_votes,
                (SELECT count(*) FROM market_discussion_vote v WHERE v.post_id=p.id AND v.vote=-1) AS down_votes,
                (SELECT v.vote FROM market_discussion_vote v WHERE v.post_id=p.id AND v.user_id=?) AS user_vote
                FROM market_discussion_post p JOIN app_user u ON u.id=p.user_id WHERE p.id=? AND p.symbol=? AND u.enabled=TRUE
                """,rs->{
            if(!rs.next())return Optional.empty();
            long down=rs.getLong("down_votes"); boolean blind=down>20; boolean hidden=rs.getBoolean("moderation_hidden");
            UUID author=rs.getObject("user_id",UUID.class);
            return Optional.of(new PostDetail(UUID.fromString(rs.getString("id")),rs.getString("symbol"),rs.getString("nickname"),rs.getString("title"),
                !hidden&&(!blind||reveal)?rs.getString("content"):null,rs.getString("font_family"),rs.getInt("font_size"),rs.getString("text_align"),rs.getBoolean("has_image"),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getLong("up_votes"),down,blind,hidden,
                rs.getObject("user_vote")==null?null:((Number)rs.getObject("user_vote")).intValue(),viewer!=null&&viewer.equals(author)));
        },viewer,id,symbol);
    }

    public CommentPage findComments(UUID id,UUID viewer,int page) {
        long total=jdbc.queryForObject("SELECT count(*) FROM market_discussion_comment c JOIN app_user u ON u.id=c.user_id WHERE c.post_id=? AND u.enabled=TRUE",Long.class,id);
        List<DiscussionComment> comments=jdbc.query("""
                SELECT c.id,c.user_id,u.nickname,c.content,c.created_at,c.updated_at FROM market_discussion_comment c
                JOIN app_user u ON u.id=c.user_id WHERE c.post_id=? AND u.enabled=TRUE ORDER BY c.created_at,c.id LIMIT 10 OFFSET ?
                """,(rs,n)->new DiscussionComment(UUID.fromString(rs.getString("id")),rs.getString("nickname"),rs.getString("content"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),viewer!=null&&viewer.equals(rs.getObject("user_id",UUID.class))),id,(long)page*10);
        return new CommentPage(comments,page,10,total,(int)((total+9)/10));
    }

    public void addComment(UUID user,UUID post,String content) {
        jdbc.update("INSERT INTO market_discussion_comment (id,post_id,user_id,content) VALUES (?,?,?,?)",UUID.randomUUID(),post,user,content.strip());
    }
    public boolean updateComment(UUID user,UUID post,UUID comment,String content) {
        return jdbc.update("UPDATE market_discussion_comment SET content=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND post_id=? AND user_id=?",content.strip(),comment,post,user)==1;
    }
    public boolean deleteComment(UUID user,UUID post,UUID comment) {
        return jdbc.update("DELETE FROM market_discussion_comment WHERE id=? AND post_id=? AND user_id=?",comment,post,user)==1;
    }

    public VoteCounts vote(UUID user,UUID post,int direction) {
        jdbc.update("INSERT INTO market_discussion_vote (post_id,user_id,vote) VALUES (?,?,?) ON CONFLICT (post_id,user_id) DO UPDATE SET vote=EXCLUDED.vote,created_at=CURRENT_TIMESTAMP",post,user,direction);
        return jdbc.queryForObject("SELECT count(*) FILTER (WHERE vote=1) up_votes,count(*) FILTER (WHERE vote=-1) down_votes FROM market_discussion_vote WHERE post_id=?",(rs,n)->new VoteCounts(rs.getLong(1),rs.getLong(2)),post);
    }

    public boolean exists(String symbol,UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM market_discussion_post WHERE symbol=? AND id=?)",Boolean.class,symbol,id));
    }
    public boolean visible(String symbol,UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM market_discussion_post WHERE symbol=? AND id=? AND moderation_hidden=FALSE)",Boolean.class,symbol,id));
    }
    public Optional<PostImage> findImage(String symbol,UUID id,boolean reveal) {
        return jdbc.query("SELECT image_type,image_data FROM market_discussion_post p WHERE id=? AND symbol=? AND moderation_hidden=FALSE AND image_data IS NOT NULL AND (? OR (SELECT count(*) FROM market_discussion_vote v WHERE v.post_id=p.id AND v.vote=-1)<=20)",rs->rs.next()?Optional.of(new PostImage(rs.getString(1),rs.getBytes(2))):Optional.empty(),id,symbol,reveal);
    }

    public boolean report(UUID reporter,UUID post,String reason,String details) {
        return jdbc.update("INSERT INTO market_discussion_report (id,post_id,reporter_user_id,reason,details) VALUES (?,?,?,?,?) ON CONFLICT (post_id,reporter_user_id) DO NOTHING",UUID.randomUUID(),post,reporter,reason,details)==1;
    }
    public ReportPage findReports(int page) {
        long total=jdbc.queryForObject("SELECT count(*) FROM market_discussion_report",Long.class);
        List<DiscussionReport> reports=jdbc.query("""
                SELECT r.id,r.post_id,p.symbol,p.title,left(p.content,300) AS excerpt,u.nickname AS reporter_nickname,
                       r.reason,r.details,r.status,r.created_at,p.moderation_hidden
                FROM market_discussion_report r JOIN market_discussion_post p ON p.id=r.post_id
                JOIN app_user u ON u.id=r.reporter_user_id ORDER BY r.created_at DESC,r.id DESC LIMIT 10 OFFSET ?
                """,(rs,n)->new DiscussionReport(rs.getObject("id",UUID.class),rs.getObject("post_id",UUID.class),rs.getString("symbol"),rs.getString("title"),rs.getString("excerpt"),rs.getString("reporter_nickname"),rs.getString("reason"),rs.getString("details"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),rs.getBoolean("moderation_hidden")),(long)page*10);
        return new ReportPage(reports,page,10,total,(int)((total+9)/10));
    }
    public Optional<ReportTarget> findReportTarget(UUID id) {
        return jdbc.query("SELECT post_id,status FROM market_discussion_report WHERE id=?",rs->rs.next()?Optional.of(new ReportTarget(rs.getObject(1,UUID.class),rs.getString(2))):Optional.empty(),id);
    }
    public boolean isAdmin(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND role='ADMIN' AND enabled=TRUE)",Boolean.class,user));
    }
    public boolean hidePost(UUID post,UUID admin,String reason) {
        return jdbc.update("UPDATE market_discussion_post SET moderation_hidden=TRUE,moderation_reason=?,moderated_by=?,moderated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND moderation_hidden=FALSE",reason,admin,post)==1;
    }
    public void hideReportsForPost(UUID post,UUID admin) {
        jdbc.update("UPDATE market_discussion_report SET status='HIDDEN',resolved_at=CURRENT_TIMESTAMP,resolved_by=? WHERE post_id=? AND status='PENDING'",admin,post);
    }
    public boolean dismissReport(UUID id,UUID admin) {
        return jdbc.update("UPDATE market_discussion_report SET status='DISMISSED',resolved_at=CURRENT_TIMESTAMP,resolved_by=? WHERE id=? AND status='PENDING'",admin,id)==1;
    }
    public boolean restorePost(UUID post,UUID admin) {
        return jdbc.update("UPDATE market_discussion_post SET moderation_hidden=FALSE,moderation_reason=NULL,moderated_by=?,moderated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND moderation_hidden=TRUE",admin,post)==1;
    }
    public void restoreReportsForPost(UUID post,UUID admin) {
        jdbc.update("UPDATE market_discussion_report SET status='RESTORED',resolved_at=CURRENT_TIMESTAMP,resolved_by=? WHERE post_id=? AND status='HIDDEN'",admin,post);
    }

    private Optional<Post> findById(UUID id,UUID user) {
        return jdbc.query("SELECT p.id,p.symbol,u.nickname,p.title,p.content,p.font_family,p.font_size,p.text_align,p.image_type IS NOT NULL AS has_image,p.created_at FROM market_discussion_post p JOIN app_user u ON u.id=p.user_id WHERE p.id=? AND p.user_id=?",
            rs->rs.next()?Optional.of(new Post(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getString(8),rs.getBoolean(9),rs.getTimestamp(10).toInstant())):Optional.empty(),id,user);
    }
    public void deleteByUserId(UUID user) { jdbc.update("DELETE FROM market_discussion_post WHERE user_id=?",user); }

    public record Post(UUID id,String symbol,String nickname,String title,String content,String fontFamily,int fontSize,String textAlign,boolean hasImage,Instant createdAt) {}
    public record PostSummary(UUID id,long number,String title,String nickname,Instant createdAt,long upVotes) {}
    public record PostPage(List<PostSummary> content,int page,int size,long totalElements,int totalPages) {}
    public record PostDetail(UUID id,String symbol,String nickname,String title,String content,String fontFamily,int fontSize,String textAlign,boolean hasImage,Instant createdAt,Instant updatedAt,long upVotes,long downVotes,boolean blind,boolean hidden,Integer userVote,boolean canEdit) {}
    public record DiscussionComment(UUID id,String nickname,String content,Instant createdAt,Instant updatedAt,boolean canEdit) {}
    public record CommentPage(List<DiscussionComment> content,int page,int size,long totalElements,int totalPages) {}
    public record DiscussionReport(UUID id,UUID postId,String symbol,String title,String excerpt,String reporterNickname,String reason,String details,String status,Instant createdAt,boolean hidden) {}
    public record ReportPage(List<DiscussionReport> content,int page,int size,long totalElements,int totalPages) {}
    public record ReportTarget(UUID postId,String status) {}
    public record VoteCounts(long upVotes,long downVotes) {}
    public record PostImage(String contentType,byte[] data) {}
}
