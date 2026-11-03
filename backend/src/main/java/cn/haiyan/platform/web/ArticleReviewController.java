package cn.haiyan.platform.web;

import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import cn.haiyan.platform.service.ArticleReviewService;
import cn.haiyan.platform.service.CommunityService.WorkInput;

@RestController
public class ArticleReviewController {
 private final ArticleReviewService reviews;
 public ArticleReviewController(ArticleReviewService reviews){this.reviews=reviews;}
 @PostMapping("/api/member/article-reviews") public Map<String,Object> submitNew(Principal p,@RequestBody WorkInput input){return reviews.submit(null,p.getName(),input);}
 @PostMapping("/api/member/works/{id}/article-reviews") public Map<String,Object> submit(@PathVariable long id,Principal p,@RequestBody WorkInput input){return reviews.submit(id,p.getName(),input);}
 @GetMapping("/api/member/works/{id}/article-review") public Map<String,Object> latest(@PathVariable long id,Principal p){return reviews.latest(id,p.getName());}
 @GetMapping("/api/admin/article-reviews") public List<Map<String,Object>> pending(){return reviews.pending();}
 @GetMapping("/api/admin/article-reviews/{id}") public Map<String,Object> detail(@PathVariable long id){return reviews.detail(id);}
 public record Decision(boolean approve,String note){}
 @PostMapping("/api/admin/article-reviews/{id}/decision") public void decide(@PathVariable long id,Principal p,@RequestBody Decision decision){reviews.decide(id,p.getName(),decision.approve(),decision.note());}
}
