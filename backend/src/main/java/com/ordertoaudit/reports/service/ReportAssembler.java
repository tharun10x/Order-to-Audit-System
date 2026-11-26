package com.ordertoaudit.reports.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.Company;
import com.ordertoaudit.masters.repository.CompanyRepository;
import com.ordertoaudit.reports.model.Report;
import com.ordertoaudit.reports.model.ReportTable;

/** Adds the common header to every report: company name, and who generated it and when. */
@Component
public class ReportAssembler {

    private final CompanyRepository companyRepository;

    public ReportAssembler(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /** subject is null, except for a report about one thing (the stock ledger's item code). */
    public Report assemble(String code, String title, String subject, ReportPeriod period, String periodLabel,
                           AppUser user, List<ReportTable> sections) {
        String companyName = companyRepository.findAll().stream().findFirst()
                .map(Company::getName)
                .orElseThrow(() -> new IllegalStateException("The company row is missing"));
        // generatedAt is the real time, not the business clock: it records when the file was made
        return new Report(code, title, subject, period.from(), period.to(), periodLabel, companyName,
                Instant.now(), user.getUsername(), List.copyOf(sections));
    }

    public Report assemble(String code, String title, ReportPeriod period, AppUser user, List<ReportTable> sections) {
        return assemble(code, title, null, period, period.label(), user, sections);
    }
}
