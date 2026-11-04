package cn.haiyan.platform.web;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small, bounded cache of public QQ avatar bytes. Account lookup remains authoritative. */
final class AvatarCache {
    private record Entry(byte[] bytes,long expiresAt) {}
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,0.75f,true);
    private final long ttlNanos;
    private final int maxBytes,maxItemBytes;
    private final LongSupplier clock;
    private int bytes;
    AvatarCache(){this(Duration.ofMinutes(10),4*1024*1024,128*1024,System::nanoTime);}
    AvatarCache(Duration ttl,int maxBytes,int maxItemBytes,LongSupplier clock){this.ttlNanos=ttl.toNanos();this.maxBytes=maxBytes;this.maxItemBytes=maxItemBytes;this.clock=clock;}
    synchronized byte[] get(String key){
        Entry entry=entries.get(key);
        if(entry==null)return null;
        if(clock.getAsLong()-entry.expiresAt()>=0){entries.remove(key);bytes-=entry.bytes().length;return null;}
        return entry.bytes();
    }
    synchronized void put(String key,byte[] value){
        if(value.length>maxItemBytes||value.length>maxBytes)return;
        Entry previous=entries.remove(key);if(previous!=null)bytes-=previous.bytes().length;
        while(bytes+value.length>maxBytes||entries.size()>=256){Map.Entry<String,Entry> eldest=entries.entrySet().iterator().next();bytes-=eldest.getValue().bytes().length;entries.remove(eldest.getKey());}
        entries.put(key,new Entry(value,clock.getAsLong()+ttlNanos));bytes+=value.length;
    }
}
