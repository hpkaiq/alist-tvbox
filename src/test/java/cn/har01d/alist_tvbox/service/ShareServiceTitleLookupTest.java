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
import org.mockito.ArgumentCaptor;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 同一 (type, shareId) 可合法存在多行 Share(订阅挂载行与 temp 推送行/不同密码,
 * 唯一约束在 path 而非 shareId):标题恢复与持久化必须容忍重复行,
 * 不能因唯一行查询抛 IncorrectResultSizeDataAccessException 炸掉 /parse 推送。
 */
class ShareServiceTitleLookupTest {

    private ShareRepository shareRepository;
    private ShareService service;

    @BeforeEach
    void setUp() {
        shareRepository = mock(ShareRepository.class);
        AListLocalService aListLocalService = mock(AListLocalService.class);
        when(aListLocalService.getInternalPort()).thenReturn(4567);

        service = new ShareService(
                mock(AppProperties.class),
                shareRepository,
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
                mock(UserService.class));
    }

    private static Share shareRow(int id, String path, String title) {
        Share share = new Share();
        share.setId(id);
        share.setType(5);
        share.setShareId("abc123");
        share.setPath(path);
        share.setTitle(title);
        return share;
    }

    /** 订阅挂载行(有标题)+ temp 推送行(无标题)并存:取到有标题的行,不因 2 行结果抛异常。 */
    @Test
    void findShareTitleToleratesDuplicateShareIdRows() {
        Share mounted = shareRow(101, "/追剧/测试剧", "测试剧 第一季");
        Share temp = shareRow(102, "/temp/quark@abc123@", null);
        when(shareRepository.findByTypeAndShareId(5, "abc123")).thenReturn(List.of(temp, mounted));

        assertThat(service.findShareTitle("quark@abc123@")).isEqualTo("测试剧 第一季");
    }

    /** 全部行都无标题时返回 null(调用方回落到挂载目录名)。 */
    @Test
    void findShareTitleReturnsNullWhenNoRowHasTitle() {
        when(shareRepository.findByTypeAndShareId(5, "abc123"))
                .thenReturn(List.of(shareRow(101, "/a", ""), shareRow(102, "/b", null)));

        assertThat(service.findShareTitle("quark@abc123@")).isNull();
    }

    /** 标题持久化把新标题写到所有不一致的行,后续任一行命中都能恢复且各行口径一致。 */
    @Test
    void saveShareTitleUpdatesEveryMismatchedRow() {
        Share mounted = shareRow(101, "/追剧/测试剧", null);
        Share temp = shareRow(102, "/temp/quark@abc123@", null);
        when(shareRepository.findByTypeAndShareId(5, "abc123")).thenReturn(List.of(mounted, temp));

        service.saveShareTitle("quark@abc123@", "推送标题");

        ArgumentCaptor<Share> captor = ArgumentCaptor.forClass(Share.class);
        verify(shareRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Share::getTitle).containsOnly("推送标题");
    }

    /** 无法识别的链接(解析不出 type/shareId)直接返回 null,不触库。 */
    @Test
    void unknownLinkShortCircuitsWithoutQuery() {
        assertThat(service.findShareTitle("not-a-share-link")).isNull();
        verify(shareRepository, times(0)).findByTypeAndShareId(any(), any());
    }
}
