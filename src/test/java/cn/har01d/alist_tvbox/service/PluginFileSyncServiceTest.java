package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.entity.Plugin;
import cn.har01d.alist_tvbox.entity.PluginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 自定义网页源文件双向同步(static/webhome/pages/*.html 自动注册/删除),
 * 与 .py 插件同款幂等收敛;base 路径注入临时目录,不依赖运行目录。
 * repository 查询未打桩时默认空列表(Mockito ReturnsEmptyValues)。
 */
@ExtendWith(MockitoExtension.class)
class PluginFileSyncServiceTest {

    @TempDir
    Path staticRoot;

    @Mock
    private PluginService pluginService;
    @Mock
    private PluginRepository pluginRepository;
    @Mock
    private SubscriptionSourceService subscriptionSourceService;

    private PluginFileSyncService service;

    @BeforeEach
    void setUp() {
        service = new PluginFileSyncService(pluginService, pluginRepository, subscriptionSourceService);
    }

    @Test
    void reconcileCreatesDirectoriesWhenMissing() {
        // 服务启动(onApplicationReady→reconcile)即自动创建 plugins 与 webhome/pages 目录:
        // 静态文件页可见、可直接进入上传,无需先手动建目录
        service.reconcile(staticRoot);

        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(staticRoot.resolve("webhome/pages")));
        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(staticRoot.resolve("plugins")));
    }

    @Test
    void htmlUploadRegistersWebPageSource() throws Exception {
        Files.createDirectories(staticRoot.resolve("webhome/pages"));
        Files.writeString(staticRoot.resolve("webhome/pages/电影库.html"), "<html></html>");

        service.reconcile(staticRoot);

        // url 身份为 /static/ 前缀,名称取文件名(可中文)
        verify(pluginService).upsertWebPage("/static/webhome/pages/电影库.html", "电影库");
        verifyNoMoreInteractions(pluginService);
    }

    @Test
    void nestedHtmlRegisteredAndNonHtmlIgnored() throws Exception {
        Files.createDirectories(staticRoot.resolve("webhome/pages/sub"));
        Files.writeString(staticRoot.resolve("webhome/pages/foo.html"), "<html></html>");
        Files.writeString(staticRoot.resolve("webhome/pages/sub/bar.html"), "<html></html>");
        Files.writeString(staticRoot.resolve("webhome/pages/readme.txt"), "text");

        service.reconcile(staticRoot);

        // 递归扫描 .html;.txt 不注册
        verify(pluginService).upsertWebPage("/static/webhome/pages/foo.html", "foo");
        verify(pluginService).upsertWebPage("/static/webhome/pages/sub/bar.html", "bar");
        verifyNoMoreInteractions(pluginService);
    }

    @Test
    void deletedFileRemovesStaleWebPage() throws Exception {
        Plugin stale = new Plugin();
        stale.setId(7);
        stale.setUrl("/static/webhome/pages/gone.html");
        when(pluginRepository.findByUrlStartingWithOrderBySortOrderAscIdAsc(PluginService.FILE_PLUGIN_URL_PREFIX))
                .thenReturn(List.of());
        when(pluginRepository.findByUrlStartingWithOrderBySortOrderAscIdAsc(PluginService.WEB_PAGE_URL_PREFIX))
                .thenReturn(List.of(stale));

        service.reconcile(staticRoot);

        // 目录自建后无该文件 → 残留条目删除
        verify(pluginService).delete(7);
        verify(pluginService, never()).upsertWebPage(anyString(), anyString());
    }

    @Test
    void rescanKeepsRegisteredWebPage() throws Exception {
        Files.createDirectories(staticRoot.resolve("webhome/pages"));
        Files.writeString(staticRoot.resolve("webhome/pages/foo.html"), "<html></html>");
        Plugin existing = new Plugin();
        existing.setId(3);
        existing.setUrl("/static/webhome/pages/foo.html");
        when(pluginRepository.findByUrlStartingWithOrderBySortOrderAscIdAsc(PluginService.FILE_PLUGIN_URL_PREFIX))
                .thenReturn(List.of());
        when(pluginRepository.findByUrlStartingWithOrderBySortOrderAscIdAsc(PluginService.WEB_PAGE_URL_PREFIX))
                .thenReturn(List.of(existing));
        when(pluginService.upsertWebPage(anyString(), anyString())).thenReturn(existing);

        service.reconcile(staticRoot);

        // 文件仍在 → 幂等 upsert,不删除
        verify(pluginService).upsertWebPage("/static/webhome/pages/foo.html", "foo");
        verify(pluginService, never()).delete(anyInt());
    }
}
