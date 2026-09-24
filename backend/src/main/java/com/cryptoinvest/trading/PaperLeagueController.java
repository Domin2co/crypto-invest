package com.cryptoinvest.trading;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper-league")
public class PaperLeagueController {
    private final PaperLeagueService league;
    public PaperLeagueController(PaperLeagueService league) { this.league = league; }

    @GetMapping
    public PaperLeagueService.Board board(@RequestParam(required = false) String month) {
        try {
            return league.board(month == null || month.isBlank() ? league.currentMonth() : YearMonth.parse(month));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid month; use YYYY-MM");
        }
    }
    @GetMapping("/entry")
    public PaperLeagueService.EntryStatus entry(Authentication authentication) {
        return league.entry((UUID) authentication.getPrincipal());
    }
    @PostMapping("/entry")
    @ResponseStatus(HttpStatus.OK)
    public PaperLeagueService.EntryStatus enroll(Authentication authentication) {
        return league.enroll((UUID) authentication.getPrincipal());
    }
}