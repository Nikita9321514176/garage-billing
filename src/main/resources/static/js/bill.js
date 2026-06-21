// =====================================================
// Garage Billing System - bill.js
// =====================================================

var rowCount = 1;

// =====================================================
// ADD SERVICE ROW
// =====================================================
function addServiceRow() {

    var container = document.getElementById('serviceRowsContainer');

    if (!container) {
        console.error('serviceRowsContainer not found');
        return;
    }

    var index = rowCount++;

    var row = document.createElement('div');

    row.className = 'service-row mt-2';
    row.id = 'serviceRow_' + index;

    row.innerHTML =
        '<div class="row g-2 align-items-center">' +

            '<div class="col-md-4">' +
                '<input type="text"' +
                ' name="services[' + index + '].serviceName"' +
                ' class="form-control form-control-sm"' +
                ' placeholder="Service Name"' +
                ' required>' +
            '</div>' +

            '<div class="col-md-4">' +
                '<input type="text"' +
                ' name="services[' + index + '].description"' +
                ' class="form-control form-control-sm"' +
                ' placeholder="Description">' +
            '</div>' +

            '<div class="col-md-3">' +
                '<div class="input-group input-group-sm">' +
                    '<span class="input-group-text">₹</span>' +

                    '<input type="number"' +
                    ' name="services[' + index + '].amount"' +
                    ' class="form-control amount-input"' +
                    ' min="0"' +
                    ' step="0.01"' +
                    ' value="0"' +
                    ' oninput="calculateTotal()">' +
                '</div>' +
            '</div>' +

            '<div class="col-md-1 text-center">' +
                '<button type="button"' +
                ' class="btn btn-outline-danger btn-sm"' +
                ' onclick="removeRow(\'serviceRow_' + index + '\')">' +
                '<i class="bi bi-trash"></i>' +
                '</button>' +
            '</div>' +

        '</div>';

    container.appendChild(row);

    calculateTotal();
}

// =====================================================
// REMOVE SERVICE ROW
// =====================================================
function removeRow(rowId) {

    var container = document.getElementById('serviceRowsContainer');

    if (!container) return;

    var rows = container.querySelectorAll('.service-row');

    if (rows.length <= 1) {
        alert('At least one service is required.');
        return;
    }

    var row = document.getElementById(rowId);

    if (row) {
        row.remove();
    }

    calculateTotal();
}

// =====================================================
// GST (18% = 9% CGST + 9% SGST)
// =====================================================
var CGST_RATE = 9;
var SGST_RATE = 9;

function calculateGst(subtotal, discount) {
    if (discount > subtotal) {
        discount = subtotal;
    }

    var taxable = round2(subtotal - discount);
    var cgst = round2(taxable * CGST_RATE / 100);
    var sgst = round2(taxable * SGST_RATE / 100);
    var grandTotal = round2(taxable + cgst + sgst);

    return {
        subtotal: subtotal,
        discount: discount,
        taxable: taxable,
        cgst: cgst,
        sgst: sgst,
        grandTotal: grandTotal
    };
}

