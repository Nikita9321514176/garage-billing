package com.garage.billing.service;

// OpenPDF imports — all from com.lowagie.text package
// OpenPDF is a fork of iText 4, completely free for commercial use

import com.lowagie.text.*;           // Document, Paragraph, Phrase, etc.
import com.lowagie.text.pdf.*;       // PdfWriter, PdfPTable, PdfPCell, etc.
import com.lowagie.text.pdf.draw.LineSeparator;

import com.garage.billing.config.GarageConfig;
import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;
import com.garage.billing.model.Payment;
import com.garage.billing.service.BillService;
import com.garage.billing.service.PaymentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;           // java.awt.Color for PDF colors
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    @Autowired private BillService    billService;
    @Autowired private PaymentService paymentService;

    // ── COLORS ──────────────────────────────────────────────
    // Define colors once, reuse everywhere for consistency
    private static final Color COLOR_DARK     = new Color(26,  26,  46);  // #1a1a2e — dark navy
    private static final Color COLOR_PRIMARY  = new Color(13,  110, 253); // #0d6efd — Bootstrap blue
    private static final Color COLOR_SUCCESS  = new Color(25,  135, 84);  // #198754 — green
    private static final Color COLOR_DANGER   = new Color(220, 53,  69);  // #dc3545 — red
    private static final Color COLOR_WARNING  = new Color(255, 193, 7);   // #ffc107 — yellow
    private static final Color COLOR_LIGHT    = new Color(248, 249, 250); // #f8f9fa — light grey
    private static final Color COLOR_MUTED    = new Color(108, 117, 125); // #6c757d — muted text
    private static final Color COLOR_WHITE    = Color.WHITE;
    private static final Color COLOR_BORDER   = new Color(222, 226, 230); // #dee2e6

    // ── FONTS ────────────────────────────────────────────────
    // OpenPDF uses Font objects. BaseFont.HELVETICA is always
    // available — no need to install fonts on the server.
    private static final Font FONT_TITLE =
        new Font(Font.HELVETICA, 22, Font.BOLD, COLOR_DARK);
    private static final Font FONT_SUBTITLE =
        new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_HEADING =
        new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_DARK);
    private static final Font FONT_NORMAL =
        new Font(Font.HELVETICA, 10, Font.NORMAL, COLOR_DARK);
    private static final Font FONT_SMALL =
        new Font(Font.HELVETICA, 8,  Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_BOLD =
        new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_DARK);
    private static final Font FONT_TABLE_HEADER =
        new Font(Font.HELVETICA, 9,  Font.BOLD, COLOR_WHITE);
    private static final Font FONT_MUTED =
        new Font(Font.HELVETICA, 9,  Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_SUCCESS =
        new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_SUCCESS);
    private static final Font FONT_DANGER =
        new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_DANGER);
    private static final Font FONT_LINK =
        new Font(Font.HELVETICA, 9,  Font.UNDERLINE, COLOR_PRIMARY);

    // Date formatter for display
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_ONLY =
        DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── MAIN METHOD: generateBillPdf ─────────────────────────
    // Returns the PDF as a byte array.
    // The controller writes this to the HTTP response.
    public byte[] generateBillPdf(Long billId)
            throws DocumentException, IOException {

        // Step 1: Load bill + services + payments from DB
        Bill bill = billService.findById(billId)
            .orElseThrow(() -> new RuntimeException(
                "Bill not found: " + billId));

        List<BillServiceItem> services = bill.getServices();
        if (services == null || services.isEmpty()) {
            services = List.of();
        }

        List<Payment> payments =
            paymentService.getPaymentHistory(billId);

        // Step 2: Create in-memory output stream
        // We write the PDF to memory, not to a file on disk.
        // The byte[] is sent directly to the browser.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Step 3: Create Document — A4 page with margins
        // PageSize.A4 = 595 x 842 points (1 point = 1/72 inch)
        // Margins: left=40, right=40, top=40, bottom=40
        Document doc = new Document(
            PageSize.A4, 40, 40, 40, 40);

        // Step 4: PdfWriter connects the Document to the output stream
        PdfWriter writer = PdfWriter.getInstance(doc, baos);

        // Step 5: Open document — all content goes between open/close
        doc.open();

        // Step 6: Build all sections
        buildHeader(doc, bill);
        buildDivider(doc);
        buildCustomerCarSection(doc, bill);
        buildServicesTable(doc, services);
        buildTotalsSection(doc, bill);

        if (!payments.isEmpty()) {
            buildPaymentHistory(doc, payments);
        }

        buildFooter(doc, writer);

        // Step 7: Close document — flushes all content to stream
        doc.close();

        // Step 8: Return byte array
        return baos.toByteArray();
    }

    // ── SECTION 1: HEADER ────────────────────────────────────
    // Garage name (left) + Invoice number/date/status (right)
    private void buildHeader(Document doc, Bill bill)
            throws DocumentException {

        // PdfPTable with 2 columns — left: garage, right: invoice info
        // 100f = 100% of page width
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        // Column widths: 60% left, 40% right
        table.setWidths(new float[]{60f, 40f});
        table.setSpacingAfter(8f);

        // ── LEFT CELL: Garage details ─────────────────────
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        // Garage name — large, bold, dark
        Paragraph garageName = new Paragraph(
            GarageConfig.GARAGE_NAME, FONT_TITLE);
        garageName.setSpacingAfter(2f);
        leftCell.addElement(garageName);

        // Tagline
        leftCell.addElement(new Paragraph(
            GarageConfig.TAGLINE, FONT_SUBTITLE));

        // Address lines
        leftCell.addElement(new Paragraph(
            GarageConfig.ADDRESS_LINE_1, FONT_SMALL));
        leftCell.addElement(new Paragraph(
            GarageConfig.ADDRESS_LINE_2, FONT_SMALL));

        // Phone + Email
        leftCell.addElement(new Paragraph(
            "Ph: " + GarageConfig.PHONE
            + "  |  " + GarageConfig.EMAIL,
            FONT_SMALL));

        // GST number (if configured)
        if (GarageConfig.GST_NUMBER != null
                && !GarageConfig.GST_NUMBER.isEmpty()) {
            leftCell.addElement(new Paragraph(
                "GST: " + GarageConfig.GST_NUMBER,
                FONT_SMALL));
        }

        table.addCell(leftCell);

        // ── RIGHT CELL: Invoice details ───────────────────
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        // RIGHT_ALIGN text in this cell
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        // "INVOICE" label
        Paragraph invoiceLabel = new Paragraph(
            "INVOICE", FONT_SMALL);
        invoiceLabel.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invoiceLabel);

        // Bill number — large and bold
        Paragraph billNum = new Paragraph(
            bill.getBillNumber(), FONT_HEADING);
        billNum.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(billNum);

        // Bill date
        String dateStr = bill.getBillDate() != null
            ? bill.getBillDate().format(DATE_FMT) : "N/A";
        Paragraph dateP = new Paragraph(
            "Date: " + dateStr, FONT_SMALL);
        dateP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateP);

        // Due date
        if (bill.getDueDate() != null) {
            Paragraph dueP = new Paragraph(
                "Due: " + bill.getDueDate()
                    .format(DATE_ONLY),
                FONT_SMALL);
            dueP.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(dueP);
        }

        // Payment status badge (colored box)
        Paragraph statusP = buildStatusParagraph(
            bill.getPaymentStatus());
        statusP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(statusP);

        table.addCell(rightCell);
        doc.add(table);
    }

    // ── SECTION 2: HORIZONTAL DIVIDER ────────────────────────
    private void buildDivider(Document doc)
            throws DocumentException {
        // LineSeparator draws a horizontal line
        LineSeparator ls = new LineSeparator(
            2f,     // line thickness
            100f,   // width %
            COLOR_DARK,  // color
            Element.ALIGN_CENTER,
            -2f     // vertical offset
        );
        doc.add(new Chunk(ls));
        doc.add(Chunk.NEWLINE);
    }

    // ── SECTION 3: CUSTOMER + CAR ────────────────────────────
    private void buildCustomerCarSection(
            Document doc, Bill bill)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});
        table.setSpacingBefore(6f);
        table.setSpacingAfter(10f);

        // ── LEFT: Bill To ─────────────────────────────────
        PdfPCell billToCell = new PdfPCell();
        billToCell.setBorder(Rectangle.BOX);
        billToCell.setBorderColor(COLOR_BORDER);
        billToCell.setPadding(10f);
        billToCell.setBackgroundColor(COLOR_LIGHT);

        // Label
        Paragraph billToLabel = new Paragraph(
            "BILL TO", FONT_TABLE_HEADER);
        // Reuse table header font but dark background cell
        // so we need a dark label font
        Font labelFont = new Font(
            Font.HELVETICA, 8, Font.BOLD, COLOR_MUTED);
        billToLabel = new Paragraph("BILL TO", labelFont);
        billToCell.addElement(billToLabel);

        // Customer name — bold
        billToCell.addElement(new Paragraph(
            bill.getCustomerName() != null
                ? bill.getCustomerName() : "—",
            FONT_BOLD));

        // Phone
        if (bill.getCustomerPhone() != null) {
            billToCell.addElement(new Paragraph(
                "Ph: " + bill.getCustomerPhone(),
                FONT_MUTED));
        }

        table.addCell(billToCell);

        // ── RIGHT: Vehicle ────────────────────────────────
        PdfPCell vehicleCell = new PdfPCell();
        vehicleCell.setBorder(Rectangle.BOX);
        vehicleCell.setBorderColor(COLOR_BORDER);
        vehicleCell.setPadding(10f);
        vehicleCell.setBackgroundColor(COLOR_LIGHT);

        Font labelFont2 = new Font(
            Font.HELVETICA, 8, Font.BOLD, COLOR_MUTED);
        vehicleCell.addElement(
            new Paragraph("VEHICLE", labelFont2));

        // Car number in a styled box
        // We simulate the "number plate" look with a bold font
        Font plateFont = new Font(
            Font.COURIER, 13, Font.BOLD,
            new Color(99, 56, 6)); // dark amber
        Paragraph platePara = new Paragraph(
            bill.getCarNumber() != null
                ? bill.getCarNumber() : "—",
            plateFont);
        vehicleCell.addElement(platePara);

        // Model and brand
        if (bill.getCarModel() != null) {
            vehicleCell.addElement(new Paragraph(
                bill.getCarModel(), FONT_MUTED));
        }

        table.addCell(vehicleCell);
        doc.add(table);
    }

    // ── SECTION 4: SERVICES TABLE ─────────────────────────────
    private void buildServicesTable(
            Document doc,
            List<BillServiceItem> services)
            throws DocumentException {

        // Section heading
        Paragraph heading = new Paragraph(
            "SERVICES PERFORMED", new Font(
                Font.HELVETICA, 9, Font.BOLD, COLOR_MUTED));
        heading.setSpacingAfter(4f);
        doc.add(heading);

        // 4-column table: #, Service, Description, Amount
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        // Column widths: narrow #, wide service, wide desc, amount
        table.setWidths(new float[]{8f, 30f, 42f, 20f});
        table.setSpacingAfter(8f);

        // ── TABLE HEADER ROW ──────────────────────────────
        // Helper method adds a colored header cell
        addHeaderCell(table, "#");
        addHeaderCell(table, "Service");
        addHeaderCell(table, "Description");
        addHeaderCell(table, "Amount");

        // ── DATA ROWS ─────────────────────────────────────
        if (services.isEmpty()) {
            // Colspan = span all 4 columns for empty message
            PdfPCell emptyCell = new PdfPCell(
                new Phrase("No services recorded",
                    FONT_MUTED));
            emptyCell.setColspan(4);
            emptyCell.setPadding(8f);
            emptyCell.setBorderColor(COLOR_BORDER);
            table.addCell(emptyCell);
        } else {
            boolean alternate = false;
            for (int i = 0; i < services.size(); i++) {
                BillServiceItem svc = services.get(i);

                // Alternate row background for readability
                Color rowBg = alternate
                    ? COLOR_LIGHT : COLOR_WHITE;
                alternate = !alternate;

                // # column
                addDataCell(table,
                    String.valueOf(i + 1),
                    FONT_MUTED, Element.ALIGN_CENTER,
                    rowBg);

                // Service name — bold
                addDataCell(table,
                    svc.getServiceName() != null
                        ? svc.getServiceName() : "—",
                    FONT_BOLD, Element.ALIGN_LEFT,
                    rowBg);

                // Description
                addDataCell(table,
                    svc.getDescription() != null
                        && !svc.getDescription().isEmpty()
                        ? svc.getDescription() : "—",
                    FONT_MUTED, Element.ALIGN_LEFT,
                    rowBg);

                // Amount — right aligned
                addDataCell(table,
                    formatCurrency(svc.getAmount()),
                    FONT_NORMAL, Element.ALIGN_RIGHT,
                    rowBg);
            }
        }

        doc.add(table);
    }

    // ── SECTION 5: TOTALS ─────────────────────────────────────
    private void buildTotalsSection(
            Document doc, Bill bill)
            throws DocumentException {

        // Right-align totals using a 2-column table
        // Left column = empty space, right = totals
        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{55f, 45f});
        wrapper.setSpacingAfter(10f);

        // Empty left cell
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(emptyCell);

        // Right cell contains the totals box
        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.BOX);
        totalsCell.setBorderColor(COLOR_BORDER);
        totalsCell.setPadding(10f);
        totalsCell.setBackgroundColor(COLOR_LIGHT);

        // ── SUBTOTAL ────────────────────────────────────
        addTotalRow(totalsCell, "Subtotal",
            formatCurrency(bill.getTotalAmount()),
            FONT_NORMAL, FONT_NORMAL);

        // ── Separator line inside cell ───────────────────
        // We simulate a line with an underline paragraph
        Paragraph sep = new Paragraph("_".repeat(40),
            new Font(Font.HELVETICA, 4,
                Font.NORMAL, COLOR_BORDER));
        sep.setSpacingBefore(2f);
        sep.setSpacingAfter(2f);
        totalsCell.addElement(sep);

        // ── GRAND TOTAL ──────────────────────────────────
        Font totalLabelFont = new Font(
            Font.HELVETICA, 12, Font.BOLD, COLOR_DARK);
        Font totalValueFont = new Font(
            Font.HELVETICA, 12, Font.BOLD, COLOR_DARK);
        addTotalRow(totalsCell, "TOTAL",
            formatCurrency(bill.getTotalAmount()),
            totalLabelFont, totalValueFont);

        // ── PAID ─────────────────────────────────────────
        addTotalRow(totalsCell, "Amount Paid",
            formatCurrency(bill.getPaidAmount()),
            FONT_SUCCESS, FONT_SUCCESS);

        // ── BALANCE ──────────────────────────────────────
        boolean hasBalance = bill.getBalanceAmount() != null
            && bill.getBalanceAmount()
                .compareTo(BigDecimal.ZERO) > 0;

        Font balFont = hasBalance ? FONT_DANGER : FONT_SUCCESS;
        addTotalRow(totalsCell, "Balance Due",
            formatCurrency(bill.getBalanceAmount()),
            balFont, balFont);

        // ── PAYMENT STATUS ───────────────────────────────
        Paragraph statusLine = buildStatusParagraph(
            bill.getPaymentStatus());
        statusLine.setAlignment(Element.ALIGN_RIGHT);
        statusLine.setSpacingBefore(4f);
        totalsCell.addElement(statusLine);

        wrapper.addCell(totalsCell);
        doc.add(wrapper);
    }

    // ── SECTION 6: PAYMENT HISTORY ────────────────────────────
    private void buildPaymentHistory(
            Document doc, List<Payment> payments)
            throws DocumentException {

        Paragraph heading = new Paragraph(
            "PAYMENT HISTORY", new Font(
                Font.HELVETICA, 9,
                Font.BOLD, COLOR_MUTED));
        heading.setSpacingAfter(4f);
        doc.add(heading);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30f, 20f, 30f, 20f});
        table.setSpacingAfter(10f);

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Mode");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Amount");

        for (Payment pmt : payments) {
            String dateStr = pmt.getPaymentDate() != null
                ? pmt.getPaymentDate().format(DATE_ONLY)
                : "—";

            addDataCell(table, dateStr,
                FONT_NORMAL, Element.ALIGN_LEFT,
                COLOR_WHITE);
            addDataCell(table,
                pmt.getPaymentMode() != null
                    ? pmt.getPaymentMode() : "—",
                FONT_MUTED, Element.ALIGN_CENTER,
                COLOR_WHITE);
            addDataCell(table,
                pmt.getTransactionReference() != null
                    && !pmt.getTransactionReference()
                        .isEmpty()
                    ? pmt.getTransactionReference() : "—",
                FONT_MUTED, Element.ALIGN_LEFT,
                COLOR_WHITE);
            addDataCell(table,
                formatCurrency(pmt.getAmountPaid()),
                FONT_SUCCESS, Element.ALIGN_RIGHT,
                COLOR_WHITE);
        }

        doc.add(table);
    }

    // ── SECTION 7: FOOTER ────────────────────────────────────
    private void buildFooter(
            Document doc, PdfWriter writer)
            throws DocumentException {

        // Divider line
        buildDivider(doc);

        // Thank you message
        Paragraph thankYou = new Paragraph(
            GarageConfig.FOOTER_MESSAGE, FONT_MUTED);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        thankYou.setSpacingAfter(3f);
        doc.add(thankYou);

        // Website link
        // Chunk.setAnchor() makes it a clickable hyperlink in PDF
        Chunk websiteChunk = new Chunk(
            GarageConfig.WEBSITE, FONT_LINK);
        websiteChunk.setAnchor(
            "http://" + GarageConfig.WEBSITE);

        Paragraph websitePara = new Paragraph();
        websitePara.add(websiteChunk);
        websitePara.setAlignment(Element.ALIGN_CENTER);
        websitePara.setSpacingAfter(3f);
        doc.add(websitePara);

        // Generated by line
        Paragraph genBy = new Paragraph(
            "Generated by GarageBilling System",
            new Font(Font.HELVETICA, 7,
                Font.ITALIC, COLOR_MUTED));
        genBy.setAlignment(Element.ALIGN_CENTER);
        doc.add(genBy);
    }

    // ── HELPER: Add header cell to table ─────────────────────
    private void addHeaderCell(
            PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(
            new Phrase(text, FONT_TABLE_HEADER));
        cell.setBackgroundColor(COLOR_DARK);
        cell.setPadding(7f);
        cell.setBorderColor(COLOR_DARK);
        cell.setHorizontalAlignment(
            Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    // ── HELPER: Add data cell ─────────────────────────────────
    private void addDataCell(
            PdfPTable table,
            String text,
            Font font,
            int alignment,
            Color background) {
        PdfPCell cell = new PdfPCell(
            new Phrase(text, font));
        cell.setPadding(6f);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    // ── HELPER: Add total row inside a cell ───────────────────
    private void addTotalRow(
            PdfPCell parentCell,
            String label,
            String value,
            Font labelFont,
            Font valueFont) {

        // Use a nested 2-column table for label + value
        PdfPTable row = new PdfPTable(2);
        try { row.setWidths(new float[]{55f, 45f}); }
        catch (Exception ignored) {}
        row.setWidthPercentage(100);

        PdfPCell labelCell = new PdfPCell(
            new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(2f);
        labelCell.setBackgroundColor(COLOR_LIGHT);

        PdfPCell valueCell = new PdfPCell(
            new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(2f);
        valueCell.setHorizontalAlignment(
            Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(COLOR_LIGHT);

        row.addCell(labelCell);
        row.addCell(valueCell);

        parentCell.addElement(row);
    }

    // ── HELPER: Build status paragraph ────────────────────────
    private Paragraph buildStatusParagraph(String status) {
        Font statusFont;
        String label;

        if ("PAID".equals(status)) {
            statusFont = new Font(
                Font.HELVETICA, 9, Font.BOLD, COLOR_SUCCESS);
            label = "✓ PAID IN FULL";
        } else if ("PARTIAL".equals(status)) {
            statusFont = new Font(
                Font.HELVETICA, 9, Font.BOLD,
                new Color(133, 100, 4)); // dark amber
            label = "⚠ PARTIALLY PAID";
        } else {
            statusFont = new Font(
                Font.HELVETICA, 9, Font.BOLD, COLOR_DANGER);
            label = "✗ PAYMENT PENDING";
        }
        return new Paragraph(label, statusFont);
    }

    // ── HELPER: Format currency ───────────────────────────────
    // Formats BigDecimal as "₹1,500.00"
    // Uses ASCII Rs. instead of ₹ because
    // standard PDF fonts don't support the ₹ Unicode glyph
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        // String.format with %,.2f adds comma separators
        // and 2 decimal places
        return "Rs. " + String.format("%,.2f", amount);
    }
}