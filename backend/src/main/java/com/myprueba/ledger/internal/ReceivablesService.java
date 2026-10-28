package com.myprueba.ledger.internal;

import com.myprueba.customers.CustomerDeleting;
import com.myprueba.customers.CustomerDirectory;
import com.myprueba.customers.CustomerView;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ReceivablesService {

    private final ReceivablesRepository receivables;
    private final CustomerDirectory customers;
    private final Clock clock;

    ReceivablesService(ReceivablesRepository receivables, CustomerDirectory customers, Clock clock) {
        this.receivables = receivables;
        this.customers = customers;
        this.clock = clock;
    }

    ReceivablesResponse overview(UUID businessId) {
        List<CustomerDebt> debts = receivables.debts(businessId).entrySet().stream()
                .map(entry -> {
                    CustomerView customer =
                            customers.find(businessId, entry.getKey()).orElseThrow();
                    return new CustomerDebt(
                            customer.id(), customer.name(), customer.phone(), Money.format(entry.getValue()));
                })
                .toList();
        return new ReceivablesResponse(Money.format(receivables.totalDebt(businessId)), debts);
    }

    AccountResponse account(UUID businessId, UUID customerId) {
        CustomerView customer = customers.find(businessId, customerId).orElseThrow(NotFoundException::new);
        List<AccountEntry> entries = receivables.account(businessId, customerId).stream()
                .map(row -> new AccountEntry(
                        row.id(), row.type(), Money.format(row.amountCents()), row.concept(), row.occurredAt()))
                .toList();
        return new AccountResponse(
                new AccountCustomer(customer.id(), customer.name(), customer.phone()),
                Money.format(receivables.debt(businessId, customerId)),
                entries);
    }

    @Transactional
    PaymentResponse create(UUID businessId, ZoneId zone, PaymentRequest request) {
        Instant now = clock.instant();
        PaymentData payment = validate(businessId, LedgerInput.payment(request, zone, now), 0);
        UUID id = UUID.randomUUID();
        receivables.insertPayment(businessId, id, payment, now);
        return toResponse(id, payment);
    }

    @Transactional
    PaymentResponse update(UUID businessId, ZoneId zone, UUID id, PaymentRequest request) {
        Instant now = clock.instant();
        PaymentData previous = receivables.findPayment(businessId, id).orElseThrow(NotFoundException::new);
        LedgerInput.PaymentInput input = LedgerInput.payment(request, zone, now);
        // The payment being edited is already subtracted from its customer's debt: give it back.
        long credit = input.customerId().equals(previous.customerId()) ? previous.amountCents() : 0;
        PaymentData payment = validate(businessId, input, credit);
        receivables.updatePayment(businessId, id, payment, now);
        return toResponse(id, payment);
    }

    PaymentResponse get(UUID businessId, UUID id) {
        return toResponse(id, receivables.findPayment(businessId, id).orElseThrow(NotFoundException::new));
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        if (receivables.deletePayment(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    /** Vetoes deleting a customer that still owes, or is owed, money. */
    @EventListener
    void on(CustomerDeleting event) {
        if (receivables.debt(event.businessId(), event.customerId()) != 0) {
            throw new ConflictException("customer_has_debt");
        }
    }

    private PaymentData validate(UUID businessId, LedgerInput.PaymentInput input, long credit) {
        CustomerView customer = customers
                .find(businessId, input.customerId())
                .orElseThrow(() -> new NotFoundException("customer_not_found"));
        long available = receivables.debt(businessId, customer.id()) + credit;
        if (input.amountCents() > available) {
            throw ValidationException.of("amount", "exceeds_debt");
        }
        return new PaymentData(
                customer.id(),
                customer.name(),
                input.amountCents(),
                input.paymentMethod(),
                input.occurredAt(),
                input.note());
    }

    private static PaymentResponse toResponse(UUID id, PaymentData payment) {
        return new PaymentResponse(
                id,
                MovementType.PAYMENT,
                payment.customerId(),
                payment.customerName(),
                Money.format(payment.amountCents()),
                payment.paymentMethod(),
                payment.occurredAt(),
                payment.note());
    }
}
