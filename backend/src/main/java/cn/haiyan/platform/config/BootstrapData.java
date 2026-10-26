package cn.haiyan.platform.config;
import cn.haiyan.platform.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapData implements ApplicationRunner {
    private final CommunityService s;private final PasswordEncoder encoder;private final String password;
    public BootstrapData(CommunityService s,PasswordEncoder encoder,@Value("${platform.bootstrap.password:}")String password){this.s=s;this.encoder=encoder;this.password=password;}
    @Override @Transactional public void run(ApplicationArguments args){
        if(s.store().count("SELECT COUNT(*) FROM members")>0)return;
        if(password.length()<16)throw new IllegalStateException("Run tools/Prepare-Community.ps1 to generate the initial admin credentials.");
        s.store().insert("INSERT INTO members(username,password_hash,pen_name,bio,role) VALUES(?,?,?,?,?)","admin",encoder.encode(password),"海燕编辑部","收集校园里的微光，也等待你的第一封来信。","ADMIN");
        seed("把秋天折进书页","诗歌","一枚落叶，是秋天写给书页的信。","<p>风经过走廊，<br>把一枚金黄的逗号<br>留在摊开的书页上。</p><p>我们还没学会告别，<br>树已把整年的心事<br>轻轻交给了大地。</p><p>放学的铃声响起，<br>我合上书——<br>于是，口袋里多了一整个秋天。</p>",2);
        seed("窗边，有风经过","散文","有些平常的下午，因为一阵风而变得值得记住。","<p>图书馆最里面的那扇窗，总比别处早一点接住下午的光。桌面上有很浅的木纹，像一条没有画完的河。</p><p>我把书翻到昨天停下来的地方，却没有急着读。窗外有人经过，鞋底与石板轻轻相碰；更远处，操场上的呼喊被风揉成模糊的一团。世界照常热闹，这一小块安静却恰好属于我。</p><p>后来，风掀起了书页。我伸手按住它，忽然觉得，所谓值得记录的生活，也许并不总需要一件大事。有一扇开着的窗，有一本尚未读完的书，还有愿意停留的自己，就已经足够。</p>",1);
        seed("给尚未落笔的你","随笔","不必等到准备好，第一句话可以从今天开始。","<p>你可能有一个一直没有讲完的故事，或者一句在心里停留很久的话。它们不必马上变成一篇完美的作品。</p><p>先写下来吧。写今天的天空，写放学路上的声音，写一场没有说出口的告别。让第一句话带你走到第二句，再慢慢走远。</p><blockquote><p>文字最初的意义，也许只是替我们保留那些不愿忘记的瞬间。</p></blockquote><p>在这里，每一种体裁都有位置，每一个认真表达的人都值得被听见。我们等你的来信。</p>",3);
    }
    private void seed(String title,String genre,String excerpt,String body,long tag){var work=s.save(null,"admin",new CommunityService.WorkInput(title,genre,excerpt,body,"",java.util.List.of(tag),true));s.store().update("UPDATE works SET is_demo=TRUE WHERE id=?",work.get("id"));}
}
