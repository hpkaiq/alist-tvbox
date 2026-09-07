package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.entity.Setting;
import cn.har01d.alist_tvbox.entity.SettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebHome 客户端能力记忆:哪些 token 对应的客户端支持 WebHome 自定义网页首页
 * (webhtv / fish 等魔改端,原版 FongMi 不支持)。
 * <p>
 * 配置拉取请求无法被动区分客户端(fish 与原版 FongMi 包名同为 com.fongmi.android.tv,
 * 且拉配置都不带特征 UA/头),能力信号来自 spider 运行时探测:spring.jar 在宿主进程内
 * {@code Class.forName} 探测 WebHome 桥接类后,经 X-CLIENT-CAPS 头随 /media 请求上报,
 * 在此按 token 记忆(落 Setting 表,重启保留)。订阅配置生成时按 token 门禁注入
 * homePage 站点 —— 原版 FongMi 收到 csp_Builtin 站点会解析失败,绝不能无差别下发。
 */
@Slf4j
@Service
public class WebHomeService {
    /** 已知独占包名直接判定(webhtv);fish 与原版同包名,只能靠 spider 探测头。 */
    private static final Set<String> KNOWN_PACKAGES = Set.of("com.silent.android.webhtv");
    private static final String SETTING_KEY = "webhome_capable_tokens";
    private static final String BLANK_TOKEN_KEY = "-";

    private final SettingRepository settingRepository;
    private final Set<String> capable = ConcurrentHashMap.newKeySet();

    public WebHomeService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    void load() {
        settingRepository.findById(SETTING_KEY).ifPresent(s -> {
            for (String token : s.getValue().split(",")) {
                if (StringUtils.isNotBlank(token)) {
                    capable.add(token.trim());
                }
            }
        });
    }

    /** spider/客户端请求到达时记录能力;caps 头缺失但包名已知独占的也记。 */
    public void recordCapability(String token, String clientCaps, String clientPackage) {
        boolean webhome = "webhome".equalsIgnoreCase(clientCaps)
                || (clientPackage != null && KNOWN_PACKAGES.contains(clientPackage));
        if (!webhome) {
            return;
        }
        String key = StringUtils.isBlank(token) ? BLANK_TOKEN_KEY : token;
        if (capable.add(key)) {
            settingRepository.findById(SETTING_KEY).ifPresentOrElse(
                    s -> settingRepository.save(new Setting(SETTING_KEY, s.getValue() + "," + key)),
                    () -> settingRepository.save(new Setting(SETTING_KEY, key)));
            log.info("WebHome capable client recorded: token={} package={}", key, clientPackage);
        }
    }

    public boolean isCapable(String token) {
        return capable.contains(StringUtils.isBlank(token) ? BLANK_TOKEN_KEY : token);
    }
}
