package cn.haiyan.platform.web;
import java.util.Map;
import cn.haiyan.platform.service.ArticleModerationWarning;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> business(ResponseStatusException ex) { if(ex instanceof ArticleModerationWarning warning)return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",warning.getReason(),"locations",warning.locations(),"structure",warning.structure())); return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"请求失败":ex.getReason())); }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<?> duplicate() { return ResponseEntity.badRequest().body(Map.of("message","名称或账号已存在，请换一个再试")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge() { return ResponseEntity.badRequest().body(Map.of("message","文件超过大小限制（20 MB）")); }
}
