package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.config.AppProperties;
import cn.har01d.alist_tvbox.entity.MediaSubscription;
import cn.har01d.alist_tvbox.entity.MediaSubscriptionRepository;
import cn.har01d.alist_tvbox.entity.MediaSubscriptionResource;
import cn.har01d.alist_tvbox.entity.MediaSubscriptionResourceRepository;
import cn.har01d.alist_tvbox.entity.Share;
import cn.har01d.alist_tvbox.exception.BadRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 手动添加资源的路径形态识别(issue #1071):本实例 URL(%XX 解码/去 query)、裸路径
 * (尾斜杠/重斜杠归一)、/dav 前缀剥离、根路径/主源挂载目录拒绝、幂等刷新与复活、
 * 分享链接流程不受影响。
 */
class MediaSubscriptionAddPathTest {

    private final MediaSubscriptionRepository subscriptionRepository = mock(MediaSubscriptionRepository.class);
    private final MediaSubscriptionResourceRepository resourceRepository = mock(MediaSubscriptionResourceRepository.class);
    private final ShareService shareService = mock(ShareService.class);
    private final MediaSubscriptionCheckService checkService = mock(MediaSubscriptionCheckService.class);
    private final MediaSubscriptionService service = new MediaSubscriptionService(
            subscriptionRepository, resourceRepository, null, null, null, null, null, null, null, shareService, null,
            checkService, null, null, new AppProperties(), new ObjectMapper(), null, null, null);

    private static final String PATH = "/115/115/电影/醒来";
    private static final String LINK = "path:" + PATH;

    @BeforeEach
    void setUp() {
        MediaSubscription subscription = new MediaSubscription();
        subscription.setId(3);
        subscription.setUid(1);
        subscription.setName("醒来");
        subscription.setMountPath("/追剧/3-醒来");
        when(subscriptionRepository.findById(3)).thenReturn(Optional.of(subscription));
        when(shareService.parseShareLink(anyString())).thenReturn(null);
        when(shareService.findStorageDriverByPath(PATH)).thenReturn("115 Cloud");
        when(resourceRepository.findBySubscriptionIdAndLink(anyInt(), anyString())).thenReturn(Optional.empty());
        when(checkService.registerPathResource(any(), any())).thenAnswer(inv -> {
            MediaSubscriptionResource resource = inv.getArgument(1);
            if (resource != null) { // 重新打桩时 Mockito 以 (null,null) 实调旧 answer,须空参安全
                resource.setId(41);
                resource.setState(MediaSubscriptionResource.STATE_MOUNTED);
            }
            return Set.of(1, 2, 3);
        });
    }

    @Test
    void urlInputDecodesToPathResource() {
        Map<String, Object> result = service.addResource(1, 3,
                "http://192.168.1.1:5344/115/115/%E7%94%B5%E5%BD%B1/%E9%86%92%E6%9D%A5?t=1", null);

        assertEquals(3, result.get("episodes"));
        assertEquals(false, result.get("existed"));
        ArgumentCaptor<MediaSubscriptionResource> captor = ArgumentCaptor.forClass(MediaSubscriptionResource.class);
        verify(checkService).registerPathResource(any(), captor.capture());
        MediaSubscriptionResource resource = captor.getValue();
        assertEquals(LINK, resource.getLink());
        assertEquals(8, resource.getType()); // 115 Cloud → 115
        assertEquals("醒来", resource.getTitle());
        assertEquals(1000, resource.getScore());
        assertEquals(MediaSubscriptionResource.SOURCE_MANUAL, resource.getSource());
        assertTrue(resource.getPassword() == null, "路径资源无提取码语义");
    }

    @Test
    void barePathAndDavPrefixNormalize() {
        Map<String, Object> result = service.addResource(1, 3, PATH + "/", null);
        assertEquals(3, result.get("episodes"));

        service.addResource(1, 3, "https://box.example.com/dav" + PATH + "//", null);

        verify(checkService, org.mockito.Mockito.times(2)).registerPathResource(any(),
                org.mockito.ArgumentMatchers.argThat(r -> LINK.equals(r.getLink())));
    }

    @Test
    void rootPathRejected() {
        assertThrows(BadRequestException.class, () -> service.addResource(1, 3, "http://192.168.1.1:5344/", null));
        assertThrows(BadRequestException.class, () -> service.addResource(1, 3, "/", null));
        verify(checkService, never()).registerPathResource(any(), any());
    }

    @Test
    void selfMountPathRejected() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.addResource(1, 3, "/追剧/3-醒来", null));
        assertTrue(error.getMessage().contains("主源挂载目录"), error.getMessage());
    }

    @Test
    void existingMountedRowRefreshes() {
        MediaSubscriptionResource existing = new MediaSubscriptionResource();
        existing.setId(41);
        existing.setSubscriptionId(3);
        existing.setLink(LINK);
        existing.setState(MediaSubscriptionResource.STATE_MOUNTED);
        when(resourceRepository.findBySubscriptionIdAndLink(3, LINK)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.addResource(1, 3, PATH, null);

        assertEquals(true, result.get("existed"));
        assertEquals(false, result.get("revived"));
        verify(checkService).registerPathResource(any(), org.mockito.ArgumentMatchers.same(existing));
    }

    @Test
    void removedRowRevivesViaRegistration() {
        MediaSubscriptionResource removed = new MediaSubscriptionResource();
        removed.setId(41);
        removed.setSubscriptionId(3);
        removed.setLink(LINK);
        removed.setState(MediaSubscriptionResource.STATE_REMOVED);
        when(resourceRepository.findBySubscriptionIdAndLink(3, LINK)).thenReturn(Optional.of(removed));

        Map<String, Object> result = service.addResource(1, 3, PATH, null);

        assertEquals(true, result.get("revived"));
        verify(checkService).registerPathResource(any(), org.mockito.ArgumentMatchers.same(removed));
    }

    @Test
    void registrationFailureSurfacesMessageAndPersistsNothing() {
        when(checkService.registerPathResource(any(), any()))
                .thenThrow(new IllegalStateException("目录里没有可识别的本季剧集文件:" + PATH));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.addResource(1, 3, PATH, null));

        assertTrue(error.getMessage().contains("没有可识别"), error.getMessage());
        verify(resourceRepository, never()).save(any(MediaSubscriptionResource.class));
    }

    @Test
    void shareLinksStillGoCandidateFlow() {
        Share share = new Share();
        share.setType(5);
        when(shareService.parseShareLink("https://pan.quark.cn/s/abc")).thenReturn(share);
        when(resourceRepository.save(any(MediaSubscriptionResource.class))).thenAnswer(inv -> {
            MediaSubscriptionResource saved = inv.getArgument(0);
            saved.setId(11);
            return saved;
        });

        Map<String, Object> result = service.addResource(1, 3, "https://pan.quark.cn/s/abc", "1234");

        assertEquals(false, result.get("existed"));
        verify(checkService, never()).registerPathResource(any(), any());
        ArgumentCaptor<MediaSubscriptionResource> captor = ArgumentCaptor.forClass(MediaSubscriptionResource.class);
        verify(resourceRepository).save(captor.capture());
        assertEquals("https://pan.quark.cn/s/abc", captor.getValue().getLink());
        assertEquals(MediaSubscriptionResource.STATE_CANDIDATE, captor.getValue().getState());
    }
}
