package cn.har01d.alist_tvbox.dto.diagnostics;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 诊断报告的一个子系统区块(系统/数据库/存储/追剧/搜索源/日志)。
 * items 保持插入顺序(LinkedHashMap),文本渲染按此顺序输出。
 */
@Data
public class DiagnosticsSectionDto {
    public static final String STATUS_OK = "OK";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_ERROR = "ERROR";

    private String name;
    private String status = STATUS_OK;
    private Map<String, String> items = new LinkedHashMap<>();

    public DiagnosticsSectionDto(String name) {
        this.name = name;
    }

    public DiagnosticsSectionDto add(String key, String value) {
        items.put(key, value);
        return this;
    }
}
