package cn.har01d.alist_tvbox.service.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 代理地址解析矩阵 + 带/不带代理的工厂档位互不影响。 */
class MetadataHttpTest {

    @Test
    void blankValueMeansDirect() {
        assertNull(MetadataHttp.parseProxy(null));
        assertNull(MetadataHttp.parseProxy(""));
        assertNull(MetadataHttp.parseProxy("   "));
    }

    @Test
    void httpSchemeParsesHostPort() {
        Proxy proxy = MetadataHttp.parseProxy("http://192.168.1.2:7890");
        assertEquals(Proxy.Type.HTTP, proxy.type());
        var address = (java.net.InetSocketAddress) proxy.address();
        assertEquals("192.168.1.2", address.getHostString());
        assertEquals(7890, address.getPort());
    }

    @Test
    void httpsSchemeIsHttpTunnel() {
        assertEquals(Proxy.Type.HTTP, MetadataHttp.parseProxy("https://proxy.example.com:3128").type());
    }

    @Test
    void bareHostPortDefaultsToHttp() {
        Proxy proxy = MetadataHttp.parseProxy("127.0.0.1:7890");
        assertEquals(Proxy.Type.HTTP, proxy.type());
        assertEquals(7890, ((java.net.InetSocketAddress) proxy.address()).getPort());
    }

    @Test
    void socksSchemesMapToSocksType() {
        assertEquals(Proxy.Type.SOCKS, MetadataHttp.parseProxy("socks5://127.0.0.1:1080").type());
        assertEquals(Proxy.Type.SOCKS, MetadataHttp.parseProxy("socks5h://127.0.0.1:1080").type());
        assertEquals(Proxy.Type.SOCKS, MetadataHttp.parseProxy("socks://127.0.0.1:1080").type());
    }

    @Test
    void unsupportedSchemeRejected() {
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("socks4://127.0.0.1:1080"));
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("vmess://uuid@host:443"));
    }

    @Test
    void userinfoRejected() {
        // JDK Proxy 不携带凭据;静默忽略凭据会表现为「能建实例但连不上」,直接拒绝更可见
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("http://user:pass@127.0.0.1:7890"));
    }

    @Test
    void missingPortOrHostRejected() {
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("http://127.0.0.1"));
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("http://:7890"));
        assertThrows(IllegalArgumentException.class, () -> MetadataHttp.parseProxy("not a proxy"));
    }

    @Test
    void createBuildsUsableTemplateWithAndWithoutProxy() {
        MetadataHttp factory = new MetadataHttp(null);
        RestTemplate direct = factory.create(Duration.ofSeconds(15), null);
        RestTemplate proxied = factory.create(Duration.ofSeconds(15), "http://127.0.0.1:7890");
        assertNotNull(direct);
        assertNotNull(proxied);
        assertNotNull(proxied.getRequestFactory());
    }

    @Test
    void createLegacyOverloadStillWorks() {
        assertNotNull(new MetadataHttp(null).create(Duration.ofSeconds(35)));
    }
}
