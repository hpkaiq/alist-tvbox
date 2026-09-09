package cn.har01d.alist_tvbox.web;

import cn.har01d.alist_tvbox.service.PluginService;
import cn.har01d.alist_tvbox.service.SubscriptionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PluginContentController {
    private final SubscriptionService subscriptionService;
    private final PluginService pluginService;

    public PluginContentController(SubscriptionService subscriptionService, PluginService pluginService) {
        this.subscriptionService = subscriptionService;
        this.pluginService = pluginService;
    }

    @GetMapping(value = "/plugins/{token}/{id}.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String content(@PathVariable String token, @PathVariable Integer id) {
        subscriptionService.checkToken(token);
        return pluginService.readContent(id);
    }

    @GetMapping(value = "/plugins/{token}/{id}.py", produces = "text/x-python;charset=UTF-8")
    public String pythonContent(@PathVariable String token, @PathVariable Integer id) {
        subscriptionService.checkToken(token);
        return pluginService.readContent(id);
    }

    /**
     * 插件预热清单:启用插件的密文地址(常用在前),供客户端冷启动后台预下载。
     * 鉴权与密文端点同款 checkToken;路径避开 /plugins/{token} 前缀,防止名为
     * preheat 的订阅 token 与 /plugins/preheat/{id}.txt 产生歧义。
     */
    @GetMapping(value = "/plugin-preheat/{token}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> preheat(@PathVariable String token) {
        subscriptionService.checkToken(token);
        return subscriptionService.buildPreheatManifest();
    }
}
