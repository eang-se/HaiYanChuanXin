package cn.haiyan.platform.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** The remote call is outside the transaction; the saved snapshot is revalidated under lock. */
@Service
public class ArticlePublicationService {
    private final CommunityService community;
    private final AiArticleModeration ai;
    private final ArticleReviewQuota quota;
    public ArticlePublicationService(CommunityService community,AiArticleModeration ai,ArticleReviewQuota quota){this.community=community;this.ai=ai;this.quota=quota;}
    public Map<String,Object> save(Long id,String username,CommunityService.WorkInput input){
        if(!input.publish())return community.save(id,username,input);
        var snapshot=community.prepareForAi(id,username,input);
        var document=WorkHtml.clean(snapshot.body());
        AiArticleModeration.Result result;
        List<AiArticleModeration.Image> images;
        try{images=community.prepareAiImages(id,username,snapshot);}
        catch(ResponseStatusException e){if(e.getStatusCode().value()!=503)throw e;throw new ArticleModerationWarning(List.of(),document,"图片暂不可用，可保存草稿或提交人工复核。");}
        quota.reserve(username,id,snapshot,false);
        try{result=ai.assess(document,snapshot.fields(),images);}
        catch(ResponseStatusException e){if(e.getStatusCode().value()!=503)throw e;throw new ArticleModerationWarning(List.of(),document,"自动审核暂不可用，可保存草稿或提交人工复核。");}
        if(result.review())throw new ArticleModerationWarning(result.locations(),document,"自动审核提示文章可能需要复核，请修改后重试或提交人工复核。");
        return community.saveWithAi(id,username,input,snapshot);
    }
}
