package com.cryptoinvest.recommendation;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketCandle;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
@Service
public class RecommendationOutcomeService {
 private final RecommendationSnapshotRepository snapshots;
 private final Map<Exchange,ExchangePublicClient> clients;
 public RecommendationOutcomeService(RecommendationSnapshotRepository snapshots,List<ExchangePublicClient> clients){this.snapshots=snapshots;this.clients=clients.stream().collect(Collectors.toUnmodifiableMap(ExchangePublicClient::exchange,Function.identity()));}
 @Scheduled(fixedDelay=21600000)
 public void observe(){
  Map<String,List<RecommendationSnapshotRepository.PendingOutcome>> groups=snapshots.pending(100).stream().collect(Collectors.groupingBy(x->x.exchange()+":"+x.market()));
  for(List<RecommendationSnapshotRepository.PendingOutcome> group:groups.values()){
   var item=group.getFirst();
   try{
    var client=clients.get(Exchange.valueOf(item.exchange())); if(client==null)continue;
    List<MarketCandle> candles=client.getDailyCandles(item.market(),121).stream().sorted(Comparator.comparing(MarketCandle::openedAt)).toList();
    for(var pending:group){
     var r7=pending.return7d()==null?RecommendationBacktest.forwardReturnPercent(pending.evaluatedAt(),pending.price(),candles,7):null;
     var r30=pending.return30d()==null?RecommendationBacktest.forwardReturnPercent(pending.evaluatedAt(),pending.price(),candles,30):null;
     if(r7!=null||r30!=null)snapshots.updateOutcome(pending.id(),r7,r30);
    }
   }catch(RuntimeException ignored){ /* Retry on next scheduled pass; quote/provider failures do not alter the snapshot. */ }
  }
 }
}
