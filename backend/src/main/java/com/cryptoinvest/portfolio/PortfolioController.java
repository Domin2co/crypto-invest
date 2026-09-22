package com.cryptoinvest.portfolio;

import com.cryptoinvest.exchange.Exchange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/** 본인 토큰에서 추출한 사용자 식별자로만 읽기 전용 포트폴리오를 조회한다. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {
    private final PortfolioReadService portfolios;
    private final PortfolioTargetRepository targets;

    public PortfolioController(PortfolioReadService portfolios, PortfolioTargetRepository targets) { this.portfolios = portfolios; this.targets = targets; }

    @GetMapping("/{exchange}")
    public PortfolioReadService.PortfolioView read(Authentication authentication, @PathVariable Exchange exchange) {
        return portfolios.read((UUID) authentication.getPrincipal(), exchange);
    }

    @PutMapping("/{exchange}/targets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceTargets(Authentication authentication, @PathVariable Exchange exchange, @Valid @RequestBody TargetRequest request) {
        targets.replace((UUID) authentication.getPrincipal(), exchange,
                request.targets().stream().map(target -> new PortfolioTargetRepository.Target(target.currency(), target.weight())).toList());
    }

    public record TargetRequest(@NotEmpty List<@Valid Target> targets) {}
    public record Target(@NotBlank @Pattern(regexp = "[A-Z0-9]{2,20}") String currency,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal weight) {}
}
