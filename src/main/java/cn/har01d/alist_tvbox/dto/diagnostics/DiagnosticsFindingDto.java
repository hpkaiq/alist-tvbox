package cn.har01d.alist_tvbox.dto.diagnostics;

import lombok.Data;

/** 诊断告警条目:ERROR=疑似故障需处理,WARN=值得关注(瞬时退避/积压等不一定是故障)。 */
@Data
public class DiagnosticsFindingDto {
    public static final String SEVERITY_ERROR = "ERROR";
    public static final String SEVERITY_WARN = "WARN";

    private String severity;
    private String message;

    public DiagnosticsFindingDto(String severity, String message) {
        this.severity = severity;
        this.message = message;
    }
}
