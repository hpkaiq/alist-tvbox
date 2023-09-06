package cn.har01d.alist_tvbox.service;

import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixService {
    private static Map<String, String> getUrlParam(String url) {
        Map<String, String> urlParamMap = new HashMap<>();
        try {
            URL uri = new URL(url);
            String query = uri.getQuery();
            String[] queryParams = query.split("&");
            for (String param : queryParams) {
                String[] keyValue = param.split("=");
                String key = keyValue[0];
                String value = keyValue[1];
                urlParamMap.put(key, value);
            }
        } catch (Exception ignored) {
        }
        urlParamMap.put("sourceUrl", urlParamMap.getOrDefault("sourceUrl", url));
        return urlParamMap;
    }

    public static Map<String, Object> updateOverride(String url, Map<String, Object> override) {
        Map<String, String> urlParam = getUrlParam(url);
        Object spiderObj = override.get("spider");
        if (spiderObj != null && StringUtils.isNotBlank(spiderObj.toString())) {
            String spider = spiderObj.toString();
            if (spider.startsWith("./")) {
                spider = urlParam.get("sourceUrl") + spider.substring(1);
            } else if (spider.startsWith("/")) {
                spider = getRoot(urlParam.get("sourceUrl")) + spider;
            } else if (!spider.startsWith("http")) {
                spider = getRoot(urlParam.get("sourceUrl")) + spider;
            }
            override.put("spider", spider);
        }


        for (Map.Entry<String, Object> entry : override.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Collection) {
                List<Object> valueObjs = (List<Object>) value;
                if (valueObjs.size() > 0 && valueObjs.get(0) instanceof Map) {
                    for (Object valueObj : valueObjs) {
                        Map<String, Object> valueObjArr = (Map<String, Object>) valueObj;
                        fixSite(valueObjArr, urlParam.get("sourceUrl"));
                    }
                }
            }
        }

        return override;

    }

    private static void fixSite(Map<String, Object> site, String sourceUrl) {
        try {
            fixPath(site, "url", sourceUrl);
        } catch (Exception ignored) {

        }
        try {
            fixPath(site, "api", sourceUrl);
        } catch (Exception ignored) {

        }
        try {
            fixPath(site, "ext", sourceUrl);
        } catch (Exception ignored) {

        }
        try {
            fixPath(site, "jar", sourceUrl);
        } catch (Exception ignored) {

        }

    }

    private static void fixPath(Map<String, Object> site, String key, String sourceUrl) {
        Object o = site.get(key);
        if (o instanceof String) {
            String path = o.toString();
            if (StringUtils.isNotBlank(path) && StringUtils.isNotBlank(sourceUrl)) {
                if (path.startsWith("./")) {
                    path = sourceUrl + path.substring(1);
                } else if (path.startsWith("/")) {
                    path = getRoot(sourceUrl) + path;
                }
                site.put(key, path);
            }
        }
    }

    public static String getRoot(String path) {
        try {
            URL url = new URL(path);
            int port = url.getPort();
            port = port == -1 ? url.getDefaultPort() : port;
            return url.getProtocol() + "://" + url.getHost() + (port == 80 || port == 443 ? "" : ":" + port);
        } catch (Exception e) {
        }
        return "";
    }

}