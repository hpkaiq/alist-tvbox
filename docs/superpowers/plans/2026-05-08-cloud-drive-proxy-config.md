# Cloud Drive Proxy Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the built-in spider `enable_local_proxy` flag with a per-`CloudDriveType` `local_proxy_config` object, edit that configuration from the netdisk account page, and pass it through `SubscriptionService#buildSite` via `ext`.

**Architecture:** Lock in the new backend contract first with focused `SubscriptionServiceTest` coverage that decodes the generated `ext` payload and asserts that `local_proxy_config` is present while `enable_local_proxy` is absent. Then implement the minimal backend parser and payload change in `SubscriptionService`. Finally replace the temporary single-switch config dialog in `DriverAccountView.vue` with a typed per-drive configuration editor that loads, normalizes, and saves a single `local_proxy_config` JSON setting.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, Spring mock web utilities, Vue 3, TypeScript, Element Plus, Vite, vue-tsc

---

### Task 1: Lock In the New `ext` Contract With Failing Backend Tests

**Files:**
- Create: `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- Create: `src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java`
- Test: `src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java`

- [ ] **Step 1: Create the Mockito mock-maker resource required by this environment**

```text
org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker
```

- [ ] **Step 2: Create `SubscriptionServiceTest` with a failing test for the missing-setting case**

```java
package cn.har01d.alist_tvbox.service;

import cn.har01d.alist_tvbox.config.AppProperties;
import cn.har01d.alist_tvbox.entity.AccountRepository;
import cn.har01d.alist_tvbox.entity.Setting;
import cn.har01d.alist_tvbox.entity.SettingRepository;
import cn.har01d.alist_tvbox.entity.SiteRepository;
import cn.har01d.alist_tvbox.entity.SubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {
    @Mock
    private Environment environment;
    @Mock
    private AppProperties appProperties;
    @Spy
    private RestTemplateBuilder builder = new RestTemplateBuilder();
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SettingRepository settingRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SiteRepository siteRepository;
    @Mock
    private AListLocalService aListLocalService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void buildSiteShouldEmitEmptyLocalProxyConfigWhenSettingMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/subscriptions");
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(4567);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(appProperties.isEnableHttps()).thenReturn(false);
        when(settingRepository.findById("local_proxy_config")).thenReturn(Optional.empty());

        Map<String, Object> site = ReflectionTestUtils.invokeMethod(
                subscriptionService,
                "buildSite",
                "test-token",
                "test-uid",
                "csp_AList",
                "AList"
        );

        String ext = (String) site.get("ext");
        String json = new String(Base64.getDecoder().decode(ext), StandardCharsets.UTF_8);
        Map<String, Object> extMap = objectMapper.readValue(json, Map.class);

        assertThat(extMap).containsEntry("api", "http://127.0.0.1:4567");
        assertThat(extMap).containsEntry("token", "test-token");
        assertThat(extMap).containsEntry("uid", "test-uid");
        assertThat(extMap).containsEntry("local_proxy_config", Map.of());
        assertThat(extMap).doesNotContainKey("enable_local_proxy");
    }
}
```

- [ ] **Step 3: Extend the same test class with a failing stored-config case**

```java
    @Test
    void buildSiteShouldEmitStoredLocalProxyConfig() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/subscriptions");
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(4567);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        when(appProperties.isEnableHttps()).thenReturn(false);
        when(settingRepository.findById("local_proxy_config")).thenReturn(Optional.of(new Setting(
                "local_proxy_config",
                "{\"QUARK\":{\"enabled\":true,\"concurrency\":20,\"chunk_size\":1048576},\"UC\":{\"enabled\":false,\"concurrency\":10,\"chunk_size\":262144}}"
        )));

        Map<String, Object> site = ReflectionTestUtils.invokeMethod(
                subscriptionService,
                "buildSite",
                "test-token",
                "test-uid",
                "csp_AList",
                "AList"
        );

        String ext = (String) site.get("ext");
        String json = new String(Base64.getDecoder().decode(ext), StandardCharsets.UTF_8);
        Map<String, Object> extMap = objectMapper.readValue(json, Map.class);
        Map<String, Object> localProxyConfig = (Map<String, Object>) extMap.get("local_proxy_config");

        assertThat(localProxyConfig).containsKey("QUARK");
        assertThat(localProxyConfig).containsKey("UC");
        assertThat(((Map<String, Object>) localProxyConfig.get("QUARK"))).containsEntry("concurrency", 20);
        assertThat(((Map<String, Object>) localProxyConfig.get("QUARK"))).containsEntry("chunk_size", 1048576);
        assertThat(((Map<String, Object>) localProxyConfig.get("UC"))).containsEntry("enabled", false);
    }
