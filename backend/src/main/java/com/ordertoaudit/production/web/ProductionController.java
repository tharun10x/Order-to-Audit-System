package com.ordertoaudit.production.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.production.service.ProductionService;
import com.ordertoaudit.production.service.RunReportService;
import com.ordertoaudit.security.CurrentUserProvider;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/production-runs")
public class ProductionController {

    private final ProductionService productionService;
    private final RunReportService reportService;
    private final CurrentUserProvider currentUser;

    public ProductionController(ProductionService productionService, RunReportService reportService,
                                CurrentUserProvider currentUser) {
        this.productionService = productionService;
        this.reportService = reportService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse create(@Valid @RequestBody CreateRunRequest request) {
        return productionService.create(request, currentUser.current());
    }

    @PutMapping("/{id}/actuals")
    public RunResponse enterActuals(@PathVariable Long id, @Valid @RequestBody ActualsRequest request) {
        return productionService.enterActuals(id, request, currentUser.current());
    }

    @PostMapping("/{id}/post")
    public RunResponse post(@PathVariable Long id) {
        return productionService.post(id, currentUser.current());
    }

    @PostMapping("/{id}/cancel")
    public RunResponse cancel(@PathVariable Long id) {
        return productionService.cancel(id, currentUser.current());
    }

    @PostMapping("/{id}/overheads")
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse addOverhead(@PathVariable Long id, @Valid @RequestBody OverheadRequest request) {
        return productionService.addOverhead(id, request, currentUser.current());
    }

    @GetMapping("/{id}")
    public RunResponse get(@PathVariable Long id) {
        return reportService.report(id);
    }

    @GetMapping
    public List<RunSummaryResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.list(from, to);
    }
}
