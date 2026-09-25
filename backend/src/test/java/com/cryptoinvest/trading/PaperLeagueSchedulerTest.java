package com.cryptoinvest.trading;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaperLeagueSchedulerTest {
    @Test
    void retriesBothMonthBoundariesThroughoutTheFirstDay() {
        PaperLeagueService league = Mockito.mock(PaperLeagueService.class);
        PaperLeagueScheduler scheduler = new PaperLeagueScheduler(league);
        scheduler.captureMonthBoundary(LocalDateTime.of(2026, 9, 1, 18, 42));
        verify(league).closeMonth(YearMonth.of(2026, 8));
        verify(league).openMonth(YearMonth.of(2026, 9));
    }

    @Test
    void doesNotRepeatBoundaryWorkOnLaterDays() {
        PaperLeagueService league = Mockito.mock(PaperLeagueService.class);
        new PaperLeagueScheduler(league).captureMonthBoundary(LocalDateTime.of(2026, 9, 2, 0, 0));
        verifyNoInteractions(league);
    }
}