```

- [ ] **Step 4: Run the targeted backend test to verify it fails for the right reason**

Run: `mvn -Dtest=SubscriptionServiceTest test`

Expected: FAIL because `buildSite` still emits `enable_local_proxy` and does not yet include `local_proxy_config`.

- [ ] **Step 5: Commit the red backend tests**

```bash
git add src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java
git commit -m "test: cover cloud drive proxy config in site ext"
```

### Task 2: Implement `local_proxy_config` in `SubscriptionService`

**Files:**
- Modify: `src/main/java/cn/har01d/alist_tvbox/service/SubscriptionService.java`
- Test: `src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java`

- [ ] **Step 1: Add a helper that reads `local_proxy_config` and safely parses JSON**

```java
private Map<String, Object> readLocalProxyConfig() {
    return settingRepository.findById("local_proxy_config")
            .map(Setting::getValue)
            .filter(StringUtils::isNotBlank)
            .map(this::parseLocalProxyConfig)
            .orElseGet(HashMap::new);
}

private Map<String, Object> parseLocalProxyConfig(String value) {
    try {
        return objectMapper.readValue(value, Map.class);
    } catch (Exception e) {
        log.warn("parse local proxy config failed: {}", value, e);
        return new HashMap<>();
    }
}
```

- [ ] **Step 2: Replace the current `enable_local_proxy` emission with `local_proxy_config` in `buildSite`**

```java
private Map<String, Object> buildSite(String token, String uid, String key, String name) throws IOException {
    Map<String, Object> site = new HashMap<>();
    String url = readHostAddress("");
    site.put("key", key);
    site.put("api", key);
    site.put("name", name);
    site.put("type", 3);

    Map<String, Object> map = new HashMap<>();
    map.put("api", url);
    map.put("token", token.isBlank() ? "-" : token);
    map.put("uid", uid);
    map.put("local_proxy_config", readLocalProxyConfig());

    String ext = objectMapper.writeValueAsString(map).replaceAll("\\s", "");
    ext = Base64.getEncoder().encodeToString(ext.getBytes());
    site.put("ext", ext);
    String jar = url + "/spring.jar";
    site.put("jar", jar);
    site.put("changeable", 0);
    site.put("searchable", 1);
    site.put("quickSearch", 1);
    site.put("filterable", 1);
    if ("csp_BiliBili".equals(key)) {
        Map<String, Object> style = new HashMap<>();
        style.put("type", "rect");
        style.put("ratio", 1.597);
        site.put("style", style);
    }
    return site;
}
```

- [ ] **Step 3: Remove the obsolete `isEnableLocalProxy()` helper if it still exists**

```java
// Delete the entire helper below once buildSite no longer uses it:
private boolean isEnableLocalProxy() {
    return !"false".equals(settingRepository.findById("enable_local_proxy")
            .map(Setting::getValue)
            .orElse("true"));
}
```

- [ ] **Step 4: Run the targeted backend test again to verify green**

Run: `mvn -Dtest=SubscriptionServiceTest test`

Expected: PASS with both `buildSiteShouldEmitEmptyLocalProxyConfigWhenSettingMissing` and `buildSiteShouldEmitStoredLocalProxyConfig` green.

- [ ] **Step 5: Commit the backend implementation**

```bash
git add src/main/java/cn/har01d/alist_tvbox/service/SubscriptionService.java src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker
git commit -m "feat: pass cloud drive proxy config to spiders"
```

### Task 3: Replace the Temporary Driver Config Dialog With a Typed Per-Drive Editor

**Files:**
- Modify: `web-ui/src/views/DriverAccountView.vue`
- Verify: `web-ui/src/views/DriverAccountView.vue`

- [ ] **Step 1: Define the supported drive types and default config shape in the script**

```ts
type CloudDriveType = 'ALI' | 'QUARK' | 'UC' | 'PAN115' | 'PAN123' | 'PAN139' | 'BAIDU'

type LocalProxyItem = {
  enabled: boolean
  concurrency: number
  chunk_size: number
}

type LocalProxyConfig = Record<CloudDriveType, LocalProxyItem>

const driveTypes: Array<{ key: CloudDriveType; label: string }> = [
  {key: 'ALI', label: '阿里云盘'},
  {key: 'QUARK', label: '夸克网盘'},
  {key: 'UC', label: 'UC网盘'},
  {key: 'PAN115', label: '115云盘'},
  {key: 'PAN123', label: '123网盘'},
  {key: 'PAN139', label: '移动云盘'},
  {key: 'BAIDU', label: '百度网盘'},
]

const defaultLocalProxyConfig = (): LocalProxyConfig => ({
  ALI: {enabled: true, concurrency: 20, chunk_size: 1024 * 1024},
  QUARK: {enabled: true, concurrency: 20, chunk_size: 1024 * 1024},
  UC: {enabled: true, concurrency: 10, chunk_size: 256 * 1024},
  PAN115: {enabled: true, concurrency: 2, chunk_size: 1024 * 1024},
  PAN123: {enabled: true, concurrency: 4, chunk_size: 256 * 1024},
  PAN139: {enabled: true, concurrency: 4, chunk_size: 256 * 1024},
  BAIDU: {enabled: true, concurrency: 5, chunk_size: 2 * 1024 * 1024},
})
```

- [ ] **Step 2: Replace the current `enableLocalProxy` state and handlers with `localProxyConfig` load/save helpers**

```ts
const configVisible = ref(false)
const localProxyConfig = ref<LocalProxyConfig>(defaultLocalProxyConfig())

