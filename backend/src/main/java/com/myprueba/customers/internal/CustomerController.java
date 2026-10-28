package com.myprueba.customers.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.Ids;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final CustomerService customers;
    private final CurrentBusiness business;

    CustomerController(CustomerService customers, CurrentBusiness business) {
        this.customers = customers;
        this.business = business;
    }

    @GetMapping
    List<CustomerResponse> list() {
        return customers.list(business.id());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse create(@RequestBody CustomerRequest request) {
        return customers.create(business.id(), request);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    CustomerResponse update(@PathVariable String id, @RequestBody CustomerRequest request) {
        return customers.update(business.id(), Ids.parseOrNotFound(id), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        customers.delete(business.id(), Ids.parseOrNotFound(id));
    }
}
