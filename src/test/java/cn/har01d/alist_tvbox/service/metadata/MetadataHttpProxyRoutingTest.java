package cn.har01d.alist_tvbox.service.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 端到端 smoke(离线):代理设置必须真实改变请求路由 —— 本地假代理必须收到 CONNECT api.telegram.org:443。 */
class MetadataHttpProxyRoutingTest {

    @Test
    void proxiedRequestConnectsToProxy() throws Exception {
        try (ServerSocket proxyListener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                int port = proxyListener.getLocalPort();
                RestTemplate template = new MetadataHttp(null)
                        .create(Duration.ofSeconds(5), "http://127.0.0.1:" + port);
                // 请求线程:HTTPS 经 HTTP 代理先发 CONNECT,随后因本测试不回 200 而失败 —— 只关心连接到达
                var pending = executor.submit(() ->
                        template.getForObject("https://api.telegram.org/botX/getMe", String.class));
                proxyListener.setSoTimeout(10_000);
                try (Socket accepted = proxyListener.accept()) {
                    accepted.setSoTimeout(10_000);
                    String firstLine = new BufferedReader(new InputStreamReader(accepted.getInputStream())).readLine();
                    assertTrue(firstLine != null && firstLine.startsWith("CONNECT api.telegram.org:443"),
                            "proxy should receive CONNECT for telegram, got: " + firstLine);
                }
                pending.cancel(true);
            } finally {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
}
