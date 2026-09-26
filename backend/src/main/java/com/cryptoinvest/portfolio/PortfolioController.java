package com.cryptoinvest.portfolio;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.recommendation.PortfolioRecommendationEngine;
import com.cryptoinvest.recommendation.RecommendationModels;
import com.cryptoinvest.recommendation.RecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/portfolio")
public class PortfolioController {
 private final PortfolioReadService portfolios; private final PortfolioTargetRepository targets; private final RecommendationService recommendations; private final PortfolioRecommendationEngine.Limits limits;
 public PortfolioController(PortfolioReadService portfolios,PortfolioTargetRepository targets,RecommendationService recommendations,
   @Value("${recommendation.portfolio.btc-max:0.55}") BigDecimal btcMax,
   @Value("${recommendation.portfolio.eth-max:0.30}") BigDecimal ethMax,
   @Value("${recommendation.portfolio.major-alt-max:0.15}") BigDecimal majorAltMax,
   @Value("${recommendation.portfolio.high-risk-alt-max:0.05}") BigDecimal highRiskAltMax,
   @Value("${recommendation.portfolio.total-alt-max:0.35}") BigDecimal totalAltMax,
   @Value("${recommendation.portfolio.cash-min:0.10}") BigDecimal cashMin) {
  this.portfolios=portfolios; this.targets=targets; this.recommendations=recommendations;
  this.limits=new PortfolioRecommendationEngine.Limits(btcMax,ethMax,majorAltMax,highRiskAltMax,totalAltMax,cashMin);
 }
 @GetMapping("/{exchange}") public PortfolioReadService.PortfolioView read(Authentication auth,@PathVariable Exchange exchange){return portfolios.read((UUID)auth.getPrincipal(),exchange);}
 @PutMapping("/{exchange}/targets") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void replaceTargets(Authentication auth,@PathVariable Exchange exchange,@Valid @RequestBody TargetRequest request){targets.replace((UUID)auth.getPrincipal(),exchange,request.targets().stream().map(t->new PortfolioTargetRepository.Target(t.currency(),t.weight())).toList());}
 @GetMapping("/{exchange}/recommendations")
 public PortfolioProposal recommendation(Authentication auth,@PathVariable Exchange exchange,@RequestParam @NotBlank @Pattern(regexp="[A-Z]{2,10}-[A-Z0-9]{2,20}") String market){
  UUID user=(UUID)auth.getPrincipal(); var portfolio=portfolios.read(user,exchange); var evaluation=recommendations.recommend(exchange,market).evaluation();
  String symbol=market.substring(market.indexOf('-')+1); var position=portfolio.positions().stream().filter(p->p.currency().equals(symbol)).findFirst().orElse(null);
  BigDecimal current=position==null?BigDecimal.ZERO:position.weight(); Map<String,BigDecimal> userTargets=targets.findByUserAndExchange(user,exchange); BigDecimal target=userTargets.get(symbol);
  BigDecimal alt=portfolio.positions().stream().filter(p->!List.of("KRW","BTC","ETH").contains(p.currency())).map(PortfolioReadService.Position::weight).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(target==null)return new PortfolioProposal(exchange,market,evaluation.score(),evaluation.confidence(),portfolio.totalEvaluatedAmount(),portfolio.cashWeight(),current,null,null,null,null,"HOLD",null,List.of("종목 목표 비중을 먼저 설정해야 포트폴리오 제안을 계산할 수 있습니다."),Map.of(),evaluation.factors());
  BigDecimal max=switch(symbol){case "BTC"->limits.btcMax();case "ETH"->limits.ethMax();case "SOL","XRP","BNB","ADA","DOGE"->limits.majorAltMax();default->limits.highRiskAltMax();};
  var suggestion=new PortfolioRecommendationEngine().suggest(evaluation.score(),evaluation.marketRegime().regime(),current,target,max,portfolio.cashWeight(),alt,limits);
  Map<String,BigDecimal> correlations=recommendations.correlations(exchange,market,portfolio.positions().stream().map(PortfolioReadService.Position::currency).toList());
  boolean highCorrelation=correlations.values().stream().anyMatch(value->value.compareTo(new BigDecimal("0.85"))>=0);
  BigDecimal amount=portfolio.totalEvaluatedAmount().multiply(suggestion.weightDelta()).abs();
  if(highCorrelation && suggestion.weightDelta().signum()>0) amount=amount.multiply(new BigDecimal("0.50"));
  List<String> explanations=highCorrelation && suggestion.weightDelta().signum()>0 ? java.util.stream.Stream.concat(suggestion.explanations().stream(),java.util.stream.Stream.of("High correlation with an existing position; suggested value is halved.")).toList() : suggestion.explanations();
  BigDecimal pnl=position==null||position.averageBuyPrice()==null?null:position.currentPrice().subtract(position.averageBuyPrice()).multiply(position.quantity());
  return new PortfolioProposal(exchange,market,evaluation.score(),evaluation.confidence(),portfolio.totalEvaluatedAmount(),portfolio.cashWeight(),current,target,max,amount,pnl,suggestion.action().name(),suggestion.reason(),explanations,correlations,evaluation.factors());
 }
 public record PortfolioProposal(Exchange exchange,String market,int assetScore,int confidence,BigDecimal totalValue,BigDecimal cashWeight,BigDecimal currentWeight,BigDecimal targetWeight,BigDecimal maxWeight,BigDecimal suggestedAmount,BigDecimal unrealizedPnl,String action,RecommendationModels.ReductionReason reason,List<String> explanations,Map<String,BigDecimal> correlations,List<RecommendationModels.FactorContribution> factors){}
 public record TargetRequest(@NotEmpty List<@Valid Target> targets){}
 public record Target(@NotBlank @Pattern(regexp="[A-Z0-9]{2,20}") String currency,@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal weight){}
}
