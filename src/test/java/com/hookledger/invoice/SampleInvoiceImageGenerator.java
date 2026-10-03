package com.hookledger.invoice;

import com.hookledger.domain.Invoice;
import com.hookledger.domain.InvoiceLineItem;
import com.hookledger.service.InvoicePdfService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Writes docs/sample-invoice.png for the README (run manually after dependency changes). */
public final class SampleInvoiceImageGenerator {

    private SampleInvoiceImageGenerator() {}

    public static void main(String[] args) throws IOException {
        Instant createdAt = Instant.parse("2026-03-01T12:00:00Z");
        Invoice invoice = new Invoice(
                "Acme Widgets LLC",
                "100 Demo Plaza\nFictional City, FC 00000",
                LocalDate.parse("2026-04-15"),
                "USD",
                createdAt);
        invoice.addLineItem(new InvoiceLineItem(invoice, 0, "Widget subscription", 2500, 0));
        invoice.addLineItem(new InvoiceLineItem(invoice, 1, "Support hours", 750, 0));

        byte[] pdf = new InvoicePdfService().render(invoice);
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        Path imagePath = repoRoot.resolve("docs/sample-invoice.png");
        Files.createDirectories(imagePath.getParent());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage page = renderer.renderImageWithDPI(0, 150, ImageType.RGB);
            ImageIO.write(page, "png", imagePath.toFile());
        }
        System.out.println("Wrote " + imagePath.toAbsolutePath());
    }
}
