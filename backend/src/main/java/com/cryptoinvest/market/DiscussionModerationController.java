package com.cryptoinvest.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/discussion-reports")
public class DiscussionModerationController {
    private final MarketDiscussionRepository discussions;
    public DiscussionModerationController(MarketDiscussionRepository discussions) { this.discussions=discussions; }

    @GetMapping
    public MarketDiscussionRepository.ReportPage reports(@RequestParam(defaultValue="0") int page,Authentication auth) {
        requireAdmin(auth);
        if(page<0)throw new IllegalArgumentException("Invalid page");
        return discussions.findReports(page);
    }

    @PatchMapping("/{reportId}") @Transactional
    public void resolve(@PathVariable UUID reportId,@Valid @RequestBody ModerationRequest request,Authentication auth) {
        UUID admin=(UUID)auth.getPrincipal();
        requireAdmin(auth);
        var target=discussions.findReportTarget(reportId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        switch(request.action()) {
            case "HIDE" -> {
                if(request.reason()==null||request.reason().isBlank())throw new IllegalArgumentException("A hide reason is required");
                if(!target.status().equals("PENDING")||!discussions.hidePost(target.postId(),admin,request.reason().strip()))throw new IllegalStateException("Report cannot be hidden");
                discussions.hideReportsForPost(target.postId(),admin);
            }
            case "DISMISS" -> {
                if(!target.status().equals("PENDING")||!discussions.dismissReport(reportId,admin))throw new IllegalStateException("Report cannot be dismissed");
            }
            case "RESTORE" -> {
                if(!target.status().equals("HIDDEN")||!discussions.restorePost(target.postId(),admin))throw new IllegalStateException("Post is not hidden");
                discussions.restoreReportsForPost(target.postId(),admin);
            }
            default -> throw new IllegalArgumentException("Invalid moderation action");
        }
    }

    private void requireAdmin(Authentication auth) {
        if(auth==null||!discussions.isAdmin((UUID)auth.getPrincipal()))throw new AccessDeniedException("Administrator access required");
    }

    public record ModerationRequest(@NotBlank @Pattern(regexp="HIDE|DISMISS|RESTORE") String action,@Size(min=3,max=500) String reason) {}
}


