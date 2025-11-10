package cn.har01d.alist_tvbox.web;

import cn.har01d.alist_tvbox.entity.DeviceRepository;
import cn.har01d.alist_tvbox.service.EmbyService;
import cn.har01d.alist_tvbox.service.HistoryService;
import cn.har01d.alist_tvbox.service.SubscriptionService;
import cn.har01d.alist_tvbox.service.TvBoxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class DIYController {
    private final EmbyService embyService;
    private final SubscriptionService subscriptionService;
    private final HistoryService historyService;
    private final DeviceRepository deviceRepository;
    private final ObjectMapper objectMapper;

    public DIYController(EmbyService embyService,
                         SubscriptionService subscriptionService,
                         HistoryService historyService,
                         DeviceRepository deviceRepository,
                         ObjectMapper objectMapper) {
        this.embyService = embyService;
        this.subscriptionService = subscriptionService;
        this.historyService = historyService;
        this.deviceRepository = deviceRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{token}/allsubs")
    public Map<String, Object> allSubscription(@PathVariable String token, HttpServletRequest request) {
        String env = request.getParameter("env");
        subscriptionService.checkToken(token);
        Map<String, Object> res = new HashMap<>();
        List<Map<String, String>> collect = subscriptionService.findAll().stream()
                .filter(s -> {
                            String sid = s.getSid();
                            return !sid.endsWith("-hh");
                        }
                )
                .map(s -> {
                    String sid = s.getSid();
                    String name = (StringUtils.isNotBlank(env) ? env + "-" : "") + s.getName();
                    HashMap<String, String> map = new HashMap<>();
                    map.put("url", subscriptionService.readHostAddress("/sub" + (StringUtils.isNotBlank(token) ? "/" + token : "") + "/" + sid));
                    map.put("name", name);
                    return map;
                }).collect(Collectors.toList());
        res.put("urls", collect);
        return res;
    }

    @GetMapping("/allsubs")
    public Map<String, Object> allSubscription(HttpServletRequest request) {
        return allSubscription("", request);
    }

    @GetMapping("/{token}/embyFakePlay")
    public void embyFakePlay(@PathVariable String token,HttpServletRequest request) throws JsonProcessingException {
        subscriptionService.checkToken(token);
        embyService.fakePlay();
    }

    @GetMapping("/embyFakePlay")
    public void embyFakePlay(HttpServletRequest request) throws JsonProcessingException {
        embyFakePlay("", request);
    }

    @GetMapping("/embyRemoveCache")
    public void embyRemoveCache(HttpServletRequest request,Integer id) throws JsonProcessingException {
        embyService.cache.invalidate(id);
    }

}
