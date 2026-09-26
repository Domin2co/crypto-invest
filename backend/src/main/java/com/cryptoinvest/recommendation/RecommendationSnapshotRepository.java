package com.cryptoinvest.recommendation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class RecommendationSnapshotRepository {
 private final JdbcTemplate jdbc; private final ObjectMapper json;
 public RecommendationSnapshotRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 @Transactional public void save(RecommendationModels.AssetEvaluation e,BigDecimal price){
  String key=e.exchange()+":"+e.market()+":"+e.ruleVersion();
  jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",Object.class,key);
  Boolean exists=jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM recommendation_evaluation_snapshot WHERE exchange=? AND market=? AND rule_version=? AND evaluated_at>=date_trunc('hour',now()))",Boolean.class,e.exchange().name(),e.market(),e.ruleVersion());
  if(Boolean.TRUE.equals(exists))return;
  try{
   Map<String,Object> indicators=Map.of("price",price,"metrics",e.metrics());
   Map<String,Object> quality=Map.of("confidence",e.confidenceBreakdown());
   jdbc.update("INSERT INTO recommendation_evaluation_snapshot (id,exchange,market,rule_version,market_regime,market_regime_score,asset_score,confidence_score,rating,indicator_values,factor_contributions,data_quality,evaluated_at) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?)",
    UUID.randomUUID(),e.exchange().name(),e.market(),e.ruleVersion(),e.marketRegime().regime().name(),e.marketRegime().score(),e.score(),e.confidence(),e.rating().name(),json.writeValueAsString(indicators),json.writeValueAsString(e.factors()),json.writeValueAsString(quality),Timestamp.from(e.evaluatedAt()));
  }catch(JsonProcessingException ex){throw new IllegalStateException("Unable to serialize recommendation snapshot",ex);}
 }
 public List<PendingOutcome> pending(int limit){return jdbc.query("SELECT id,exchange,market,evaluated_at,(indicator_values->>'price')::numeric AS price,forward_7d_return_pct,forward_30d_return_pct FROM recommendation_evaluation_snapshot WHERE evaluated_at<=now()-interval '7 days' AND (forward_7d_return_pct IS NULL OR (evaluated_at<=now()-interval '30 days' AND forward_30d_return_pct IS NULL)) ORDER BY evaluated_at LIMIT ?",(rs,row)->new PendingOutcome(rs.getObject("id",UUID.class),rs.getString("exchange"),rs.getString("market"),rs.getTimestamp("evaluated_at").toInstant(),rs.getBigDecimal("price"),rs.getBigDecimal("forward_7d_return_pct"),rs.getBigDecimal("forward_30d_return_pct")),limit);}
 public void updateOutcome(UUID id,BigDecimal r7,BigDecimal r30){jdbc.update("UPDATE recommendation_evaluation_snapshot SET forward_7d_return_pct=COALESCE(forward_7d_return_pct,?),forward_30d_return_pct=COALESCE(forward_30d_return_pct,?),outcome_observed_at=now() WHERE id=?",r7,r30,id);}
 public List<BacktestGroup> backtest(int days){
  String column=days==7?"forward_7d_return_pct":days==30?"forward_30d_return_pct":null; if(column==null)throw new IllegalArgumentException("days must be 7 or 30");
  String sql="SELECT rating,market_regime,COUNT(*) samples,AVG("+column+") average_return,AVG(CASE WHEN "+column+">0 THEN 1.0 ELSE 0.0 END)*100 win_rate FROM recommendation_evaluation_snapshot WHERE "+column+" IS NOT NULL GROUP BY rating,market_regime ORDER BY rating,market_regime";
  return jdbc.query(sql,(rs,row)->new BacktestGroup(rs.getString("rating"),rs.getString("market_regime"),rs.getLong("samples"),rs.getBigDecimal("average_return"),rs.getBigDecimal("win_rate")));
 }
 public record BacktestGroup(String rating,String regime,long samples,BigDecimal averageReturnPercent,BigDecimal winRatePercent){} 
 public record PendingOutcome(UUID id,String exchange,String market,java.time.Instant evaluatedAt,BigDecimal price,BigDecimal return7d,BigDecimal return30d){}
}
