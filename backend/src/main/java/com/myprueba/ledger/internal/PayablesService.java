package com.myprueba.ledger.internal;

import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.ValidationException;
import com.myprueba.suppliers.SupplierDeleting;
import com.myprueba.suppliers.SupplierDirectory;
import com.myprueba.suppliers.SupplierView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts payable: what the business owes its suppliers for purchases on credit. */
@Service
class PayablesService {

    private final PayablesRepository payables;
    private final SupplierDirectory suppliers;
    private final Clock clock;

    PayablesService(PayablesRepository payables, SupplierDirectory suppliers, Clock clock) {
        this.payables = payables;
        this.suppliers = suppliers;
        this.clock = clock;
    }

    PayablesResponse overview(UUID businessId, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        List<SupplierDebt> debts = payables.debts(businessId).entrySet().stream()
                .map(entry -> {
                    SupplierView supplier =
                            suppliers.find(businessId, entry.getKey()).orElseThrow();
                    Settlement.Unpaid unpaid = Settlement.unpaid(
                            payables.creditExpenses(businessId, supplier.id()), entry.getValue(), today);
                    return new SupplierDebt(
                            supplier.id(),
                            supplier.name(),
                            supplier.phone(),
                            Money.format(entry.getValue()),
                            unpaid.oldest(),
                            unpaid.overdue());
                })
                .toList();
        return new PayablesResponse(Money.format(payables.totalDebt(businessId)), debts);
    }

    PurchasesResponse purchases(UUID businessId, UUID supplierId) {
        suppliers.find(businessId, supplierId).orElseThrow(NotFoundException::new);
        Map<UUID, String> summaries = payables.itemSummaries(businessId, supplierId);
        List<PayablesRepository.Purchase> purchases = payables.purchases(businessId, supplierId);
        long total = 0;
        for (PayablesRepository.Purchase purchase : purchases) {
            total = Money.add(total, purchase.totalCents());
        }
        List<PurchaseEntry> items = purchases.stream()
                .map(p -> new PurchaseEntry(
                        p.id(),
                        p.credit() ? PaymentStatus.CREDIT : PaymentStatus.PAID,
                        Money.format(p.totalCents()),
                        p.concept() != null ? p.concept() : summaries.get(p.id()),
                        p.occurredAt()))
                .toList();
        return new PurchasesResponse(Money.format(total), items.size(), items);
    }

    SupplierAccountResponse account(UUID businessId, UUID supplierId) {
        SupplierView supplier = suppliers.find(businessId, supplierId).orElseThrow(NotFoundException::new);
        Map<UUID, String> summaries = payables.itemSummaries(businessId, supplierId);
        List<AccountEntry> entries = payables.account(businessId, supplierId).stream()
                .map(row -> new AccountEntry(
                        row.id(),
                        row.type(),
                        Money.format(row.amountCents()),
                        row.concept() != null ? row.concept() : summaries.get(row.id()),
                        row.occurredAt()))
                .toList();
        return new SupplierAccountResponse(
                new AccountSupplier(supplier.id(), supplier.name(), supplier.phone()),
                Money.format(payables.debt(businessId, supplierId)),
                entries);
    }

    @Transactional
    SupplierPaymentResponse create(UUID businessId, ZoneId zone, SupplierPaymentRequest request) {
        Instant now = clock.instant();
        SupplierPaymentData payment = validate(businessId, LedgerInput.payment(request, zone, now), 0);
        UUID id = UUID.randomUUID();
        payables.insertPayment(businessId, id, payment, now);
        return toResponse(id, payment);
    }

    @Transactional
    SupplierPaymentResponse update(UUID businessId, ZoneId zone, UUID id, SupplierPaymentRequest request) {
        Instant now = clock.instant();
        SupplierPaymentData previous = payables.findPayment(businessId, id).orElseThrow(NotFoundException::new);
        LedgerInput.PaymentInput input = LedgerInput.payment(request, zone, now);
        // The payment being edited is already subtracted from its supplier's debt: give it back.
        long credit = input.partyId().equals(previous.supplierId()) ? previous.amountCents() : 0;
        SupplierPaymentData payment = validate(businessId, input, credit);
        payables.updatePayment(businessId, id, payment, now);
        return toResponse(id, payment);
    }

    SupplierPaymentResponse get(UUID businessId, UUID id) {
        return toResponse(id, payables.findPayment(businessId, id).orElseThrow(NotFoundException::new));
    }

    @Transactional
    void delete(UUID businessId, UUID id) {
        if (payables.deletePayment(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    /** Vetoes deleting a supplier the business still owes, or that owes the business, money. */
    @EventListener
    void on(SupplierDeleting event) {
        if (payables.debt(event.businessId(), event.supplierId()) != 0) {
            throw new ConflictException("supplier_has_debt");
        }
    }

    private SupplierPaymentData validate(UUID businessId, LedgerInput.PaymentInput input, long credit) {
        SupplierView supplier = suppliers
                .find(businessId, input.partyId())
                .orElseThrow(() -> new NotFoundException("supplier_not_found"));
        long available = payables.debt(businessId, supplier.id()) + credit;
        if (input.amountCents() > available) {
            throw ValidationException.of("amount", "exceeds_debt");
        }
        return new SupplierPaymentData(
                supplier.id(),
                supplier.name(),
                input.amountCents(),
                input.paymentMethod(),
                input.occurredAt(),
                input.note());
    }

    private static SupplierPaymentResponse toResponse(UUID id, SupplierPaymentData payment) {
        return new SupplierPaymentResponse(
                id,
                MovementType.SUPPLIER_PAYMENT,
                payment.supplierId(),
                payment.supplierName(),
                Money.format(payment.amountCents()),
                payment.paymentMethod(),
                payment.occurredAt(),
                payment.note());
    }
}
