package com.cryptoinvest.exchange.privateapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.credential.ExchangeCredentials;
import com.cryptoinvest.portfolio.ExchangeBalance;
import java.util.List;

/** 인증된 계정의 읽기 전용 자산 조회 port. 주문 메서드는 의도적으로 포함하지 않는다. */
public interface PrivateAccountClient {
    Exchange exchange();
    List<ExchangeBalance> getBalances(ExchangeCredentials credentials);
    ExchangeOrderChance getOrderChance(ExchangeCredentials credentials, String market);
}
