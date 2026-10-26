package cn.haiyan.platform.service;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityStore {
    private final JdbcTemplate jdbc;
    public CommunityStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<Map<String,Object>> list(String sql,Object... args) {
        return jdbc.query(sql,(rs,n)-> {
            Map<String,Object> row=new LinkedHashMap<>();
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                String key=rs.getMetaData().getColumnLabel(i);
                StringBuilder camel=new StringBuilder(); boolean upper=false;
                for(char c:key.toCharArray()) { if(c=='_') upper=true; else { camel.append(upper?Character.toUpperCase(c):c); upper=false; } }
                Object value=rs.getObject(i);
                if(value instanceof Timestamp t) value=t.toInstant().toString();
                row.put(camel.toString(),value);
            }
            return row;
        },args);
    }
    public Map<String,Object> one(String sql,Object... args) { var rows=list(sql,args); return rows.isEmpty()?null:rows.getFirst(); }
    public int update(String sql,Object...args) { return jdbc.update(sql,args); }
    public long insert(String sql,Object...args) {
        var holder=new GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]); return s;},holder);
        return Objects.requireNonNull(holder.getKey()).longValue();
    }
    public long count(String sql,Object...args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
}
