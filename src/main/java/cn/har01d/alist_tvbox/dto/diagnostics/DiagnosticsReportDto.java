package cn.har01d.alist_tvbox.dto.diagnostics;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键诊断报告:只读聚合各子系统状态 + 近期错误日志摘要,供用户复制到 issue 排障。
 * 文本版 {@link #text} 已脱敏(凭证参数/令牌打码),结构化 sections/findings 供网页渲染。
 */
@Data
public class DiagnosticsReportDto {
    private String generatedAt;
    private int errorCount;
    private int warnCount;
    private List<DiagnosticsSectionDto> sections = new ArrayList<>();
    private List<DiagnosticsFindingDto> findings = new ArrayList<>();
    /** 面向复制粘贴的紧凑纯文本渲染(与 sections 同源,已脱敏)。 */
    private String text = "";
}
