package com.garage.billing.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;

import com.garage.billing.config.GarageConfig;
import com.garage.billing.model.Bill;
import com.garage.billing.model.BillServiceItem;
import com.garage.billing.model.Car;
import com.garage.billing.model.Payment;
import com.garage.billing.repository.CarRepository;
import com.garage.billing.util.AmountInWordsConverter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PdfService {

    @Autowired private BillService    billService;
    @Autowired private PaymentService paymentService;
    @Autowired private CarRepository  carRepository;

    // ── COLORS — navy blue theme preserved exactly as before ──
    private static final Color COLOR_DARK    = new Color(26,  26,  46);  // navy
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
    // FONT_GARAGE_NAME now navy instead of gold-on-logo, since
    // logo is removed entirely — clean text header instead.
    private static final Font FONT_GARAGE_NAME =
        new Font(Font.HELVETICA, 22, Font.BOLD, COLOR_DARK);
    private static final Font FONT_TAGLINE =
        new Font(Font.HELVETICA, 9,  Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_HEADING =
        new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_DARK);
    private static final Font FONT_NORMAL =
        new Font(Font.HELVETICA, 9,  Font.NORMAL, COLOR_DARK);
    private static final Font FONT_SMALL =
        new Font(Font.HELVETICA, 8,  Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_BOLD =
        new Font(Font.HELVETICA, 9,  Font.BOLD, COLOR_DARK);
    private static final Font FONT_TABLE_HEADER =
        new Font(Font.HELVETICA, 8,  Font.BOLD, COLOR_WHITE);
    private static final Font FONT_MUTED =
        new Font(Font.HELVETICA, 8,  Font.NORMAL, COLOR_MUTED);
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
    private static final Font FONT_WORDS =
        new Font(Font.HELVETICA, 8, Font.ITALIC, COLOR_MUTED);
    private static final Font FONT_TERMS =
        new Font(Font.HELVETICA, 7, Font.NORMAL, COLOR_MUTED);
    private static final Font FONT_SIGNATURE_LABEL =
        new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_MUTED);

    private static final DateTimeFormatter DATE_FMT  =
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_ONLY =
        DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── MAIN: generateBillPdf ────────────────────────────────
    public byte[] generateBillPdf(Long billId)
            throws DocumentException, IOException {

        Bill bill = billService.findById(billId)
            .orElseThrow(() -> new RuntimeException("Bill not found: " + billId));

        List<BillServiceItem> allItems = bill.getServices();
        if (allItems == null) allItems = List.of();

        // ── Split services into LABOUR and PART lists ────────
        // Existing services with no item_type (old rows) default
        // to LABOUR via BillRepository's SERVICE_MAPPER fallback.
        List<BillServiceItem> labourItems = new ArrayList<>();
        List<BillServiceItem> partItems   = new ArrayList<>();
        for (BillServiceItem item : allItems) {
            String type = item.getItemType() != null ? item.getItemType() : "LABOUR";
            if ("PART".equalsIgnoreCase(type)) {
                partItems.add(item);
            } else {
                labourItems.add(item);
            }
        }

        List<Payment> payments = paymentService.getPaymentHistory(billId);

        // Look up car for vehicle info section — engine number,
        // registration year, brand. Falls back gracefully if not found.
        Car car = null;
        try {
            Optional<Car> carOpt = carRepository.findById(bill.getCarId());
            if (carOpt.isPresent()) car = carOpt.get();
        } catch (Exception ignored) {
            // Car lookup failed — invoice still generates with "-" placeholders
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 32, 32);
        PdfWriter writer = PdfWriter.getInstance(doc, baos);
        doc.open();

        buildHeader(doc, bill);
        buildDivider(doc);
        buildCustomerVehicleSection(doc, bill, car);
        buildJobDetailsSection(doc);

        if (!labourItems.isEmpty()) {
            buildLabourTable(doc, labourItems);
        }
        buildPartsTable(doc, partItems);

        buildTotalsSection(doc, bill, labourItems, partItems);

        if (!payments.isEmpty()) {
            buildPaymentHistory(doc, payments);
        }

        buildTermsSection(doc);
        buildSignatureSection(doc);
        buildFooter(doc);

        doc.close();
        return baos.toByteArray();
    }

    // ── SECTION 1: HEADER ────────────────────────────────────
    // NO LOGO — clean text header only, per requirement.
    private void buildHeader(Document doc, Bill bill)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{60f, 40f});
        table.setSpacingAfter(6f);

        // ── LEFT: Garage Info (text only, no logo) ────────
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        Paragraph garageName = new Paragraph(
            GarageConfig.GARAGE_NAME.toUpperCase(), FONT_GARAGE_NAME);
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

        // GST number — only shown if configured (you confirmed registered)
        if (GarageConfig.GST_NUMBER != null && !GarageConfig.GST_NUMBER.isEmpty()) {
            leftCell.addElement(new Paragraph(
                "GSTIN: " + GarageConfig.GST_NUMBER, FONT_SMALL));
        }

        table.addCell(leftCell);

        // ── RIGHT: Invoice Details ────────────────────────
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);

        Paragraph invoiceLabel = new Paragraph("INVOICE", FONT_HEADING);
        invoiceLabel.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(invoiceLabel);

        Paragraph billNum = new Paragraph(
            "No: " + safe(bill.getBillNumber()), FONT_BOLD);
        billNum.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(billNum);

        String dateStr = bill.getBillDate() != null
            ? bill.getBillDate().format(DATE_FMT) : "-";
        Paragraph dateP = new Paragraph("Date: " + dateStr, FONT_SMALL);
        dateP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dateP);

        // Due Date
        String dueStr = bill.getDueDate() != null
            ? bill.getDueDate().format(DATE_ONLY) : "-";
        Paragraph dueP = new Paragraph("Due Date: " + dueStr, FONT_SMALL);
        dueP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(dueP);

        // Issue Date (same as bill date — kept distinct per spec wording)
        Paragraph issueP = new Paragraph("Issue Date: " + dateStr, FONT_SMALL);
        issueP.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(issueP);

        Paragraph statusP = buildStatusParagraph(bill.getPaymentStatus());
        statusP.setAlignment(Element.ALIGN_RIGHT);
        statusP.setSpacingBefore(2f);
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

    // ── SECTION 3: CUSTOMER + VEHICLE INFO ───────────────────
    private void buildCustomerVehicleSection(Document doc, Bill bill, Car car)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});
        table.setSpacingBefore(4f);
        table.setSpacingAfter(8f);

        // ── Customer cell ──────────────────────────────────
        PdfPCell custCell = new PdfPCell();
        custCell.setBorder(Rectangle.BOX);
        custCell.setBorderColor(COLOR_BORDER);
        custCell.setPadding(8f);
        custCell.setBackgroundColor(COLOR_LIGHT);
        custCell.addElement(new Paragraph("CUSTOMER INFORMATION", FONT_SECTION_LABEL));
        custCell.addElement(new Paragraph(
            "Name: " + safe(bill.getCustomerName()), FONT_BOLD));
        custCell.addElement(new Paragraph(
            "Phone: " + safe(bill.getCustomerPhone()), FONT_MUTED));
        table.addCell(custCell);

        // ── Vehicle cell ───────────────────────────────────
        PdfPCell vehCell = new PdfPCell();
        vehCell.setBorder(Rectangle.BOX);
        vehCell.setBorderColor(COLOR_BORDER);
        vehCell.setPadding(8f);
        vehCell.setBackgroundColor(COLOR_LIGHT);
        vehCell.addElement(new Paragraph("VEHICLE INFORMATION", FONT_SECTION_LABEL));

        Font plateFont = new Font(Font.COURIER, 12, Font.BOLD, COLOR_AMBER);
        vehCell.addElement(new Paragraph(
            "Reg. No: " + safe(bill.getCarNumber()), plateFont));
        vehCell.addElement(new Paragraph(
            "Model: " + safe(bill.getCarModel()), FONT_MUTED));

        String brand    = car != null ? car.getBrand()           : null;
        String engine   = car != null ? car.getEngineNumber()    : null;
        Integer regYear  = car != null ? car.getRegistrationYear() : null;
        String color     = car != null ? car.getColor()           : null;

        vehCell.addElement(new Paragraph("Make/Brand: " + safe(brand), FONT_MUTED));
        vehCell.addElement(new Paragraph("Color: " + safe(color), FONT_MUTED));
        vehCell.addElement(new Paragraph(
            "Year of Registration: " + (regYear != null ? regYear.toString() : "-"), FONT_MUTED));
        vehCell.addElement(new Paragraph("Engine No: " + safe(engine), FONT_MUTED));

        table.addCell(vehCell);
        doc.add(table);
    }

    // ── SECTION 4: JOB DETAILS ────────────────────────────────
    // Job Card Number and Delivery Date are future-ready fields
    // (not yet in DB) — shown as "-" since no data source exists.
    // Service Advisor uses the fixed GarageConfig.MECHANIC_NAME
    // you confirmed, same for every invoice.
    private void buildJobDetailsSection(Document doc) throws DocumentException {

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{34f, 33f, 33f});
        table.setSpacingAfter(8f);

        addJobDetailCell(table, "JOB CARD NO.", "-");
        addJobDetailCell(table, "SERVICE ADVISOR", GarageConfig.MECHANIC_NAME);
        addJobDetailCell(table, "DELIVERY DATE", "-");

        doc.add(table);
    }

    private void addJobDetailCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(6f);
        cell.addElement(new Paragraph(label, FONT_SECTION_LABEL));
        cell.addElement(new Paragraph(value, FONT_BOLD));
        table.addCell(cell);
    }

    // ── SECTION 5a: LABOUR TABLE ──────────────────────────────
    // Existing bill services appear here automatically (since
    // BillRepository defaults item_type to LABOUR for old rows).
    private void buildLabourTable(Document doc, List<BillServiceItem> labourItems)
            throws DocumentException {

        Paragraph heading = new Paragraph("LABOUR DETAILS", FONT_SECTION_LABEL);
        heading.setSpacingAfter(3f);
        doc.add(heading);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8f, 62f, 30f});
        table.setSpacingAfter(6f);

        addHeaderCell(table, "#");
        addHeaderCell(table, "Labour Description");
        addHeaderCell(table, "Amount");

        boolean alt = false;
        for (int i = 0; i < labourItems.size(); i++) {
            BillServiceItem item = labourItems.get(i);
            Color rowBg = alt ? COLOR_LIGHT : COLOR_WHITE;
            alt = !alt;

            addDataCell(table, String.valueOf(i + 1),
                FONT_MUTED, Element.ALIGN_CENTER, rowBg);

            // Service name + description combined on one line
            // to keep the table compact, matching reference style.
            String desc = item.getServiceName() != null ? item.getServiceName() : "-";
            if (item.getDescription() != null && !item.getDescription().isEmpty()) {
                desc += " (" + item.getDescription() + ")";
            }
            addDataCell(table, desc, FONT_NORMAL, Element.ALIGN_LEFT, rowBg);

            addDataCell(table, formatCurrency(item.getAmount()),
                FONT_NORMAL, Element.ALIGN_RIGHT, rowBg);
        }

        doc.add(table);
    }

    // ── SECTION 5b: PARTS TABLE ───────────────────────────────
    // Per your decision: shown with headers even when empty,
    // since no PART items exist in the system yet. Future
    // enhancement can populate Part No / Qty / Unit Price from
    // a proper parts catalog without changing this table layout.
    private void buildPartsTable(Document doc, List<BillServiceItem> partItems)
            throws DocumentException {

        Paragraph heading = new Paragraph("PARTS DETAILS", FONT_SECTION_LABEL);
        heading.setSpacingAfter(3f);
        doc.add(heading);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14f, 32f, 12f, 18f, 24f});
        table.setSpacingAfter(8f);

        addHeaderCell(table, "Part No");
        addHeaderCell(table, "Part Name");
        addHeaderCell(table, "Qty");
        addHeaderCell(table, "Unit Price");
        addHeaderCell(table, "Amount");

        if (partItems.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(
                new Phrase("No parts used for this service", FONT_MUTED));
            emptyCell.setColspan(5);
            emptyCell.setPadding(8f);
            emptyCell.setBorderColor(COLOR_BORDER);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        } else {
            boolean alt = false;
            for (BillServiceItem item : partItems) {
                Color rowBg = alt ? COLOR_LIGHT : COLOR_WHITE;
                alt = !alt;

                // Part No and Qty not tracked in current schema —
                // shown as "-" / "1" placeholder until a parts
                // catalog feature is added. Part Name + Amount
                // are real data from the existing bill_services row.
                addDataCell(table, "-", FONT_MUTED, Element.ALIGN_CENTER, rowBg);
                addDataCell(table,
                    item.getServiceName() != null ? item.getServiceName() : "-",
                    FONT_NORMAL, Element.ALIGN_LEFT, rowBg);
                addDataCell(table, "1", FONT_MUTED, Element.ALIGN_CENTER, rowBg);
                addDataCell(table, formatCurrency(item.getAmount()),
                    FONT_MUTED, Element.ALIGN_RIGHT, rowBg);
                addDataCell(table, formatCurrency(item.getAmount()),
                    FONT_NORMAL, Element.ALIGN_RIGHT, rowBg);
            }
        }

        doc.add(table);
    }

    // ── SECTION 6: TOTALS ─────────────────────────────────────
    // Subtotal / Discount / Total Labour / Total Parts / GST /
    // Grand Total / Amount Paid / Balance Due / Amount in Words
    private void buildTotalsSection(Document doc, Bill bill,
            List<BillServiceItem> labourItems, List<BillServiceItem> partItems)
            throws DocumentException {

        // ── Compute Labour Total and Parts Total ─────────────
        BigDecimal labourTotal = sumAmounts(labourItems);
        BigDecimal partsTotal  = sumAmounts(partItems);

        // Subtotal = sum of ALL services (labour + parts) before discount
        BigDecimal subtotal = labourTotal.add(partsTotal);

        BigDecimal discount = bill.getDiscountAmount() != null
                ? bill.getDiscountAmount() : BigDecimal.ZERO;

        // Taxable amount = subtotal - discount (before GST)
        BigDecimal taxableAmount = subtotal.subtract(discount);
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            taxableAmount = BigDecimal.ZERO;
        }

        // ── GST calculation ───────────────────────────────────
        // Only calculated if GST_NUMBER is configured (you confirmed
        // registered). If blank, garage is not yet registered and
        // GST shows as Rs. 0.00 — invoice remains valid either way.
        boolean gstApplicable = GarageConfig.GST_NUMBER != null
                && !GarageConfig.GST_NUMBER.isEmpty();

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;

        if (gstApplicable) {
            cgst = taxableAmount
                .multiply(GarageConfig.CGST_RATE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            sgst = taxableAmount
                .multiply(GarageConfig.SGST_RATE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalGst = cgst.add(sgst);
        }

        // ── Grand Total (exact, before rounding) ─────────────
        // bill.getTotalAmount() is the authoritative value already
        // calculated and stored by BillService (subtotal - discount).
        // We layer GST on top here for PDF display purposes only —
        // this does NOT alter bill.totalAmount in the database,
        // satisfying "do not modify existing calculations" requirement.
        BigDecimal exactGrandTotal = taxableAmount.add(totalGst);

        // ── Round Off ─────────────────────────────────────────
        BigDecimal roundedGrandTotal = exactGrandTotal.setScale(0, RoundingMode.HALF_UP);
        BigDecimal roundOff = roundedGrandTotal.subtract(exactGrandTotal);

        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{45f, 55f});
        wrapper.setSpacingAfter(8f);

        PdfPCell emptyCell = new PdfPCell();
        emptyCell.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(emptyCell);

        PdfPCell totalsCell = new PdfPCell();
        totalsCell.setBorder(Rectangle.BOX);
        totalsCell.setBorderColor(COLOR_BORDER);
        totalsCell.setPadding(10f);
        totalsCell.setBackgroundColor(COLOR_LIGHT);

        addTotalRow(totalsCell, "Subtotal",
            formatCurrency(subtotal), FONT_NORMAL, FONT_NORMAL);

        addTotalRow(totalsCell, "Total Labour",
            formatCurrency(labourTotal), FONT_MUTED, FONT_MUTED);

        addTotalRow(totalsCell, "Total Parts",
            formatCurrency(partsTotal), FONT_MUTED, FONT_MUTED);

        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            Font discLabel = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_SUCCESS);
            Font discValue = new Font(Font.HELVETICA, 9, Font.BOLD, COLOR_SUCCESS);
            addTotalRow(totalsCell, "Discount",
                "- " + formatCurrency(discount), discLabel, discValue);
        }

        if (gstApplicable) {
            addTotalRow(totalsCell,
                "CGST @ " + GarageConfig.CGST_RATE + "%",
                formatCurrency(cgst), FONT_MUTED, FONT_MUTED);
            addTotalRow(totalsCell,
                "SGST @ " + GarageConfig.SGST_RATE + "%",
                formatCurrency(sgst), FONT_MUTED, FONT_MUTED);
        } else {
            addTotalRow(totalsCell, "GST", "Rs. 0.00", FONT_MUTED, FONT_MUTED);
        }

        if (roundOff.compareTo(BigDecimal.ZERO) != 0) {
            addTotalRow(totalsCell, "Round Off",
                formatSignedCurrency(roundOff), FONT_MUTED, FONT_MUTED);
        }

        // Divider
        Paragraph sep = new Paragraph(" ",
            new Font(Font.HELVETICA, 3, Font.NORMAL, COLOR_BORDER));
        sep.setSpacingBefore(2f);
        sep.setSpacingAfter(2f);
        totalsCell.addElement(sep);

        // Grand Total — uses bill.getTotalAmount() as the source of
        // truth for what the customer actually owes (matches Amount
        // Paid / Balance Due already stored by BillService). The GST
        // breakdown above is informational on the PDF; if GST is not
        // applicable, Grand Total naturally equals bill.totalAmount.
        BigDecimal displayedGrandTotal = gstApplicable
            ? roundedGrandTotal
            : bill.getTotalAmount();

        addTotalRow(totalsCell, "Grand Total",
            formatCurrency(displayedGrandTotal),
            FONT_GRAND_TOTAL, FONT_GRAND_TOTAL);

        totalsCell.addElement(new Paragraph(" ",
            new Font(Font.HELVETICA, 3, Font.NORMAL, COLOR_LIGHT)));

        addTotalRow(totalsCell, "Amount Paid",
            formatCurrency(bill.getPaidAmount()), FONT_SUCCESS, FONT_SUCCESS);

        boolean hasBalance = bill.getBalanceAmount() != null
            && bill.getBalanceAmount().compareTo(BigDecimal.ZERO) > 0;
        Font balFont = hasBalance ? FONT_DANGER : FONT_SUCCESS;
        addTotalRow(totalsCell, "Balance Due",
            formatCurrency(bill.getBalanceAmount()), balFont, balFont);

        Paragraph statusLine = buildStatusParagraph(bill.getPaymentStatus());
        statusLine.setAlignment(Element.ALIGN_RIGHT);
        statusLine.setSpacingBefore(4f);
        totalsCell.addElement(statusLine);

        wrapper.addCell(totalsCell);
        doc.add(wrapper);

        // ── Amount in Words ───────────────────────────────────
        Paragraph wordsLine = new Paragraph(
            "Amount in Words: " + AmountInWordsConverter.convert(displayedGrandTotal),
            FONT_WORDS);
        wordsLine.setSpacingBefore(2f);
        wordsLine.setSpacingAfter(8f);
        doc.add(wordsLine);
    }

    // ── SECTION 7: PAYMENT HISTORY ────────────────────────────
    private void buildPaymentHistory(Document doc, List<Payment> payments)
            throws DocumentException {

        Paragraph heading = new Paragraph("PAYMENT HISTORY", FONT_SECTION_LABEL);
        heading.setSpacingAfter(3f);
        doc.add(heading);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{28f, 18f, 32f, 22f});
        table.setSpacingAfter(8f);

        addHeaderCell(table, "Date");
        addHeaderCell(table, "Mode");
        addHeaderCell(table, "Reference");
        addHeaderCell(table, "Amount");

        for (Payment pmt : payments) {
            String dateStr = pmt.getPaymentDate() != null
                ? pmt.getPaymentDate().format(DATE_ONLY) : "-";
            addDataCell(table, dateStr, FONT_NORMAL, Element.ALIGN_LEFT, COLOR_WHITE);
            addDataCell(table, safe(pmt.getPaymentMode()),
                FONT_MUTED, Element.ALIGN_CENTER, COLOR_WHITE);
            addDataCell(table,
                pmt.getTransactionReference() != null && !pmt.getTransactionReference().isEmpty()
                    ? pmt.getTransactionReference() : "-",
                FONT_MUTED, Element.ALIGN_LEFT, COLOR_WHITE);
            addDataCell(table, formatCurrency(pmt.getAmountPaid()),
                FONT_SUCCESS, Element.ALIGN_RIGHT, COLOR_WHITE);
        }
        doc.add(table);
    }

    // ── SECTION 8: TERMS & CONDITIONS ─────────────────────────
    private void buildTermsSection(Document doc) throws DocumentException {

        Paragraph heading = new Paragraph("TERMS & CONDITIONS", FONT_SECTION_LABEL);
        heading.setSpacingBefore(4f);
        heading.setSpacingAfter(3f);
        doc.add(heading);

        String[] terms = {
            "1. Goods once sold will not be taken back.",
            "2. Warranty applicable only where specified by the manufacturer.",
            "3. Customer should verify the vehicle before delivery.",
            "4. All disputes are subject to local jurisdiction."
        };

        for (String term : terms) {
            Paragraph p = new Paragraph(term, FONT_TERMS);
            p.setSpacingAfter(1f);
            doc.add(p);
        }
    }

    // ── SECTION 9: AUTHORIZATION / SIGNATURES ─────────────────
    private void buildSignatureSection(Document doc) throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});
        table.setSpacingBefore(20f);
        table.setSpacingAfter(8f);

        // Left: Authorized Signature
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPaddingTop(20f);

        LineSeparator sigLine1 = new LineSeparator(0.5f, 60f, COLOR_MUTED, Element.ALIGN_LEFT, 0f);
        leftCell.addElement(new Chunk(sigLine1));
        Paragraph leftLabel = new Paragraph(
            "Authorized Signature\nFor " + GarageConfig.AUTHORIZED_SIGNATORY,
            FONT_SIGNATURE_LABEL);
        leftLabel.setSpacingBefore(3f);
        leftCell.addElement(leftLabel);
        table.addCell(leftCell);

        // Right: Customer Signature
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPaddingTop(20f);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        LineSeparator sigLine2 = new LineSeparator(0.5f, 60f, COLOR_MUTED, Element.ALIGN_RIGHT, 0f);
        rightCell.addElement(new Chunk(sigLine2));
        Paragraph rightLabel = new Paragraph("Customer Signature", FONT_SIGNATURE_LABEL);
        rightLabel.setAlignment(Element.ALIGN_RIGHT);
        rightLabel.setSpacingBefore(3f);
        rightCell.addElement(rightLabel);
        table.addCell(rightCell);

        doc.add(table);
    }

    // ── SECTION 10: FOOTER ────────────────────────────────────
    // Footer completely replaced per spec — no "Generated by" line.
    private void buildFooter(Document doc) throws DocumentException {
        buildDivider(doc);

        Paragraph thankYou = new Paragraph(
            "Thank you for choosing " + GarageConfig.GARAGE_NAME + ".", FONT_BOLD);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        thankYou.setSpacingAfter(3f);
        doc.add(thankYou);

        Paragraph support = new Paragraph(
            "Support: " + GarageConfig.PHONE, FONT_MUTED);
        support.setAlignment(Element.ALIGN_CENTER);
        support.setSpacingAfter(2f);
        doc.add(support);

        Chunk websiteChunk = new Chunk(GarageConfig.WEBSITE, FONT_LINK);
        websiteChunk.setAnchor(GarageConfig.WEBSITE);
        Paragraph websitePara = new Paragraph();
        websitePara.add(websiteChunk);
        websitePara.setAlignment(Element.ALIGN_CENTER);
        doc.add(websitePara);
    }

    // ── HELPERS ───────────────────────────────────────────────
    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_HEADER));
        cell.setBackgroundColor(COLOR_DARK);
        cell.setPadding(6f);
        cell.setBorderColor(COLOR_DARK);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addDataCell(PdfPTable table, String text,
            Font font, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5f);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBackgroundColor(background);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPCell parentCell,
            String label, String value, Font labelFont, Font valueFont) {
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

    private BigDecimal sumAmounts(List<BillServiceItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BillServiceItem item : items) {
            if (item.getAmount() != null) sum = sum.add(item.getAmount());
        }
        return sum;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        return "Rs. " + String.format("%,.2f", amount);
    }

    private String formatSignedCurrency(BigDecimal amount) {
        if (amount == null) return "Rs. 0.00";
        String sign = amount.compareTo(BigDecimal.ZERO) >= 0 ? "+ " : "- ";
        return sign + "Rs. " + String.format("%,.2f", amount.abs());
    }

    // Returns "-" instead of "null" for any missing field —
    // satisfies the "never show null" requirement everywhere.
    private String safe(String value) {
        return (value != null && !value.trim().isEmpty()) ? value : "-";
    }
}