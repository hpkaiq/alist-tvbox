package cn.har01d.alist_tvbox.service.metadata;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * 元数据 provider 共用的 HTTP 客户端工厂。
 * 必须用 {@link RestTemplateBuilder} 构建:Spring Boot 4 的 Jackson2 支持在 spring-boot-jackson2 模块里,
 * 只有 builder 构建的 RestTemplate 才带自动配置的消息转换器 —— 裸 new RestTemplate() 无法序列化
 * ObjectNode/反序列化 JsonNode(表现为 "Type definition error: JsonNode",provider 全部静默空结果)。
 * 同时统一带超时(外部平台挂起不能卡死巡检线程)与项目的 SimpleClientHttpRequestFactory 定制。
 */
@Component
public class MetadataHttp {
    private final RestTemplateBuilder builder;

    public MetadataHttp(RestTemplateBuilder builder) {
        this.builder = builder;
    }

    public RestTemplate create() {
        return create(Duration.ofSeconds(15));
    }

    /** 长轮询等特殊调用需要更长的读超时(如 Telegram getUpdates 的 25s 挂起)。 */
    public RestTemplate create(Duration readTimeout) {
        return create(readTimeout, null);
    }

    /** 带出站代理版(JVM 不读 HTTP_PROXY 环境变量,需显式挂 Proxy):proxyUrl 形态见 {@link #parseProxy}。 */
    public RestTemplate create(Duration readTimeout, String proxyUrl) {
        RestTemplateBuilder base = builder != null ? builder : new RestTemplateBuilder();
        RestTemplate template = base
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(readTimeout)
                .build();
        // builder 配的超时会被 RestTemplateConfig 全局 customizer(60s 地板)的 setRequestFactory 覆盖
        // (customizer 在 build 时运行,JDK factory 无超时 getter 可透传)—— build 后自设 Simple
        // factory 收回主动权:消息转换器不受影响,巡检线程对挂起平台最多等 readTimeout 而非 60s×N 请求
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        Proxy proxy = parseProxy(proxyUrl);
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        template.setRequestFactory(factory);
        return template;
    }

    /**
     * 解析出站代理地址:{@code http://host:port} / {@code https://host:port} / {@code socks5://host:port}
     * (socks/socks5h 同 SOCKS);裸 {@code host:port} 默认 http 代理;空白返回 null(直连)。
     * <p>
     * 格式非法抛 {@link IllegalArgumentException} 而非静默回落直连 —— 墙内部署配错代理若静默直连,
     * 表现为「配了代理还是不通」无从排查;让错误直接出现在轮询日志里。
     * 不支持带用户名密码的形态(JDK {@link Proxy} 不携带凭据,Authenticator.setDefault 是全局污染)。
     */
    public static Proxy parseProxy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String spec = value.trim();
        Proxy.Type type = Proxy.Type.HTTP;
        int schemeEnd = spec.indexOf("://");
        if (schemeEnd > 0) {
            String scheme = spec.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
            switch (scheme) {
                case "http", "https" -> type = Proxy.Type.HTTP;
                case "socks", "socks5", "socks5h" -> type = Proxy.Type.SOCKS;
                default -> throw new IllegalArgumentException("不支持的代理协议: " + scheme);
            }
            spec = spec.substring(schemeEnd + 3);
        }
        if (spec.lastIndexOf('@') >= 0) {
            throw new IllegalArgumentException("代理不支持用户名密码认证: " + value.trim());
        }
        URI uri;
        try {
            uri = URI.create("http://" + spec);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("代理地址无效: " + value.trim());
        }
        if (uri.getHost() == null || uri.getPort() == -1) {
            throw new IllegalArgumentException("代理地址无效(应为 host:port): " + value.trim());
        }
        return new Proxy(type, new InetSocketAddress(uri.getHost(), uri.getPort()));
    }

    /**
     * JDK HttpClient 版(经 ALPN 协商 HTTP/2):腾讯 pbaccess 网关按<b>连接层</b>歧视 ——
     * HttpURLConnection(Simple factory,无 ALPN 的 HTTP/1.1)发 MbSearch/GetPageData 恒回
     * {@code {"ret":20607,"msg":"unknow error."}},curl 与 java.net.http(HTTP/2)放行;
     * 同一个 JDK TLS 栈 h2 即过,不是 TLS 指纹问题。腾讯系(MbSearch 分季表/官方条目搜索)一律
     * 走此客户端(2026-09-01 线上实证:一念永恒 sub 66 分季对齐静默失败的根因)。
     */
    public RestTemplate createJdk() {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(15));
        RestTemplate template = (builder != null ? builder : new RestTemplateBuilder()).build();
        template.setRequestFactory(factory); // 与 create() 同规:build 后自设 factory,超时主动权在手
        return template;
    }
}