// =====================================================
// CALCULATE BILL
// =====================================================
function calculateTotal() {

    var subtotal = 0;

    document.querySelectorAll('.amount-input').forEach(function(input) {

        var value = parseFloat(input.value);

        if (!isNaN(value)) {
            subtotal += value;
        }
    });

    subtotal = round2(subtotal);

    // -------------------------
    // Discount
    // -------------------------

    var discountField =
        document.getElementById('discountAmount');

    var discount =
        discountField
        ? parseFloat(discountField.value) || 0
        : 0;

    // -------------------------
    // GST + Grand Total
    // -------------------------

    var gst = calculateGst(subtotal, discount);
    var grandTotal = gst.grandTotal;

    // -------------------------
    // Payment Received
    // -------------------------

    var paymentInput =
        document.getElementById('initialPayment');

    var payment =
        paymentInput
        ? parseFloat(paymentInput.value) || 0
        : 0;

    if (payment > grandTotal) {
        payment = grandTotal;
        if (paymentInput) {
            paymentInput.value = grandTotal.toFixed(2);
        }
    }

    // -------------------------
    // Balance
    // -------------------------

    var balance =
        round2(grandTotal - payment);

    if (balance < 0) {
        balance = 0;
    }

    // -------------------------
    // Update UI
    // -------------------------

    setText(
        'subtotalDisplay',
        '₹' + formatCurrency(subtotal)
    );

    setText(
        'discountDisplay',
        '- ₹' + formatCurrency(gst.discount)
    );

    setText(
        'taxableDisplay',
        '₹' + formatCurrency(gst.taxable)
    );

    setText(
        'cgstDisplay',
        '₹' + formatCurrency(gst.cgst)
    );

    setText(
        'sgstDisplay',
        '₹' + formatCurrency(gst.sgst)
    );

    setText(
        'totalAmountDisplay',
        '₹' + formatCurrency(grandTotal)
    );

    setText(
        'balanceDisplay',
        '₹' + formatCurrency(balance)
    );

    var hidden =
        document.getElementById('totalAmountHidden');

    if (hidden) {
        hidden.value = grandTotal.toFixed(2);
    }

    // -------------------------
    // Show / Hide Discount Row
    // -------------------------

    var discountRow =
        document.getElementById('discountRow');

    if (discountRow) {

        if (gst.discount > 0) {
            discountRow.style.display = 'flex';
        } else {
            discountRow.style.display = 'none';
        }
    }

    updatePaymentStatus(
        grandTotal,
        payment,
        balance
    );
}

// =====================================================
// PAYMENT STATUS
// =====================================================
function updatePaymentStatus(
    grandTotal,
    payment,
    balance
) {

    var badge =
        document.getElementById('statusPreview');

    if (!badge) return;

    if (grandTotal === 0) {

        badge.innerText = 'PENDING';
        badge.className = 'badge bg-secondary';

        return;
    }

    if (balance === 0) {

        badge.innerText = 'PAID';
        badge.className = 'badge bg-success';

        return;
    }

    if (payment > 0) {

        badge.innerText = 'PARTIAL';
        badge.className = 'badge bg-warning text-dark';

        return;
    }

    badge.innerText = 'PENDING';
    badge.className = 'badge bg-danger';
}

// =====================================================
// CUSTOMER -> LOAD CARS
// =====================================================
function loadCarsForCustomer(customerId) {

    var carSelect =
        document.getElementById('existingCarId');

    if (!carSelect) return;

    if (!customerId) {

        carSelect.innerHTML =
            '<option value="">-- Select Car --</option>' +
            '<option value="0">+ Register new car</option>';

        return;
    }

    fetch('/bill/cars?customerId=' + customerId)

        .then(response => response.json())

        .then(cars => {

            var html =
                '<option value="">-- Select Car --</option>';

            cars.forEach(function(car) {

                html +=
                    '<option value="' + car.id + '">' +
                    car.carNumber +
                    ' - ' +
                    car.carModel +
                    '</option>';
            });

            html +=
                '<option value="0">+ Register new car</option>';

            carSelect.innerHTML = html;
        })

        .catch(error => {

            console.error(
                'Failed loading cars:',
                error
            );
        });
}

// =====================================================
// HELPERS
// =====================================================
function round2(value) {
    return Math.round(value * 100) / 100;
}

function formatCurrency(value) {

    return Number(value).toLocaleString(
        'en-IN',
        {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }
    );
}

function setText(id, value) {

    var el = document.getElementById(id);

    if (el) {
        el.textContent = value;
    }
}

// =====================================================
// PAGE LOAD
// =====================================================
document.addEventListener(
    'DOMContentLoaded',
    function () {

        calculateTotal();

        var paymentInput =
            document.getElementById('initialPayment');

        if (paymentInput) {

            paymentInput.addEventListener(
                'input',
                calculateTotal
            );
        }

        var discountInput =
            document.getElementById('discountAmount');

        if (discountInput) {

            discountInput.addEventListener(
                'input',
                calculateTotal
            );
        }
    }
);