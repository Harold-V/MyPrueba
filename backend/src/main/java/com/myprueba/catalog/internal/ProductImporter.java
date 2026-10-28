package com.myprueba.catalog.internal;

import com.myprueba.shared.FieldViolation;
import com.myprueba.shared.Money;
import com.myprueba.shared.Text;
import com.myprueba.shared.ValidationException;
import com.myprueba.shared.Violations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk upload of products. All or nothing: every row is validated first, and one bad row creates
 * nothing. Errors name the row as the user sees it in the file ({@code rows[3].price}).
 */
@Service
class ProductImporter {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2000;

    enum Column {
        NAME("nombre", "nombre del producto", "producto"),
        BARCODE("codigo de barras", "codigo", "sku", "referencia"),
        COST("costo", "precio de costo", "costo unitario"),
        PRICE("precio", "precio de venta", "valor"),
        CATEGORY("categoria"),
        STOCK("stock", "cantidad", "stock inicial"),
        MIN_STOCK("stock minimo");

        final List<String> headers;

        Column(String... headers) {
            this.headers = List.of(headers);
        }
    }

    private record Parsed(int row, ProductService.Draft draft, String category) {}

    private final ProductService catalog;
    private final CategoryService categories;

    ProductImporter(ProductService catalog, CategoryService categories) {
        this.catalog = catalog;
        this.categories = categories;
    }

    @Transactional
    ImportResponse importFile(UUID businessId, byte[] file, boolean excel) {
        if (file.length > MAX_BYTES) {
            throw ValidationException.of("file", "too_large");
        }
        List<Spreadsheet.Line> lines = excel ? Spreadsheet.xlsx(file) : Spreadsheet.csv(file);
        Spreadsheet.Line header = lines.stream()
                .filter(l -> !l.blank())
                .findFirst()
                .orElseThrow(() -> ValidationException.of("file", "empty_file"));
        Map<Column, Integer> columns = columns(header);
        if (!columns.containsKey(Column.NAME) || !columns.containsKey(Column.PRICE)) {
            throw ValidationException.of("file", "missing_columns");
        }
        List<Spreadsheet.Line> rows = lines.stream()
                .filter(l -> l.number() > header.number() && !l.blank())
                .toList();
        if (rows.isEmpty()) {
            throw ValidationException.of("file", "empty_file");
        }
        if (rows.size() > MAX_ROWS) {
            throw ValidationException.of("file", "too_many_rows");
        }

        List<FieldViolation> errors = new ArrayList<>();
        List<Parsed> parsed = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        for (Spreadsheet.Line line : rows) {
            Violations violations = new Violations();
            Parsed row = parse(violations, line, columns);
            try {
                violations.throwIfAny();
            } catch (ValidationException e) {
                errors.addAll(e.violations());
                continue;
            }
            String prefix = "rows[" + line.number() + "].";
            ProductService.Draft draft = row.draft();
            if (!names.add(ProductService.key(draft.name())) || catalog.nameTaken(businessId, draft.name())) {
                errors.add(new FieldViolation(prefix + "name", "product_name_taken"));
            }
            if (draft.barcode() != null
                    && (!barcodes.add(draft.barcode()) || catalog.barcodeTaken(businessId, draft.barcode()))) {
                errors.add(new FieldViolation(prefix + "barcode", "barcode_taken"));
            }
            parsed.add(row);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Map<String, UUID> categoryIds = new HashMap<>();
        for (Parsed row : parsed) {
            UUID categoryId = row.category() == null
                    ? null
                    : categoryIds.computeIfAbsent(
                            ProductService.key(row.category()),
                            k -> categories.findOrCreate(businessId, row.category()));
            ProductService.Draft draft = row.draft();
            catalog.insert(
                    businessId,
                    new ProductService.Draft(
                            draft.name(),
                            draft.priceCents(),
                            draft.costCents(),
                            categoryId,
                            draft.barcode(),
                            null,
                            null,
                            draft.stockMilli(),
                            draft.minStockMilli()));
        }
        return new ImportResponse(parsed.size());
    }

    private static Parsed parse(Violations violations, Spreadsheet.Line line, Map<Column, Integer> columns) {
        String prefix = "rows[" + line.number() + "].";
        String name = violations.capture(
                () -> Text.required(prefix + "name", value(line, columns, Column.NAME), ProductService.MAX_NAME));
        String barcode = violations.capture(() ->
                Text.optional(prefix + "barcode", value(line, columns, Column.BARCODE), ProductService.MAX_BARCODE));
        Long cost = violations.capture(
                () -> ProductService.optionalMoney(prefix + "cost", amount(value(line, columns, Column.COST))));
        Long price = violations.capture(
                () -> Money.parseCents(prefix + "price", amount(value(line, columns, Column.PRICE))));
        String category = violations.capture(() ->
                Text.optional(prefix + "category", value(line, columns, Column.CATEGORY), CategoryService.MAX_NAME));
        Long stock = violations.capture(
                () -> ProductService.optionalQuantity(prefix + "stock", quantity(value(line, columns, Column.STOCK))));
        Long min = violations.capture(() ->
                ProductService.optionalQuantity(prefix + "minStock", quantity(value(line, columns, Column.MIN_STOCK))));
        if (name == null || price == null) {
            return null;
        }
        return new Parsed(
                line.number(),
                new ProductService.Draft(name, price, cost, null, barcode, null, null, stock == null ? 0 : stock, min),
                category);
    }

    private static Map<Column, Integer> columns(Spreadsheet.Line header) {
        Map<Column, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.cells().size(); i++) {
            String cell = header.cell(i);
            if (cell == null) {
                continue;
            }
            String normalized = ProductService.fold(cell)
                    .replaceAll("[^a-z0-9 ]", " ")
                    .replaceAll("\\s+", " ")
                    .strip();
            for (Column column : Column.values()) {
                if (column.headers.contains(normalized)) {
                    columns.putIfAbsent(column, i);
                }
            }
        }
        return columns;
    }

    private static String value(Spreadsheet.Line line, Map<Column, Integer> columns, Column column) {
        Integer index = columns.get(column);
        return index == null ? null : line.cell(index);
    }

    /**
     * Amounts as a Colombian user types them: "$ 2.500" and "2.500" are 2500, "2.500,50" and
     * "1800,5" use a decimal comma, and "1800.5" (one or two decimals) a decimal point.
     */
    static String amount(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("[\\s$]", "");
        if (value.contains(",")) {
            return value.replace(".", "").replace(",", ".");
        }
        if (value.matches("\\d+\\.\\d{1,2}")) {
            return value;
        }
        return value.replace(".", "");
    }

    /** Quantities accept a decimal comma ("1,5"). */
    static String quantity(String raw) {
        return raw == null ? null : raw.strip().replace(",", ".");
    }
}
