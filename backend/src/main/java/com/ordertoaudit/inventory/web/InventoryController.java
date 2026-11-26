package com.ordertoaudit.inventory.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ordertoaudit.inventory.service.LedgerService;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final LedgerService ledgerService;

    public InventoryController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balances")
    public List<BalanceResponse> balances() {
        return ledgerService.balances();
    }

    @GetMapping("/ledger")
    public List<LedgerRowResponse> ledger(
            @RequestParam String itemCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ledgerService.ledger(itemCode, from, to);
    }
}
