package com.cryptoinvest.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/discussions")
public class MarketDiscussionController {
    private final MarketDiscussionRepository discussions;
    public MarketDiscussionController(MarketDiscussionRepository discussions) { this.discussions=discussions; }

    @GetMapping("/{symbol}")
    public MarketDiscussionRepository.PostPage list(@PathVariable String symbol,@RequestParam(defaultValue="0") int page) {
        if(page<0)throw new IllegalArgumentException("Invalid page");
        return discussions.findLatest(validSymbol(symbol),page);
    }

    @GetMapping("/{symbol}/{id}")
    public MarketDiscussionRepository.PostDetail detail(@PathVariable String symbol,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean reveal,Authentication auth) {
        UUID viewer=auth==null?null:(UUID)auth.getPrincipal();
        return discussions.findDetail(validSymbol(symbol),id,viewer,reveal).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{symbol}/{id}/comments")
    public MarketDiscussionRepository.CommentPage comments(@PathVariable String symbol,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,Authentication auth) {
        if(page<0)throw new IllegalArgumentException("Invalid page");
        requireVisiblePost(symbol,id);
        return discussions.findComments(id,auth==null?null:(UUID)auth.getPrincipal(),page);
    }

    @GetMapping("/{symbol}/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable String symbol,@PathVariable UUID id,@RequestParam(defaultValue="false") boolean reveal) {
        return discussions.findImage(validSymbol(symbol),id,reveal).map(x->ResponseEntity.ok().contentType(MediaType.parseMediaType(x.contentType())).cacheControl(CacheControl.noCache()).header("X-Content-Type-Options","nosniff").body(x.data())).orElseGet(()->ResponseEntity.notFound().build());
    }

    @PostMapping("/{symbol}/{id}/comments") @ResponseStatus(HttpStatus.CREATED)
    public void comment(@PathVariable String symbol,@PathVariable UUID id,@Valid @RequestBody CommentRequest r,Authentication auth) {
        requireVisiblePost(symbol,id);
        discussions.addComment((UUID)auth.getPrincipal(),id,r.content());
    }
    @PatchMapping("/{symbol}/{id}/comments/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateComment(@PathVariable String symbol,@PathVariable UUID id,@PathVariable UUID commentId,@Valid @RequestBody CommentRequest r,Authentication auth) {
        requireVisiblePost(symbol,id);
        if(!discussions.updateComment((UUID)auth.getPrincipal(),id,commentId,r.content()))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @DeleteMapping("/{symbol}/{id}/comments/{commentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable String symbol,@PathVariable UUID id,@PathVariable UUID commentId,Authentication auth) {
        requireVisiblePost(symbol,id);
        if(!discussions.deleteComment((UUID)auth.getPrincipal(),id,commentId))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @PostMapping("/{symbol}/{id}/reports") @ResponseStatus(HttpStatus.CREATED)
    public void report(@PathVariable String symbol,@PathVariable UUID id,@Valid @RequestBody ReportRequest r,Authentication auth) {
        requireVisiblePost(symbol,id);
        if(!discussions.report((UUID)auth.getPrincipal(),id,r.reason(),r.details()==null?null:r.details().strip()))throw new IllegalStateException("Report already submitted");
    }

    @PostMapping("/{symbol}/{id}/vote")
    public MarketDiscussionRepository.VoteCounts vote(@PathVariable String symbol,@PathVariable UUID id,@Valid @RequestBody VoteRequest r,Authentication auth) {
        requireVisiblePost(symbol,id);
        return discussions.vote((UUID)auth.getPrincipal(),id,r.direction().equals("UP")?1:-1);
    }

    @PostMapping("/{symbol}") @ResponseStatus(HttpStatus.CREATED)
    public MarketDiscussionRepository.Post create(@PathVariable String symbol,@Valid @RequestBody PostRequest r,Authentication auth) {
        validatePost(r);
        return discussions.create((UUID)auth.getPrincipal(),validSymbol(symbol),r);
    }
    @PatchMapping("/{symbol}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String symbol,@PathVariable UUID id,@Valid @RequestBody PostRequest r,Authentication auth) {
        validatePost(r);
        if(!discussions.updatePost((UUID)auth.getPrincipal(),id,validSymbol(symbol),r))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @DeleteMapping("/{symbol}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String symbol,@PathVariable UUID id,Authentication auth) {
        if(!discussions.deletePost((UUID)auth.getPrincipal(),id,validSymbol(symbol)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private void requireVisiblePost(String symbol,UUID id) {
        if(!discussions.visible(validSymbol(symbol),id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private static void validatePost(PostRequest r) {
        if(r.fontSize()<14||r.fontSize()>24||!r.isImageValid())throw new IllegalArgumentException("Invalid post format");
    }
    private static String validSymbol(String s) { if(s==null||!s.matches("[A-Z0-9]{2,20}"))throw new IllegalArgumentException("Invalid symbol"); return s; }

    public record PostRequest(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=1000) String content,
        @NotBlank @Pattern(regexp="system|serif|mono") String fontFamily,@NotNull Integer fontSize,
        @NotBlank @Pattern(regexp="left|center|right") String textAlign,
        @Pattern(regexp="image/png|image/jpeg|image/webp") String imageType,@Size(max=524288) byte[] imageData) {
        public boolean isImageValid() {
            if(imageData==null&&imageType==null)return true;
            if(imageData==null||imageType==null||imageData.length<12)return false;
            return switch(imageType) {
                case "image/png" -> (imageData[0]&255)==0x89&&imageData[1]=='P'&&imageData[2]=='N'&&imageData[3]=='G';
                case "image/jpeg" -> (imageData[0]&255)==0xff&&(imageData[1]&255)==0xd8&&(imageData[2]&255)==0xff;
                case "image/webp" -> imageData[0]==0x52&&imageData[1]==0x49&&imageData[2]==0x46&&imageData[3]==0x46&&imageData[8]==0x57&&imageData[9]==0x45&&imageData[10]==0x42&&imageData[11]==0x50;
                default -> false;
            };
        }
    }
    public record CommentRequest(@NotBlank @Size(max=500) String content) {}
    public record VoteRequest(@NotBlank @Pattern(regexp="UP|DOWN") String direction) {}
    public record ReportRequest(@NotBlank @Pattern(regexp="SPAM|ABUSE|PERSONAL_INFO|OTHER") String reason,@Size(max=500) String details) {}
}