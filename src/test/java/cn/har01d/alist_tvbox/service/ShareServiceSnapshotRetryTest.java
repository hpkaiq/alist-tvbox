package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.config.AppProperties;
import cn.har01d.alist_tvbox.entity.AListAliasRepository;
import cn.har01d.alist_tvbox.entity.AccountRepository;
import cn.har01d.alist_tvbox.entity.DriverAccountRepository;
import cn.har01d.alist_tvbox.entity.MetaRepository;
import cn.har01d.alist_tvbox.entity.SettingRepository;
import cn.har01d.alist_tvbox.entity.Share;
import cn.har01d.alist_tvbox.entity.ShareRepository;
import cn.har01d.alist_tvbox.entity.SiteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 115 新建分享秒挂的快照竞态:enable(init 列分享根目录)在服务端快照未生成完时报
 * 「正在生成文件快照」,须退避重试等就绪;其它错误不受影响(追剧自有分享首批/补批挂载链路)。
 */
class ShareServiceSnapshotRetryTest {

    private static final String SNAPSHOT_PENDING =
            "failed load storage: failed init storage: /追剧/测试剧: 正在生成文件快照";

    private ShareService service;

    @BeforeEach
    void setUp() {
        AListLocalService aListLocalService = mock(AListLocalService.class);
        when(aListLocalService.getInternalPort()).thenReturn(4567);

        service = spy(new ShareService(
                mock(AppProperties.class),
                mock(ShareRepository.class),
                mock(MetaRepository.class),
                mock(AListAliasRepository.class),
                mock(SettingRepository.class),
                mock(SiteRepository.class),
                mock(AccountRepository.class),
                mock(DriverAccountRepository.class),
                mock(AListService.class),
                mock(DriverAccountService.class),
                mock(AccountService.class),
                aListLocalService,
                mock(ConfigFileService.class),
                mock(PikPakService.class),
                mock(OfflineDownloadService.class),
                new RestTemplateBuilder(),
                mock(Environment.class),
                new ObjectMapper(),
                mock(UserService.class)));
        // 重试节奏换成毫秒级,免测试真实 sleep(生产默认 3s~30s 指数退避)
        doReturn(new long[]{1L}).when(service).snapshotRetryDelayMillis();
    }

    private static Share selfShare() {
        Share share = new Share();
        share.setType(8);
        share.setShareId("swsaz3hjs");
        share.setPassword("6666");
        share.setPath("/追剧/测试剧");
        return share;
    }

    /** 快照就绪后重试成功:第二次 enable 返回空,挂载成功、行上无错误。 */
    @Test
    void retriesEnableUntilSnapshotReady() {
        doReturn(SNAPSHOT_PENDING).doReturn(null).when(service).enableStorage(anyInt(), any());

        Share share = service.create(selfShare());

        assertThat(share.getError()).isNull();
        verify(service, times(2)).enableStorage(anyInt(), any());
    }

    /** 重试耗尽仍快照未就绪:保留错误原样上抛(调用方按失败善后),不无限等。 */
    @Test
    void keepsErrorAfterRetriesExhausted() {
        doReturn(new long[]{1L, 1L}).when(service).snapshotRetryDelayMillis();
        doReturn(SNAPSHOT_PENDING).when(service).enableStorage(anyInt(), any());

        Share share = service.create(selfShare());

        assertThat(share.getError()).contains("正在生成文件快照");
        verify(service, times(3)).enableStorage(anyInt(), any()); // 首次 + 2 轮重试
    }

    /** 非快照错误(真失效等)不重试,一次即返 —— 重试只针对已识别的瞬时态。 */
    @Test
    void otherErrorsPassThroughWithoutRetry() {
        doReturn("failed init storage: /追剧/测试剧: 分享地址已失效").when(service).enableStorage(anyInt(), any());

        Share share = service.create(selfShare());

        assertThat(share.getError()).contains("分享地址已失效");
        verify(service, times(1)).enableStorage(anyInt(), any());
        assertThat(ShareService.isSnapshotPending(null)).isFalse();
        assertThat(ShareService.isSnapshotPending("分享地址已失效")).isFalse();
        assertThat(ShareService.isSnapshotPending(SNAPSHOT_PENDING)).isTrue();
    }
}
