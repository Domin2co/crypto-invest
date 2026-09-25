package com.cryptoinvest.trading;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 월 경계 집계를 한국 시간 첫 5분 동안 재시도한다. */
@Component
public class PaperLeagueScheduler {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(PaperLeagueScheduler.class);
    private final PaperLeagueService league;
    public PaperLeagueScheduler(PaperLeagueService league) { this.league = league; }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() { captureMonthBoundary(); }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void captureMonthBoundary() { captureMonthBoundary(LocalDateTime.now(ZONE)); }

    void captureMonthBoundary(LocalDateTime now) {
        if (now.getDayOfMonth() != 1) return;
        YearMonth current = YearMonth.from(now);
        try { league.closeMonth(current.minusMonths(1)); }
        catch (RuntimeException exception) { log.warn("Monthly PAPER close failed; next boundary tick will retry"); }
        try { league.openMonth(current); }
        catch (RuntimeException exception) { log.warn("Monthly PAPER open failed; next boundary tick will retry"); }
    }
}