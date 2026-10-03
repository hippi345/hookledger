package com.hookledger.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class CustomerStatementPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;

    public byte[] render(CustomerStatementService.CustomerStatement statement) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float y = page.getMediaBox().getHeight() - MARGIN;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(fontBold, 20);
                content.beginText();
                content.newLineAtOffset(MARGIN, y);
                content.showText("CUSTOMER STATEMENT");
                content.endText();

                y -= LINE_HEIGHT * 2;
                content.setFont(fontRegular, 10);
                writeLine(content, MARGIN, y, "Customer: " + statement.customer());
                y -= LINE_HEIGHT;
                writeLine(
                        content,
                        MARGIN,
                        y,
                        "Period: " + DATE_FORMAT.format(statement.from()) + " through "
                                + DATE_FORMAT.format(statement.to()));
                y -= LINE_HEIGHT;
                writeLine(content, MARGIN, y, "Currency: " + statement.currency().toUpperCase());

                y -= LINE_HEIGHT * 2;
                content.setFont(fontBold, 11);
                writeLine(
                        content,
                        MARGIN,
                        y,
                        "Starting balance: "
                                + formatMinor(statement.startingBalanceMinor(), statement.currency()));
                y -= LINE_HEIGHT;
                content.setFont(fontRegular, 11);

                float dateX = MARGIN;
                float typeX = MARGIN + 72;
                float idX = MARGIN + 130;
                float amountX = page.getMediaBox().getWidth() - MARGIN - 200;
                float balanceX = page.getMediaBox().getWidth() - MARGIN - 95;

                y -= LINE_HEIGHT;
                content.setFont(fontBold, 11);
                writeLine(content, dateX, y, "Date");
                writeLine(content, typeX, y, "Type");
                writeLine(content, idX, y, "Id");
                writeLine(content, amountX, y, "Amount");
                writeLine(content, balanceX, y, "Balance");
                y -= LINE_HEIGHT;
                content.setFont(fontRegular, 10);

                List<CustomerStatementService.CustomerStatementLine> lines = statement.lines();
                for (CustomerStatementService.CustomerStatementLine line : lines) {
                    writeLine(content, dateX, y, DATE_FORMAT.format(line.date()));
                    writeLine(content, typeX, y, line.type());
                    writeLine(content, idX, y, truncate(line.id(), 18));
                    writeLine(content, amountX, y, formatMinor(line.amountMinor(), statement.currency()));
                    writeLine(content, balanceX, y, formatMinor(line.balanceMinor(), statement.currency()));
                    y -= LINE_HEIGHT;
                }

                y -= LINE_HEIGHT;
                content.setFont(fontBold, 12);
                writeLine(
                        content,
                        MARGIN,
                        y,
                        "Ending balance: "
                                + formatMinor(statement.endingBalanceMinor(), statement.currency()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new InvoiceException("Unable to render customer statement PDF", ex);
        }
    }

    private static void writeLine(PDPageContentStream content, float x, float y, String text) throws IOException {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(sanitize(text));
        content.endText();
    }

    private static String formatMinor(long amountMinor, String currency) {
        return amountMinor + " minor (" + currency.toUpperCase() + ")";
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 3) + "...";
    }

    private static String sanitize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 32 && ch <= 126) {
                builder.append(ch);
            } else if (ch == '\t') {
                builder.append(' ');
            }
        }
        return builder.toString();
    }
}
