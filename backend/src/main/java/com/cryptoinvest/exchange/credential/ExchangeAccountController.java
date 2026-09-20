package com.cryptoinvest.exchange.credential;

import com.cryptoinvest.exchange.Exchange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증한 사용자 본인의 거래소 자격증명만 암호화해 저장한다. */
@RestController
@RequestMapping("/api/exchange-accounts")
public class ExchangeAccountController {
    private final ExchangeAccountCredentialService credentials;
    public ExchangeAccountController(ExchangeAccountCredentialService credentials) { this.credentials = credentials; }
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(Authentication authentication, @Valid @RequestBody SaveAccountRequest request) {
        credentials.save((UUID) authentication.getPrincipal(), request.exchange(), new ExchangeCredentials(request.accessKey(), request.secretKey()));
    }
    public record SaveAccountRequest(Exchange exchange, @NotBlank String accessKey, @NotBlank String secretKey) {}
}
