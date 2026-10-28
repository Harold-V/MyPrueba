package com.myprueba.customers.internal;

import com.myprueba.customers.CustomerDeleting;
import com.myprueba.customers.CustomerDirectory;
import com.myprueba.customers.CustomerView;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService implements CustomerDirectory {

    private final CustomerRepository customers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CustomerService(CustomerRepository customers, ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.events = events;
        this.clock = clock;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<CustomerView> find(UUID businessId, UUID customerId) {
        return customers.find(businessId, customerId).map(c -> new CustomerView(c.id(), c.name(), c.phone()));
    }

    List<CustomerResponse> list(UUID businessId) {
        return customers.findAll(businessId);
    }

    @Transactional
    CustomerResponse create(UUID businessId, CustomerRequest request) {
        CustomerResponse customer = parse(UUID.randomUUID(), request);
        customers.insert(businessId, customer, clock.instant());
        return customer;
    }

    @Transactional
    CustomerResponse update(UUID businessId, UUID id, CustomerRequest request) {
        customers.find(businessId, id).orElseThrow(NotFoundException::new);
        CustomerResponse customer = parse(id, request);
        customers.update(businessId, customer, clock.instant());
        return customer;
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        customers.find(businessId, id).orElseThrow(NotFoundException::new);
        events.publishEvent(new CustomerDeleting(businessId, id));
        customers.delete(businessId, id);
    }

    private static CustomerResponse parse(UUID id, CustomerRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), 80));
        String phone = violations.capture(() -> Text.optional("phone", request.phone(), 40));
        String note = violations.capture(() -> Text.optional("note", request.note(), 500));
        violations.throwIfAny();
        return new CustomerResponse(id, name, phone, note);
    }
}
