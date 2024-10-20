package cn.har01d.alist_tvbox.web;

import cn.har01d.alist_tvbox.config.AppProperties;
import cn.har01d.alist_tvbox.dto.TokenDto;
import cn.har01d.alist_tvbox.service.SubscriptionService;
import cn.har01d.alist_tvbox.service.TvBoxService;
import cn.har01d.alist_tvbox.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class TvBoxController {
    private final TvBoxService tvBoxService;
    private final SubscriptionService subscriptionService;

    private final AppProperties appProperties;

    public TvBoxController(TvBoxService tvBoxService, SubscriptionService subscriptionService, AppProperties appProperties) {
        this.tvBoxService = tvBoxService;
        this.subscriptionService = subscriptionService;
        this.appProperties = appProperties;
    }

    @GetMapping("/vod1")
    public Object api1(String t, String f, String ids, String ac, String wd, String sort,
                       @RequestParam(required = false, defaultValue = "1") Integer pg,
                       HttpServletRequest request) {
        return api("", t, f, ids, ac, wd, sort, pg, 0, request);
    }

    @GetMapping("/vod1/{token}")
    public Object api1(@PathVariable String token, String t, String f, String ids, String ac, String wd, String sort,
                       @RequestParam(required = false, defaultValue = "1") Integer pg,
                       HttpServletRequest request) {
        return api(token, t, f, ids, ac, wd, sort, pg, 0, request);
    }

    @GetMapping("/vod")
    public Object api(String t, String f, String ids, String ac, String wd, String sort,
                      @RequestParam(required = false, defaultValue = "1") Integer pg,
                      HttpServletRequest request) {
        return api("", t, f, ids, ac, wd, sort, pg, 1, request);
    }

    @GetMapping("/vod/{token}")
    public Object api(@PathVariable String token, String t, String f, String ids, String ac, String wd, String sort,
                      @RequestParam(required = false, defaultValue = "1") Integer pg,
                      @RequestParam(required = false, defaultValue = "1") Integer type,
                      HttpServletRequest request) {
        subscriptionService.checkToken(token);

        String client = request.getHeader("X-CLIENT");
        log.info("type: {}  path: {}  folder: {}  ac: {}  keyword: {}  filter: {}  sort: {}  page: {}", type, ids, t, ac, wd, f, sort, pg);
        if (ids != null && !ids.isEmpty()) {
            if (ids.startsWith("msearch:")) {
                return tvBoxService.msearch(type, ids.substring(8));
            } else if (ids.equals("recommend")) {
                return tvBoxService.recommend(ac, pg);
            }
            return tvBoxService.getDetail(ac, ids);
        } else if (t != null && !t.isEmpty()) {
            if (t.equals("0")) {
                return tvBoxService.recommend(ac, pg);
            }
            return tvBoxService.getMovieList(client, ac, t, f, sort, pg);
        } else if (wd != null && !wd.isEmpty()) {
            return tvBoxService.search(type, ac, wd, pg);
        } else {
            return tvBoxService.getCategoryList(type);
        }
    }

    @GetMapping("/api/profiles")
    public List<String> getProfiles() {
        return subscriptionService.getProfiles();
    }

    @GetMapping("/api/token")
    public String getToken() {
        return subscriptionService.getTokens();
    }

    @PostMapping("/api/token")
    public String createToken(@RequestBody TokenDto dto) {
        return subscriptionService.createToken(dto);
    }

    @DeleteMapping("/api/token")
    public void deleteToken() {
        subscriptionService.deleteToken();
    }

    @GetMapping("/sub/{id}")
    public Map<String, Object> subscription(@PathVariable String id) {
        return subscription("", id);
    }

    @GetMapping("/sub/{token}/{id}")
    public Map<String, Object> subscription(@PathVariable String token, @PathVariable String id) {
        subscriptionService.checkToken(token);

        return subscriptionService.subscription(token, id);
    }

    @GetMapping("/open")
    public Map<String, Object> open() throws IOException {
        return open("");
    }

    @GetMapping("/open/{token}")
    public Map<String, Object> open(@PathVariable String token) throws IOException {
        subscriptionService.checkToken(token);

        return subscriptionService.open();
    }

    @GetMapping("/node/{token}/{file}")
    public String node(@PathVariable String token, @PathVariable String file) throws IOException {
        subscriptionService.checkToken(token);

        return subscriptionService.node(file);
    }

    @PostMapping("/api/cat/sync")
    public int syncCat() {
        return subscriptionService.syncCat();
    }

    @GetMapping(value = "/repo/{id}", produces = "application/json")
    public String repository(@PathVariable String id) {
        return repository("", id);
    }

    @GetMapping(value = "/repo/{token}/{id}", produces = "application/json")
    public String repository(@PathVariable String token, @PathVariable String id) {
        subscriptionService.checkToken(token);

        return subscriptionService.repository(token, id);
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
                    map.put("url", readHostAddress("/sub" + (StringUtils.isNotBlank(token) ? "/" + token : "") + "/" + sid, null));
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

    public String readHostAddress(String path, String query) {
        UriComponents uriComponents = ServletUriComponentsBuilder.fromCurrentRequest()
                .scheme(appProperties.isEnableHttps() && !Utils.isLocalAddress() ? "https" : "http") // nginx https
                .replacePath(path)
                .replaceQuery(query)
                .build();
        return uriComponents.toUriString();
    }
}
