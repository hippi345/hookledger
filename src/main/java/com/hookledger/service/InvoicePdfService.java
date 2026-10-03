package com.hookledger.service;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceLineItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
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
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 16f;

    public byte[] render(Invoice invoice) {
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
                content.showText("INVOICE");
                content.endText();

                y -= LINE_HEIGHT * 2;
                content.setFont(fontRegular, 10);
                writeLine(content, MARGIN, y, "Invoice #: " + invoice.getInvoiceId());
                y -= LINE_HEIGHT;
                writeLine(
                        content,
                        MARGIN,
                        y,
                        "Issued: "
                                + DATE_FORMAT.format(
                                        invoice.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()));
                y -= LINE_HEIGHT;
                writeLine(content, MARGIN, y, "Due: " + DATE_FORMAT.format(invoice.getDueDate()));
                y -= LINE_HEIGHT;
                writeLine(content, MARGIN, y, "Status: " + invoice.getStatus().name());
                y -= LINE_HEIGHT;
                writeLine(content, MARGIN, y, "Currency: " + invoice.getCurrency().toUpperCase());

                y -= LINE_HEIGHT * 2;
                content.setFont(fontBold, 12);
                writeLine(content, MARGIN, y, "Bill to");
                y -= LINE_HEIGHT;
                content.setFont(fontRegular, 11);
                writeLine(content, MARGIN, y, invoice.getCustomerName());
                if (invoice.getCustomerAddress() != null && !invoice.getCustomerAddress().isBlank()) {
                    for (String addressLine : invoice.getCustomerAddress().split("\n")) {
                        y -= LINE_HEIGHT;
                        writeLine(content, MARGIN, y, addressLine.trim());
                    }
                }

                y -= LINE_HEIGHT * 2;
                content.setFont(fontBold, 11);
                float descX = MARGIN;
                float amountX = page.getMediaBox().getWidth() - MARGIN - 80;
                writeLine(content, descX, y, "Description");
                writeLine(content, amountX, y, "Amount");
                y -= LINE_HEIGHT;
                content.setFont(fontRegular, 11);

                List<InvoiceLineItem> items = invoice.getLineItems();
                for (InvoiceLineItem item : items) {
                    writeLine(content, descX, y, truncate(item.getDescription(), 64));
                    writeLine(content, amountX, y, formatMoney(item.getAmountMinor(), invoice.getCurrency()));
                    y -= LINE_HEIGHT;
                }

                y -= LINE_HEIGHT;
                content.setFont(fontBold, 12);
                writeLine(
                        content,
                        amountX - 40,
                        y,
                        "Total: " + formatMoney(invoice.getTotalAmountMinor(), invoice.getCurrency()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new InvoiceException("Unable to render invoice PDF", ex);
        }
    }

    private static void writeLine(PDPageContentStream content, float x, float y, String text) throws IOException {
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(sanitize(text));
        content.endText();
    }

    private static String formatMoney(long amountMinor, String currency) {
        BigDecimal major =
                BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return major.toPlainString() + " " + currency.toUpperCase();
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
