package com.cryptoinvest.trading;

/** 주문 전송 결과를 확정할 수 없어 새 주문 대신 식별자로 상태를 조회해야 할 때 사용한다. */
public class LiveOrderUnknownResultException extends RuntimeException {
    public LiveOrderUnknownResultException(String message) { super(message); }
    public LiveOrderUnknownResultException(String message, Throwable cause) { super(message, cause); }
}
