package com.ordertoaudit.reports.export;

import java.util.Locale;

import org.springframework.http.MediaType;

import com.ordertoaudit.common.error.BadRequestException;

/** The ?format= of a report request. */
public enum ReportFormat {
    JSON("json", MediaType.APPLICATION_JSON_VALUE),
    CSV("csv", "text/csv; charset=UTF-8"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("pdf", MediaType.APPLICATION_PDF_VALUE);

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return MediaType.parseMediaType(contentType);
    }

    public static ReportFormat parse(String value) {
        for (ReportFormat format : values()) {
            if (format.extension.equals(value.toLowerCase(Locale.ROOT))) {
                return format;
            }
        }
        throw new BadRequestException("Unknown format '" + value + "'. Use json, csv, xlsx or pdf");
    }
}
