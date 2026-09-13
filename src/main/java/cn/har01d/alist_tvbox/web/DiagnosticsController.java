package cn.har01d.alist_tvbox.web;

import cn.har01d.alist_tvbox.dto.diagnostics.DiagnosticsReportDto;
import cn.har01d.alist_tvbox.service.diagnostics.DiagnosticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一键诊断报告:只读聚合系统状态与近期错误摘要,供用户复制到 issue 排障(输出已脱敏)。
 */
@Slf4j
@RestController
@RequestMapping("/api/diagnostics")
@PreAuthorize("hasAnyAuthority('ADMIN')")
public class DiagnosticsController {
    private final DiagnosticsService diagnosticsService;

    public DiagnosticsController(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/report")
    public DiagnosticsReportDto report() {
        log.info("generating diagnostics report");
        return diagnosticsService.buildReport();
    }
}
