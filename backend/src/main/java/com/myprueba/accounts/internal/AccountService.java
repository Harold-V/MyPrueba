package com.myprueba.accounts.internal;

import com.myprueba.shared.ApiException;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountService {

    record Session(String token, MeView me) {}

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AccountRepository accounts;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String currency;
    private final String zone;
    private final String dummyHash;

    AccountService(
            AccountRepository accounts,
            SessionService sessions,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.currency}") String currency,
            @Value("${app.zone}") String zone) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.currency = currency;
        this.zone = zone;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    Session register(RegisterRequest request) {
        Violations violations = new Violations();
        String email = violations.capture(() -> parseEmail(request.email()));
        String password = request.password();
        if (password == null || password.length() < MIN_PASSWORD) {
            violations.add("password", "too_short");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            violations.add("password", "too_long");
        }
        String name = violations.capture(() -> Text.required("businessName", request.businessName(), 80));
        BusinessCategory category = violations.capture(
                () -> Text.optionalEnum("businessCategory", request.businessCategory(), BusinessCategory.class));
        violations.throwIfAny();

        if (accounts.emailExists(email)) {
            throw new ConflictException("email_taken");
        }
        Instant now = clock.instant();
        UUID userId = UUID.randomUUID();
        try {
            accounts.insertUser(userId, email, passwordEncoder.encode(password), now);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("email_taken");
        }
        accounts.insertBusiness(UUID.randomUUID(), userId, name, category, currency, zone, now);
        return new Session(sessions.open(userId), me(userId));
    }

    @Transactional
    Session login(LoginRequest request) {
        String email = request.email() == null ? "" : request.email().strip().toLowerCase(Locale.ROOT);
        String password = request.password() == null ? "" : request.password();
        var user = accounts.findUserByEmail(email);
        boolean matches = passwordEncoder.matches(
                password, user.map(u -> u.passwordHash()).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials");
        }
        UUID userId = user.get().id();
        return new Session(sessions.open(userId), me(userId));
    }

    MeView me(UUID userId) {
        UserView user = accounts.findUser(userId).orElseThrow();
        BusinessView business = accounts.findBusinessByOwner(userId).orElseThrow();
        return new MeView(user, business);
    }

    @Transactional
    BusinessView updateBusiness(OwnerPrincipal owner, BusinessRequest request) {
        Violations violations = new Violations();
        String name = violations.capture(() -> Text.required("name", request.name(), 80));
        BusinessCategory category =
                violations.capture(() -> Text.optionalEnum("category", request.category(), BusinessCategory.class));
        violations.throwIfAny();
        accounts.updateBusiness(owner.businessId(), name, category, clock.instant());
        return accounts.findBusinessByOwner(owner.userId()).orElseThrow();
    }

    private static String parseEmail(String raw) {
        String email = Text.required("email", raw, 254).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw com.myprueba.shared.ValidationException.of("email", "invalid_format");
        }
        return email;
    }
}
