package com.myprueba.ledger.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LedgerController {

    private final LedgerService ledger;
    private final CurrentBusiness business;

    LedgerController(LedgerService ledger, CurrentBusiness business) {
        this.ledger = ledger;
        this.business = business;
    }

    @PostMapping(path = "/api/sales", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SaleResponse> createSale(@RequestBody SaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledger.createSale(business.id(), business.zone(), request));
    }

    @GetMapping("/api/sales/{id}")
    SaleResponse getSale(@PathVariable String id) {
        return ledger.getSale(business.id(), Ids.parseOrNotFound(id));
    }

    @GetMapping("/api/sales/{id}/receipt")
    ReceiptResponse receipt(@PathVariable String id) {
        return ledger.receipt(business.id(), Ids.parseOrNotFound(id));
    }

    @PutMapping(path = "/api/sales/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    SaleResponse updateSale(@PathVariable String id, @RequestBody SaleRequest request) {
        return ledger.updateSale(business.id(), business.zone(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/api/sales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSale(@PathVariable String id) {
        ledger.deleteSale(business.id(), Ids.parseOrNotFound(id));
    }

    @PostMapping(path = "/api/expenses", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ExpenseResponse> createExpense(@RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledger.createExpense(business.id(), business.zone(), request));
    }

    @GetMapping("/api/expenses/{id}")
    ExpenseResponse getExpense(@PathVariable String id) {
        return ledger.getExpense(business.id(), Ids.parseOrNotFound(id));
    }

    @PutMapping(path = "/api/expenses/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ExpenseResponse updateExpense(@PathVariable String id, @RequestBody ExpenseRequest request) {
        return ledger.updateExpense(business.id(), business.zone(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/api/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExpense(@PathVariable String id) {
        ledger.deleteExpense(business.id(), Ids.parseOrNotFound(id));
    }

    @GetMapping("/api/movements")
    MovementsResponse movements(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String type) {
        return ledger.movements(business.id(), business.zone(), period, from, to, type);
    }

    @GetMapping("/api/balance")
    BalanceResponse balance(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ledger.balance(business.id(), business.zone(), period, from, to);
    }
}
