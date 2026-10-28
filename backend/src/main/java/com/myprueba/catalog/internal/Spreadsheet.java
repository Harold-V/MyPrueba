package com.myprueba.catalog.internal;

import com.myprueba.shared.ValidationException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;

/** Reads the first sheet of an .xlsx file, or a .csv file, as rows of text cells. */
final class Spreadsheet {

    /** One row of the file; {@code number} is the row as the user sees it (the header is 1). */
    record Line(int number, List<String> cells) {

        String cell(int index) {
            return index < 0 || index >= cells.size() ? null : cells.get(index);
        }

        boolean blank() {
            return cells.stream().allMatch(c -> c == null || c.isBlank());
        }
    }

    private Spreadsheet() {}

    static List<Line> xlsx(byte[] file) {
        List<Line> lines = new ArrayList<>();
        try (ReadableWorkbook workbook = new ReadableWorkbook(new ByteArrayInputStream(file));
                Stream<Row> rows = workbook.getFirstSheet().openStream()) {
            rows.forEach(row -> {
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < row.getCellCount(); i++) {
                    cells.add(text(row.getCell(i)));
                }
                lines.add(new Line(row.getRowNum(), cells));
            });
        } catch (Exception e) {
            throw ValidationException.of("file", "unreadable_file");
        }
        return lines;
    }

    /** A number cell keeps its exact decimal value ("1800.5"); anything else is its text. */
    private static String text(Cell cell) {
        if (cell == null || cell.getType() == CellType.EMPTY) {
            return null;
        }
        if (cell.getType() == CellType.NUMBER) {
            return cell.asNumber().stripTrailingZeros().toPlainString();
        }
        return cell.getText();
    }

    /** CSV as Excel saves it in Colombia: ";" or "," separated, UTF-8 or Windows-1252. */
    static List<Line> csv(byte[] file) {
        String content = decode(file);
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        String[] rawLines = content.split("\r\n|\n|\r", -1);
        char separator = rawLines.length > 0 && rawLines[0].contains(";") ? ';' : ',';
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < rawLines.length; i++) {
            lines.add(new Line(i + 1, split(rawLines[i], separator)));
        }
        return lines;
    }

    private static String decode(byte[] file) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(file, Charset.forName("windows-1252"));
        }
    }

    /** Splits one line, honouring double-quoted fields ("a;b" stays one cell, "" is a quote). */
    private static List<String> split(String line, char separator) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
