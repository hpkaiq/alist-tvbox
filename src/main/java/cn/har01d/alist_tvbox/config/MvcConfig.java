package cn.har01d.alist_tvbox.config;

import cn.har01d.alist_tvbox.util.Utils;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/cat/**").addResourceLocations("file:" + Utils.getWebPath("cat") + "/");
        registry.addResourceHandler("/tvbox/**").addResourceLocations("file:" + Utils.getWebPath("tvbox") + "/");
        registry.addResourceHandler("/files/**").addResourceLocations("file:" + Utils.getWebPath("files") + "/");
        registry.addResourceHandler("/pg/**").addResourceLocations("file:" + Utils.getWebPath("pg") + "/");
        registry.addResourceHandler("/zx/**").addResourceLocations("file:" + Utils.getWebPath("zx") + "/");
        registry.addResourceHandler("/static/**").addResourceLocations("file:" + Utils.getWebPath("static") + "/");
        // WebHome 首页:static/webhome/ 下的同名文件(用户经文件页面-静态文件上传)覆盖内置页面,
        // 删除即回落 classpath 内置 app.html;zip 解压可带 css/js/图片等相对资源一并覆盖。
        registerWebHomeOverride(registry, Utils.getWebPath("static", "webhome"));
    }

    /**
     * /webhome/** 双位置映射:外部覆盖目录优先、classpath 内置页兜底(目录注入便于测试)。
     * homePage URL 固定不变且 WebView 对其有缓存,必须 no-cache 让覆盖更新即时可见(未变更走 304)。
     */
    static void registerWebHomeOverride(ResourceHandlerRegistry registry, Path overrideDir) {
        registry.addResourceHandler("/webhome/**")
                .addResourceLocations("file:" + overrideDir + "/",
                        "classpath:/static/webhome/")
                .setCacheControl(CacheControl.noCache());
    }
}
