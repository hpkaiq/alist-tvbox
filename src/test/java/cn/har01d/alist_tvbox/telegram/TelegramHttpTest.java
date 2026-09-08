package cn.har01d.alist_tvbox.telegram;

import cn.har01d.alist_tvbox.entity.Setting;
import cn.har01d.alist_tvbox.service.SettingService;
import cn.har01d.alist_tvbox.service.metadata.MetadataHttp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 出站代理热切换:设置变化换新实例、同配置复用缓存、超时档位隔离、设置源抖动不断流。 */
class TelegramHttpTest {

    private static final Duration BOT_TIMEOUT = Duration.ofSeconds(35);
    private static final Duration NOTIFY_TIMEOUT = Duration.ofSeconds(15);

    private final SettingService settingService = mock(SettingService.class);
    private final MetadataHttp metadataHttp = mock(MetadataHttp.class);
    private TelegramHttp http;

    @BeforeEach
    void setUp() {
        http = new TelegramHttp(metadataHttp, settingService);
    }

    private void stubProxy(String value) {
        when(settingService.get(TelegramHttp.PROXY_KEY)).thenReturn(value == null ? null : new Setting(TelegramHttp.PROXY_KEY, value));
    }

    @Test
    void cachesPerTimeoutAndReusesWhileSettingUnchanged() {
        stubProxy(null);
        RestTemplate bot = mock(RestTemplate.class);
        RestTemplate notify = mock(RestTemplate.class);
        when(metadataHttp.create(BOT_TIMEOUT, null)).thenReturn(bot);
        when(metadataHttp.create(NOTIFY_TIMEOUT, null)).thenReturn(notify);

        assertSame(bot, http.get(BOT_TIMEOUT));
        assertSame(bot, http.get(BOT_TIMEOUT));
        assertSame(notify, http.get(NOTIFY_TIMEOUT));
        verify(metadataHttp, times(1)).create(BOT_TIMEOUT, null);
        verify(metadataHttp, times(1)).create(NOTIFY_TIMEOUT, null);
    }

    @Test
    void proxyChangeRebuildsAllTimeouts() {
        stubProxy(null);
        RestTemplate direct = mock(RestTemplate.class);
        when(metadataHttp.create(BOT_TIMEOUT, null)).thenReturn(direct);
        assertSame(direct, http.get(BOT_TIMEOUT));

        stubProxy("http://127.0.0.1:7890");
        RestTemplate proxied = mock(RestTemplate.class);
        when(metadataHttp.create(BOT_TIMEOUT, "http://127.0.0.1:7890")).thenReturn(proxied);
        assertSame(proxied, http.get(BOT_TIMEOUT));
        verify(metadataHttp).create(BOT_TIMEOUT, "http://127.0.0.1:7890");

        // 代理清空回到直连:同样要换新实例,不能停留在代理版
        stubProxy("");
        when(metadataHttp.create(BOT_TIMEOUT, null)).thenReturn(direct);
        assertSame(direct, http.get(BOT_TIMEOUT));
    }

    @Test
    void settingReadFailureKeepsLastSnapshot() {
        stubProxy("http://127.0.0.1:7890");
        RestTemplate proxied = mock(RestTemplate.class);
        when(metadataHttp.create(NOTIFY_TIMEOUT, "http://127.0.0.1:7890")).thenReturn(proxied);
        assertSame(proxied, http.get(NOTIFY_TIMEOUT));

        when(settingService.get(TelegramHttp.PROXY_KEY)).thenThrow(new RuntimeException("db down"));
        assertSame(proxied, http.get(NOTIFY_TIMEOUT));
        verify(metadataHttp, times(1)).create(any(), any());
    }

    @Test
    void blankValueIsDirect() {
        stubProxy("   ");
        RestTemplate direct = mock(RestTemplate.class);
        when(metadataHttp.create(BOT_TIMEOUT, null)).thenReturn(direct);
        assertSame(direct, http.get(BOT_TIMEOUT));
    }

    @Test
    void fixedStubAlwaysReturnedWithoutTouchingSettings() {
        RestTemplate fixed = mock(RestTemplate.class);
        TelegramHttp stub = new TelegramHttp(fixed);
        assertSame(fixed, stub.get(BOT_TIMEOUT));
        assertSame(fixed, stub.get(NOTIFY_TIMEOUT));
        verify(metadataHttp, times(0)).create(any(), any());
        verify(settingService, times(0)).get(any());
    }

    @Test
    void invalidProxyPropagatesForVisibility() {
        // 配错代理不能静默回落直连(墙内部署会表现为「配了还是不通」),让轮询日志直接暴露配错
        stubProxy("http://127.0.0.1");
        when(metadataHttp.create(any(), any()))
                .thenThrow(new IllegalArgumentException("代理地址无效(应为 host:port): http://127.0.0.1"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> http.get(BOT_TIMEOUT));
    }
}
