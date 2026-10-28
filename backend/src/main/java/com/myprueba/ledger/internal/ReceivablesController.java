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
class ReceivablesController {

    private final ReceivablesService receivables;
    private final CurrentBusiness business;

    ReceivablesController(ReceivablesService receivables, CurrentBusiness business) {
        this.receivables = receivables;
        this.business = business;
    }

    @GetMapping("/api/receivables")
    ReceivablesResponse overview() {
        return receivables.overview(business.id(), business.zone());
    }

    @GetMapping("/api/customers/{id}/account")
    AccountResponse account(@PathVariable String id) {
        return receivables.account(business.id(), Ids.parseOrNotFound(id));
    }

    @PostMapping(path = "/api/customer-payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse create(@RequestBody PaymentRequest request) {
        return receivables.create(business.id(), business.zone(), request);
    }

    @GetMapping("/api/customer-payments/{id}")
    PaymentResponse get(@PathVariable String id) {
        return receivables.get(business.id(), Ids.parseOrNotFound(id));
    }

    @PutMapping(path = "/api/customer-payments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    PaymentResponse update(@PathVariable String id, @RequestBody PaymentRequest request) {
        return receivables.update(business.id(), business.zone(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/api/customer-payments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        receivables.delete(business.id(), Ids.parseOrNotFound(id));
    }
}
