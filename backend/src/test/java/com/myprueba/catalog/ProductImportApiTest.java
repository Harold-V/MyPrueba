package com.myprueba.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myprueba.support.ApiTest;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Phase 4: bulk upload from Excel or CSV, all or nothing. */
class ProductImportApiTest extends ApiTest {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    Cookie sid;

    @BeforeEach
    void setUp() throws Exception {
        sid = registerOwner();
    }

    static byte[] xlsx(List<List<Object>> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Workbook workbook = new Workbook(out, "test", "1.0");
        Worksheet sheet = workbook.newWorksheet("Productos");
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                Object value = rows.get(r).get(c);
                if (value instanceof Number number) {
                    sheet.value(r, c, number);
                } else if (value != null) {
                    sheet.value(r, c, value.toString());
                }
            }
        }
        workbook.finish();
        return out.toByteArray();
    }

    @Test
    void importsProductsFromExcelCreatingCategories() throws Exception {
        byte[] file = xlsx(List.of(
                List.of("Nombre", "Código de barras", "Costo", "Precio", "Categoría", "Stock"),
                List.of("Gaseosa", "7702004003508", 2000, 3000, "Bebidas", 24),
                List.of("Agua", "", 800, 1500, "Bebidas", 12),
                List.of("Jabón", "JAB-01", 1800.5, "2.500", "", "")));
        mvc.perform(post("/api/products/import").contentType(XLSX).content(file).cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3));
        mvc.perform(get("/api/product-categories").cookie(sid))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bebidas"));
        mvc.perform(get("/api/products?q=7702004003508").cookie(sid))
                .andExpect(jsonPath("$[0].cost").value("2000.00"))
                .andExpect(jsonPath("$[0].price").value("3000.00"))
                .andExpect(jsonPath("$[0].categoryName").value("Bebidas"));
        mvc.perform(get("/api/products?q=jab").cookie(sid))
                .andExpect(jsonPath("$[0].cost").value("1800.50"))
                .andExpect(jsonPath("$[0].price").value("2500.00"));
        mvc.perform(get("/api/inventory?q=gaseosa").cookie(sid))
                .andExpect(jsonPath("$.items[0].stock").value("24"));
    }

    @Test
    void anInvalidRowRejectsTheWholeFile() throws Exception {
        byte[] file = xlsx(List.of(
                List.of("Nombre", "Código de barras", "Costo", "Precio", "Categoría"),
                List.of("Gaseosa", "", 2000, 3000, "Bebidas"),
                List.of("Agua", "", 800, "barata", "Bebidas")));
        mvc.perform(post("/api/products/import").contentType(XLSX).content(file).cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[3].price"))
                .andExpect(jsonPath("$.errors[0].code").value("invalid_format"));
        mvc.perform(get("/api/products").cookie(sid))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/product-categories").cookie(sid))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void repeatedNamesAndCodesAreRowErrors() throws Exception {
        mvc.perform(json(post("/api/products"), """
                        {"name":"Gaseosa","price":"3000"}
                        """).cookie(sid)).andExpect(status().isCreated());
        String csv = """
                nombre;codigo de barras;costo;precio;categoria
                Gaseosa;;2000;3000;
                Agua;A1;800;1500;
                Agua grande;A1;900;2000;
                """;
        mvc.perform(post("/api/products/import")
                        .contentType("text/csv")
                        .content(csv.getBytes(StandardCharsets.UTF_8))
                        .cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rows[2].name"))
                .andExpect(jsonPath("$.errors[0].code").value("product_name_taken"))
                .andExpect(jsonPath("$.errors[1].field").value("rows[4].barcode"))
                .andExpect(jsonPath("$.errors[1].code").value("barcode_taken"));
    }

    @Test
    void importsCsvWithCommas() throws Exception {
        String csv = "Nombre,Precio,Stock mínimo\nPan,500,10\n";
        mvc.perform(post("/api/products/import")
                        .contentType("text/csv")
                        .content(csv.getBytes(StandardCharsets.UTF_8))
                        .cookie(sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(get("/api/inventory").cookie(sid))
                .andExpect(jsonPath("$.items[0].minStock").value("10"));
    }

    @Test
    void rejectsFilesWithoutTheRequiredColumnsOrOfAnotherType() throws Exception {
        mvc.perform(post("/api/products/import")
                        .contentType("text/csv")
                        .content("Descripcion;Monto\nPan;500\n".getBytes(StandardCharsets.UTF_8))
                        .cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].code").value("missing_columns"));
        mvc.perform(post("/api/products/import")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content(new byte[] {1, 2, 3})
                        .cookie(sid))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/products/import")
                        .contentType(XLSX)
                        .content(new byte[] {1, 2, 3})
                        .cookie(sid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("unreadable_file"));
    }
}
