package com.hookledger.invoice;

import com.hookledger.service.CustomerStatementPdfService;
import com.hookledger.service.CustomerStatementService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Writes docs/sample-statement.png for the README (run manually after dependency changes). */
public final class SampleStatementImageGenerator {

    private SampleStatementImageGenerator() {}

    public static void main(String[] args) throws IOException {
        CustomerStatementService.CustomerStatement statement = new CustomerStatementService.CustomerStatement(
                "Acme Widgets LLC",
                "USD",
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-28"),
                4000,
                4500,
                List.of(
                        new CustomerStatementService.CustomerStatementLine(
                                LocalDate.parse("2026-02-05"), "invoice", "inv_demo_01", 2000, 6000),
                        new CustomerStatementService.CustomerStatementLine(
                                LocalDate.parse("2026-02-10"), "credit", "cn_demo_01", 500, 5500),
                        new CustomerStatementService.CustomerStatementLine(
                                LocalDate.parse("2026-02-15"), "payment", "pay_demo_01", 1000, 4500)));

        byte[] pdf = new CustomerStatementPdfService().render(statement);
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        Path imagePath = repoRoot.resolve("docs/sample-statement.png");
        Files.createDirectories(imagePath.getParent());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage page = renderer.renderImageWithDPI(0, 150, ImageType.RGB);
            ImageIO.write(page, "png", imagePath.toFile());
        }
        System.out.println("Wrote " + imagePath.toAbsolutePath());
    }
}
