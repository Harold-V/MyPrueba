package com.myprueba.suppliers.internal;

import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import com.myprueba.suppliers.SupplierDeleting;
import com.myprueba.suppliers.SupplierDirectory;
import com.myprueba.suppliers.SupplierView;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SupplierService implements SupplierDirectory {

    private final SupplierRepository suppliers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SupplierService(SupplierRepository suppliers, ApplicationEventPublisher events, Clock clock) {
        this.suppliers = suppliers;
        this.events = events;
        this.clock = clock;
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<SupplierView> find(UUID businessId, UUID supplierId) {
        return suppliers.find(businessId, supplierId).map(c -> new SupplierView(c.id(), c.name(), c.phone()));
    }

    List<SupplierResponse> list(UUID businessId) {
        return suppliers.findAll(businessId);
    }

    @Transactional
    SupplierResponse create(UUID businessId, SupplierRequest request) {
        SupplierResponse supplier = parse(UUID.randomUUID(), request);
        suppliers.insert(businessId, supplier, clock.instant());
        return supplier;
    }

    @Transactional
    SupplierResponse update(UUID businessId, UUID id, SupplierRequest request) {
        suppliers.find(businessId, id).orElseThrow(NotFoundException::new);
        SupplierResponse supplier = parse(id, request);
        suppliers.update(businessId, supplier, clock.instant());
        return supplier;
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        suppliers.find(businessId, id).orElseThrow(NotFoundException::new);
        events.publishEvent(new SupplierDeleting(businessId, id));
        suppliers.delete(businessId, id);
    }

    private static SupplierResponse parse(UUID id, SupplierRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), 80));
        String phone = violations.capture(() -> Text.optional("phone", request.phone(), 40));
        String document = violations.capture(() -> Text.optional("document", request.document(), 30));
        String note = violations.capture(() -> Text.optional("note", request.note(), 500));
        violations.throwIfAny();
        return new SupplierResponse(id, name, phone, document, note);
    }
}
