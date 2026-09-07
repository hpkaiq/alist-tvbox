package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.entity.Setting;
import cn.har01d.alist_tvbox.entity.SettingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebHomeServiceTest {

    @SuppressWarnings("unchecked")
    private SettingRepository repoReturning(Map<String, String> store) {
        SettingRepository repository = mock(SettingRepository.class);
        when(repository.findById(any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Optional.ofNullable(store.get(key)).map(v -> new Setting(key, v));
        });
        when(repository.save(any(Setting.class))).thenAnswer(invocation -> {
            Setting s = invocation.getArgument(0);
            store.put(s.getName(), s.getValue());
            return s;
        });
        return repository;
    }

    @Test
    void recordsAndPersistsWebHomeCapabilityByToken() {
        Map<String, String> store = new HashMap<>();
        SettingRepository repository = repoReturning(store);
        WebHomeService service = new WebHomeService(repository);
        service.load();

        assertFalse(service.isCapable("u-alice"));

        // spider 类探测头
        service.recordCapability("u-alice", "webhome", "com.fongmi.android.tv");
        assertTrue(service.isCapable("u-alice"));
        assertEquals("u-alice", store.get("webhome_capable_tokens"));

        // webhtv 独占包名,头缺失也能判定
        service.recordCapability("u-bob", null, "com.silent.android.webhtv");
        assertTrue(service.isCapable("u-bob"));

        // 原版 FongMi:无头 + 同包名,绝不记忆
        service.recordCapability("u-carol", null, "com.fongmi.android.tv");
        assertFalse(service.isCapable("u-carol"));
        assertFalse(store.get("webhome_capable_tokens").contains("u-carol"));

        // 重复上报只落一次库
        verify(repository, times(2)).save(any(Setting.class));
    }

    @Test
    void blankTokenSharesSingleKeyAndSurvivesReload() {
        Map<String, String> store = new HashMap<>();
        SettingRepository repository = repoReturning(store);
        WebHomeService service = new WebHomeService(repository);
        service.load();

        service.recordCapability("", "webhome", null);
        assertTrue(service.isCapable(""));
        assertTrue(service.isCapable(null));

        // 重启后从 Setting 恢复
        WebHomeService revived = new WebHomeService(repository);
        revived.load();
        assertTrue(revived.isCapable(""));
        assertFalse(revived.isCapable("u-alice"));
    }

    @Test
    void unknownCapsHeaderIgnored() {
        SettingRepository repository = mock(SettingRepository.class);
        when(repository.findById(any())).thenReturn(Optional.empty());
        WebHomeService service = new WebHomeService(repository);
        service.load();

        service.recordCapability("u-x", "whatever", "com.other.app");
        assertFalse(service.isCapable("u-x"));
        verify(repository, never()).save(any(Setting.class));
    }
}
