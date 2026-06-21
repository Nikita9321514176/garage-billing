package com.garage.billing.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;

import com.garage.billing.config.GarageConfig;
import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;
import com.garage.billing.model.Payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    @Autowired private BillService    billService;
    @Autowired private PaymentService paymentService;

    // ── COLORS ──────────────────────────────────────────────
    private static final Color COLOR_DARK    = new Color(26,  26,  46);
    private static final Color COLOR_PRIMARY = new Color(13,  110, 253);
    private static final Color COLOR_SUCCESS = new Color(25,  135, 84);
    private static final Color COLOR_DANGER  = new Color(220, 53,  69);
    private static final Color COLOR_GOLD    = new Color(255, 193, 7);
    private static final Color COLOR_LIGHT   = new Color(248, 249, 250);
    private static final Color COLOR_MUTED   = new Color(108, 117, 125);
    private static final Color COLOR_WHITE   = Color.WHITE;
    private static final Color COLOR_BORDER  = new Color(222, 226, 230);
    private static final Color COLOR_AMBER   = new Color(99,  56,  6);

    // ── FONTS ────────────────────────────────────────────────
    private static final Font FONT_GARAGE_NAME =
        new Font(Font.HELVETICA, 20, Font.BOLD, COLOR_GOLD);
    private static final Font FONT_TAGLINE =
        new Font(Font.HELVETICA, 9,  Font.NORMAL, COLOR_MUTED);
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
    private static final Font FONT_GRAND_TOTAL =
        new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_DARK);
    private static final Font FONT_SECTION_LABEL =
        new Font(Font.HELVETICA, 8,  Font.BOLD, COLOR_MUTED);

    private static final DateTimeFormatter DATE_FMT  =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_ONLY =
        DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── MAIN: generateBillPdf ────────────────────────────────
    public byte[] generateBillPdf(Long billId)
            throws DocumentException, IOException {

        Bill bill = billService.findById(billId)
            .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        List<BillServiceItem> services = bill.getServices();
        if (services == null) services = List.of();

        List<Payment> payments = paymentService.getPaymentHistory(billId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        buildHeader(doc, bill);
        buildDivider(doc);
        buildCustomerCarSection(doc, bill);
        buildServicesTable(doc, services);
        buildTotalsSection(doc, bill);

        if (!payments.isEmpty()) {
            buildPaymentHistory(doc, payments);
        }

        buildFooter(doc);

        doc.close();
        return baos.toByteArray();
    }

    // ── SECTION 1: HEADER ────────────────────────────────────
    // Logo (left) + garage name/address + invoice details (right)
    private void buildHeader(Document doc, Bill bill)
            throws DocumentException, IOException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60f, 40f});
        table.setSpacingAfter(6f);

        // ── LEFT: Logo + Garage Info ──────────────────────
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        // Try to load logo from static/images/logo.png
        // If file not found, skip logo gracefully — no crash
        try {
            ClassPathResource logoResource =
                new ClassPathResource("static/images/logo.png");
            if (logoResource.exists()) {
                InputStream logoStream = logoResource.getInputStream();
                Image logo = Image.getInstance(logoStream.readAllBytes());
                // Scale logo to fit — max 80pt height, proportional width
                logo.scaleToFit(100f, 70f);
                logo.setSpacingAfter(4f);
                leftCell.addElement(logo);
            }
        } catch (Exception ignored) {
            // Logo not found — continue without it
        }

        // Garage name in gold
        Paragraph garageName = new Paragraph("THE J MOTORS", FONT_GARAGE_NAME);
        garageName.setSpacingAfter(1f);
        leftCell.addElement(garageName);

        leftCell.addElement(new Paragraph(
            "Premium Vehicle Service & Repair", FONT_TAGLINE));
        leftCell.addElement(new Paragraph(
            GarageConfig.ADDRESS_LINE_1, FONT_SMALL));
        leftCell.addElement(new Paragraph(
            GarageConfig.ADDRESS_LINE_2, FONT_SMALL));
        leftCell.addElement(new Paragraph(
            "Ph: " + GarageConfig.PHONE, FONT_SMALL));

        table.addCell(leftCell);

        // ── RIGHT: Invoice Details ────────────────────────
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);

        Paragraph invoiceLabel = new Paragraph("INVOICE", FONT_SECTION_LABEL);
        invoiceLabel.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invoiceLabel);

        Paragraph billNum = new Paragraph(bill.getBillNumber(), FONT_HEADING);
        billNum.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(billNum);

        String dateStr = bill.getBillDate() != null
            ? bill.getBillDate().format(DATE_FMT) : "N/A";
        Paragraph dateP = new Paragraph("Date: " + dateStr, FONT_SMALL);
        dateP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateP);

        if (bill.getDueDate() != null) {
            Paragraph dueP = new Paragraph(
                "Due: " + bill.getDueDate().format(DATE_ONLY), FONT_SMALL);
            dueP.setAlignment(Element.ALIGN_RIGHT);
            rightCell.addElement(dueP);
        }

        Paragraph statusP = buildStatusParagraph(bill.getPaymentStatus());
        statusP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(statusP);

        table.addCell(rightCell);
        doc.add(table);
    }

    // ── SECTION 2: DIVIDER ───────────────────────────────────
    private void buildDivider(Document doc) throws DocumentException {
        LineSeparator ls = new LineSeparator(
            2f, 100f, COLOR_DARK, Element.ALIGN_CENTER, -2f);
        doc.add(new Chunk(ls));
        doc.add(Chunk.NEWLINE);
    }

    // ── SECTION 3: CUSTOMER + CAR ────────────────────────────
    private void buildCustomerCarSection(Document doc, Bill bill)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});
        table.setSpacingBefore(6f);
        table.setSpacingAfter(10f);

        // Customer cell
        PdfPCell billToCell = new PdfPCell();
        billToCell.setBorder(Rectangle.BOX);
        billToCell.setBorderColor(COLOR_BORDER);
        billToCell.setPadding(10f);
        billToCell.setBackgroundColor(COLOR_LIGHT);
        billToCell.addElement(new Paragraph("CUSTOMER", FONT_SECTION_LABEL));
        billToCell.addElement(new Paragraph(
            bill.getCustomerName() != null ? bill.getCustomerName() : "—", FONT_BOLD));
        if (bill.getCustomerPhone() != null) {
            billToCell.addElement(new Paragraph("Ph: " + bill.getCustomerPhone(), FONT_MUTED));
        }
        table.addCell(billToCell);

        // Vehicle cell
        PdfPCell vehicleCell = new PdfPCell();
        vehicleCell.setBorder(Rectangle.BOX);
        vehicleCell.setBorderColor(COLOR_BORDER);
        vehicleCell.setPadding(10f);
        vehicleCell.setBackgroundColor(COLOR_LIGHT);
        vehicleCell.addElement(new Paragraph("VEHICLE", FONT_SECTION_LABEL));

        Font plateFont = new Font(Font.COURIER, 13, Font.BOLD, COLOR_AMBER);
        vehicleCell.addElement(new Paragraph(
            bill.getCarNumber() != null ? bill.getCarNumber() : "—", plateFont));

        if (bill.getCarModel() != null) {
            vehicleCell.addElement(new Paragraph(bill.getCarModel(), FONT_MUTED));
        }
        table.addCell(vehicleCell);
        doc.add(table);
    }

    // ── SECTION 4: SERVICES TABLE ─────────────────────────────
    private void buildServicesTable(Document doc, List<BillServiceItem> services)
            throws DocumentException {

        Paragraph heading = new Paragraph("SERVICES PERFORMED", FONT_SECTION_LABEL);
        heading.setSpacingAfter(4f);
        doc.add(heading);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8f, 30f, 42f, 20f});
        table.setSpacingAfter(8f);

        addHeaderCell(table, "#");
        addHeaderCell(table, "Service");
        addHeaderCell(table, "Description");
        addHeaderCell(table, "Amount");

        if (services.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(
                new Phrase("No services recorded", FONT_MUTED));
            emptyCell.setColspan(4);
            emptyCell.setPadding(8f);
            emptyCell.setBorderColor(COLOR_BORDER);
            table.addCell(emptyCell);
        } else {
            boolean alt = false;
            for (int i = 0; i < services.size(); i++) {
                BillServiceItem svc = services.get(i);
                Color rowBg = alt ? COLOR_LIGHT : COLOR_WHITE;
                alt = !alt;

                addDataCell(table, String.valueOf(i + 1),
                    FONT_MUTED, Element.ALIGN_CENTER, rowBg);
                addDataCell(table,
                    svc.getServiceName() != null ? svc.getServiceName() : "—",
                    FONT_BOLD, Element.ALIGN_LEFT, rowBg);
                addDataCell(table,
                    svc.getDescription() != null && !svc.getDescription().isEmpty()
                        ? svc.getDescription() : "—",
                    FONT_MUTED, Element.ALIGN_LEFT, rowBg);
                addDataCell(table,
                    formatCurrency(svc.getAmount()),
                    FONT_NORMAL, Element.ALIGN_RIGHT, rowBg);
            }
        }
        doc.add(table);
    }

    // ── SECTION 5: TOTALS ─────────────────────────────────────
    // Shows: Subtotal / Discount / Grand Total / Amount Paid / Balance Due
    private void buildTotalsSection(Document doc, Bill bill)
            throws DocumentException {

        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{50f, 50f});
        wrapper.setSpacingAfter(10f);

        // Empty left cell
        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(emptyCell);

        // Totals box — right side
        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.BOX);
        totalsCell.setBorderColor(COLOR_BORDER);
        totalsCell.setPadding(10f);
        totalsCell.setBackgroundColor(COLOR_LIGHT);

        // ── Subtotal ─────────────────────────────────────
        // Subtotal = totalAmount + discountAmount (services sum before discount)
        BigDecimal discount = bill.getDiscountAmount() != null
                ? bill.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal subtotal = bill.getTotalAmount() != null
                ? bill.getTotalAmount().add(discount) : discount;

        addTotalRow(totalsCell, "Subtotal",
            formatCurrency(subtotal), FONT_NORMAL, FONT_NORMAL);

        // ── Discount (only if > 0) ────────────────────────
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            Font discountLabelFont = new Font(Font.HELVETICA, 10, Font.NORMAL, COLOR_SUCCESS);
            Font discountValueFont = new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_SUCCESS);
            addTotalRow(totalsCell, "Discount",
                "- " + formatCurrency(discount),
                discountLabelFont, discountValueFont);
        }

        // ── Divider line ──────────────────────────────────
        Paragraph sep = new Paragraph(" ",
            new Font(Font.HELVETICA, 3, Font.NORMAL, COLOR_BORDER));
        sep.setSpacingBefore(2f);
        sep.setSpacingAfter(2f);
        totalsCell.addElement(sep);

        // ── Grand Total ───────────────────────────────────
        addTotalRow(totalsCell, "Grand Total",
            formatCurrency(bill.getTotalAmount()),
            FONT_GRAND_TOTAL, FONT_GRAND_TOTAL);

        // Another small gap
        totalsCell.addElement(new Paragraph(" ",
            new Font(Font.HELVETICA, 3, Font.NORMAL, COLOR_LIGHT)));

        // ── Amount Paid ───────────────────────────────────
        addTotalRow(totalsCell, "Amount Paid",
            formatCurrency(bill.getPaidAmount()),
            FONT_SUCCESS, FONT_SUCCESS);

        // ── Balance Due ───────────────────────────────────
        boolean hasBalance = bill.getBalanceAmount() != null
            && bill.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0;
        Font balFont = hasBalance ? FONT_DANGER : FONT_SUCCESS;
        addTotalRow(totalsCell, "Balance Due",
            formatCurrency(bill.getBalanceAmount()),
            balFont, balFont);

        // ── Payment Status ────────────────────────────────
        Paragraph statusLine = buildStatusParagraph(bill.getPaymentStatus());
        statusLine.setAlignment(Element.ALIGN_RIGHT);
        statusLine.setSpacingBefore(5f);
        totalsCell.addElement(statusLine);

        wrapper.addCell(totalsCell);
        doc.add(wrapper);
    }

    // ── SECTION 6: PAYMENT HISTORY ────────────────────────────
    private void buildPaymentHistory(Document doc, List<Payment> payments)
            throws DocumentException {

        Paragraph heading = new Paragraph("PAYMENT HISTORY", FONT_SECTION_LABEL);
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
                ? pmt.getPaymentDate().format(DATE_ONLY) : "—";
            addDataCell(table, dateStr,       FONT_NORMAL, Element.ALIGN_LEFT,   COLOR_WHITE);
            addDataCell(table, pmt.getPaymentMode() != null ? pmt.getPaymentMode() : "—",
                               FONT_MUTED,  Element.ALIGN_CENTER, COLOR_WHITE);
            addDataCell(table, pmt.getTransactionReference() != null
                && !pmt.getTransactionReference().isEmpty()
                ? pmt.getTransactionReference() : "—",
                               FONT_MUTED,  Element.ALIGN_LEFT,   COLOR_WHITE);
            addDataCell(table, formatCurrency(pmt.getAmountPaid()),
                               FONT_SUCCESS, Element.ALIGN_RIGHT,  COLOR_WHITE);
        }
        doc.add(table);
    }

    // ── SECTION 7: FOOTER ────────────────────────────────────
    private void buildFooter(Document doc) throws DocumentException {
        buildDivider(doc);

        Paragraph thankYou = new Paragraph(
            "Thank you for choosing The J Motors", FONT_BOLD);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        thankYou.setSpacingAfter(3f);
        doc.add(thankYou);

        Paragraph support = new Paragraph(
            "Support: 7303666143", FONT_MUTED);
        support.setAlignment(Element.ALIGN_CENTER);
        support.setSpacingAfter(2f);
        doc.add(support);

        Chunk websiteChunk = new Chunk(
            "https://the-j-motors.localo.site", FONT_LINK);
        websiteChunk.setAnchor("https://the-j-motors.localo.site");
        Paragraph websitePara = new Paragraph();
        websitePara.add(websiteChunk);
        websitePara.setAlignment(Element.ALIGN_CENTER);
        doc.add(websitePara);
    }

    // ── HELPERS ───────────────────────────────────────────────
    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_HEADER));
        cell.setBackgroundColor(COLOR_DARK);
        cell.setPadding(7f);
        cell.setBorderColor(COLOR_DARK);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text,
            Font font, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6f);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPCell parentCell,
            String label, String value,
            Font labelFont, Font valueFont) {
        PdfPTable row = new PdfPTable(2);
        try { row.setWidths(new float[]{55f, 45f}); } catch (Exception ignored) {}
        row.setWidthPercentage(100);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(2f);
        labelCell.setBackgroundColor(COLOR_LIGHT);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(2f);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setBackgroundColor(COLOR_LIGHT);

        row.addCell(labelCell);
        row.addCell(valueCell);
        parentCell.addElement(row);
    }

    private Paragraph buildStatusParagraph(String status) {
        Font statusFont;
        String label;
        if ("PAID".equals(status)) {
            statusFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_SUCCESS);
            label = "PAID IN FULL";
        } else if ("PARTIAL".equals(status)) {
            statusFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(133, 100, 4));
            label = "PARTIALLY PAID";
        } else {
            statusFont = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_DANGER);
            label = "PAYMENT PENDING";
        }
        return new Paragraph(label, statusFont);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        return "Rs. " + String.format("%,.2f", amount);
    }
}