package cn.haiyan.platform.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/** Offline JSONL validator used by the article conversion tool; does not connect to a database. */
public final class DocumentMigrationValidator {
    private DocumentMigrationValidator() {}
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var json=new ObjectMapper(); boolean failed=false; int count=0;
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            for(String line;(line=input.readLine())!=null;) {
                if(line.isBlank())continue;
                try {
                    var row=json.readValue(line,Map.class);
                    String body=(String)row.get("body"),hash=(String)row.get("sha256");
                    if(body==null||hash==null||!(row.get("document") instanceof Map<?,?>))throw new IllegalArgumentException();
                    String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
                    if(!actual.equals(hash))throw new IllegalArgumentException();
                    WorkDocument.validate((Map<String,Object>)row.get("document"),WorkHtml.clean(body).body().html());
                    count++;
                } catch(Exception ex) {failed=true;break;}
            }
        }
        if(failed||count==0){System.err.println("Document validation failed; no database changes made.");System.exit(1);}
        System.out.println("Validated "+count+" documents.");
    }
}