const normalizeLocalProxyConfig = (value: any): LocalProxyConfig => {
  const defaults = defaultLocalProxyConfig()
  for (const item of driveTypes) {
    const current = value?.[item.key] || {}
    defaults[item.key] = {
      enabled: current.enabled ?? defaults[item.key].enabled,
      concurrency: current.concurrency ?? defaults[item.key].concurrency,
      chunk_size: current.chunk_size ?? defaults[item.key].chunk_size,
    }
  }
  return defaults
}

const loadLocalProxyConfig = () => {
  axios.get('/api/settings/local_proxy_config').then(({data}) => {
    if (!data || !data.value) {
      localProxyConfig.value = defaultLocalProxyConfig()
      return
    }

    try {
      localProxyConfig.value = normalizeLocalProxyConfig(JSON.parse(data.value))
    } catch (e) {
      localProxyConfig.value = defaultLocalProxyConfig()
    }
  })
}

const openConfig = () => {
  loadLocalProxyConfig()
  configVisible.value = true
}

const updateLocalProxyConfig = () => {
  axios.post('/api/settings', {
    name: 'local_proxy_config',
    value: JSON.stringify(localProxyConfig.value),
  }).then(() => {
    ElMessage.success('更新成功')
    configVisible.value = false
  })
}
```

- [ ] **Step 3: Replace the current config dialog template with a per-drive editor**

```vue
<el-dialog v-model="configVisible" title="网盘账号配置" width="60%">
  <div class="proxy-config-grid">
    <div class="proxy-config-row proxy-config-head">
      <span>类型</span>
      <span>启用</span>
      <span>并发数</span>
      <span>分片大小</span>
    </div>
    <div class="proxy-config-row" v-for="item in driveTypes" :key="item.key">
      <span>{{ item.label }}</span>
      <el-switch
        v-model="localProxyConfig[item.key].enabled"
        inline-prompt
        active-text="开启"
        inactive-text="关闭"
      />
      <el-input-number v-model="localProxyConfig[item.key].concurrency" :min="1" :max="64" />
      <el-input-number v-model="localProxyConfig[item.key].chunk_size" :min="256 * 1024" :step="256 * 1024" />
    </div>
  </div>
  <template #footer>
    <span class="dialog-footer">
      <el-button @click="configVisible = false">取消</el-button>
      <el-button type="primary" @click="updateLocalProxyConfig">保存</el-button>
    </span>
  </template>
</el-dialog>
```

- [ ] **Step 4: Remove the old temporary `enableLocalProxy` ref and handlers, and load the new config on mount**

```ts
onMounted(() => {
  load()
  loadLocalProxyConfig()
  axios.get('/api/settings/driver_round_robin').then(({data}) => {
    driverRoundRobin.value = data.value === 'true'
  })
})
```

- [ ] **Step 5: Add scoped styles for the dialog grid**

```css
.proxy-config-grid {
  display: grid;
  gap: 12px;
}

.proxy-config-row {
  display: grid;
  grid-template-columns: 120px 120px 160px 180px;
  align-items: center;
  gap: 12px;
}

.proxy-config-head {
  font-weight: 600;
}
```

- [ ] **Step 6: Run frontend type-check and build verification**

Run: `npm --prefix web-ui run build`

Expected: PASS with `vue-tsc --noEmit` and `vite build` both succeeding.

- [ ] **Step 7: Commit the frontend implementation**

```bash
git add web-ui/src/views/DriverAccountView.vue
git commit -m "feat: add cloud drive proxy config dialog"
```

### Task 4: Final Verification

**Files:**
- Verify: `src/main/java/cn/har01d/alist_tvbox/service/SubscriptionService.java`
- Verify: `src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java`
- Verify: `web-ui/src/views/DriverAccountView.vue`

- [ ] **Step 1: Run the backend regression target one more time**

Run: `mvn -Dtest=SubscriptionServiceTest test`

Expected: PASS

- [ ] **Step 2: Run the frontend build again**

Run: `npm --prefix web-ui run build`

Expected: PASS

- [ ] **Step 3: Run the repository test target to confirm the final branch state**

Run: `mvn test`

Expected: PASS

- [ ] **Step 4: Inspect the final diff before handing off**

Run: `git diff -- src/main/java/cn/har01d/alist_tvbox/service/SubscriptionService.java src/test/java/cn/har01d/alist_tvbox/service/SubscriptionServiceTest.java src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker web-ui/src/views/DriverAccountView.vue`

Expected: Only the `local_proxy_config` backend contract, targeted tests, Mockito test resource, and the new driver config dialog appear.
