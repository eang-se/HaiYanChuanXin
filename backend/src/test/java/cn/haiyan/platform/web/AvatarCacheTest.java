package cn.haiyan.platform.web;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarCacheTest {
    @Test void expiresEntriesAndEvictsLeastRecentlyUsedWithinByteLimit(){
        var time=new AtomicLong();var cache=new AvatarCache(Duration.ofSeconds(10),6,4,time::get);
        byte[] one={1,2,3},two={4,5,6},three={7,8,9};
        cache.put("one",one);cache.put("two",two);assertArrayEquals(one,cache.get("one"));
        cache.put("three",three);assertNull(cache.get("two"));assertArrayEquals(one,cache.get("one"));
        cache.put("large",new byte[5]);assertNull(cache.get("large"));
        time.set(Duration.ofSeconds(10).toNanos());assertNull(cache.get("one"));assertNull(cache.get("three"));
    }
}
