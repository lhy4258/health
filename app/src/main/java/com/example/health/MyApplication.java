package com.example.health;

import android.app.Application;
import cn.leancloud.LeanCloud;
import cn.leancloud.LCInstallation;
import cn.leancloud.LCObject;
import cn.leancloud.LCUser;
import com.example.health.data.model.Comment;
import com.example.health.data.model.DietRecord;
import com.example.health.data.model.LoginAttempt;
import com.example.health.data.model.Moment;
import com.example.health.data.model.PlanRecord;
import com.example.health.data.model.SportRecord;
import com.example.health.data.model.Album;
import com.example.health.data.model.Photo;
import com.example.health.data.model.User;
import com.example.health.data.model.Favorite;
import com.example.health.data.model.LikeDailyRecord;
import com.example.health.data.model.ProfileLike;

/**
 * 应用程序入口，负责 LeanCloud SDK 初始化及所有自定义数据模型的子类注册。
 * 在 AndroidManifest.xml 中声明为 application 节点，应用启动时最先执行。
 */
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        LCObject.registerSubclass(LoginAttempt.class);
        LCObject.registerSubclass(SportRecord.class);
        LCObject.registerSubclass(Moment.class);
        LCObject.registerSubclass(Comment.class);
        LCObject.registerSubclass(PlanRecord.class);
        LCObject.registerSubclass(DietRecord.class);
        LCObject.registerSubclass(Album.class);
        LCObject.registerSubclass(Photo.class);
        LCObject.registerSubclass(Favorite.class);
        LCObject.registerSubclass(LikeDailyRecord.class);
        LCObject.registerSubclass(ProfileLike.class);
        LCUser.registerSubclass(User.class);

        LeanCloud.initialize(this, "KIgcDd7N9oLbac7NSEfiVlVF-gzGzoHsz", "CqTupU2ipJtvzl8YirbEz3O0", "https://kigcdd7n.lc-cn-n1-shared.com");
        LCInstallation.getCurrentInstallation().saveInBackground();
    }
}
