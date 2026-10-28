package com.myprueba.ledger.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PayablesController {

    private final PayablesService payables;
    private final CurrentBusiness business;

    PayablesController(PayablesService payables, CurrentBusiness business) {
        this.payables = payables;
        this.business = business;
    }

    @GetMapping("/api/payables")
    PayablesResponse overview() {
        return payables.overview(business.id(), business.zone());
    }

    @GetMapping("/api/suppliers/{id}/account")
    SupplierAccountResponse account(@PathVariable String id) {
        return payables.account(business.id(), Ids.parseOrNotFound(id));
    }

    @GetMapping("/api/suppliers/{id}/purchases")
    PurchasesResponse purchases(@PathVariable String id) {
        return payables.purchases(business.id(), Ids.parseOrNotFound(id));
    }

    @PostMapping(path = "/api/supplier-payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    SupplierPaymentResponse create(@RequestBody SupplierPaymentRequest request) {
        return payables.create(business.id(), business.zone(), request);
    }

    @GetMapping("/api/supplier-payments/{id}")
    SupplierPaymentResponse get(@PathVariable String id) {
        return payables.get(business.id(), Ids.parseOrNotFound(id));
    }

    @PutMapping(path = "/api/supplier-payments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    SupplierPaymentResponse update(@PathVariable String id, @RequestBody SupplierPaymentRequest request) {
        return payables.update(business.id(), business.zone(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/api/supplier-payments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        payables.delete(business.id(), Ids.parseOrNotFound(id));
    }
}
