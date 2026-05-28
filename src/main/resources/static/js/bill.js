// bill.js — GarageBilling dynamic form engine
// Tracks how many rows have been created (never decrements,
// so each row always gets a unique index even after removals)
var rowCount = 1;

// ── addServiceRow ────────────────────────────────────────────
// Called by the "+ Add Service" button (type="button", NOT submit)
function addServiceRow() {
    var container = document.getElementById('serviceRowsContainer');
    if (!container) {
        console.error('serviceRowsContainer not found');
        return;
    }

    var idx = rowCount; // capture current value for this row's names
    rowCount++;

    var div = document.createElement('div');
    div.className = 'service-row';
    div.id = 'serviceRow_' + idx;

    // Build the inner HTML using string concatenation (no template literals
    // to avoid any browser compatibility issues)
    div.innerHTML =
        '<div class="row g-2 align-items-center">' +
            '<div class="col-md-4">' +
                '<input type="text"' +
                '       name="services[' + idx + '].serviceName"' +
                '       class="form-control form-control-sm"' +
                '       placeholder="e.g. Tyre Rotation"' +
                '       required />' +
            '</div>' +
            '<div class="col-md-4">' +
                '<input type="text"' +
                '       name="services[' + idx + '].description"' +
                '       class="form-control form-control-sm"' +
                '       placeholder="Description (optional)" />' +
            '</div>' +
            '<div class="col-md-3">' +
                '<div class="input-group input-group-sm">' +
                    '<span class="input-group-text">&#8377;</span>' +
                    '<input type="number"' +
                    '       name="services[' + idx + '].amount"' +
                    '       class="form-control form-control-sm amount-input"' +
                    '       placeholder="0.00"' +
                    '       min="0"' +
                    '       step="0.01"' +
                    '       oninput="calculateTotal()" />' +
                '</div>' +
            '</div>' +
            '<div class="col-md-1 text-center">' +
                '<button type="button"' +
                '        class="btn btn-outline-danger btn-sm"' +
                '        onclick="removeRow(\'serviceRow_' + idx + '\')"' +
                '        title="Remove service">' +
                    '<i class="bi bi-trash"></i>' +
                '</button>' +
            '</div>' +
        '</div>';

    container.appendChild(div);
    calculateTotal();

    // Focus the service name field in the new row
    var nameInput = div.querySelector('input[type="text"]');
    if (nameInput) nameInput.focus();
}

// ── removeRow ────────────────────────────────────────────────
function removeRow(rowId) {
    var container = document.getElementById('serviceRowsContainer');
    if (!container) return;

    var rows = container.querySelectorAll('.service-row');
    if (rows.length <= 1) {
        // Flash red border to signal "can't remove last row"
        var row = document.getElementById(rowId);
        if (row) {
            row.style.borderColor = '#dc3545';
            row.style.borderWidth = '1.5px';
            setTimeout(function() {
                row.style.borderColor = '';
                row.style.borderWidth = '';
            }, 800);
        }
        return;
    }

    var rowEl = document.getElementById(rowId);
    if (rowEl) {
        rowEl.remove();
        calculateTotal();
    }
}

// ── calculateTotal ───────────────────────────────────────────
// Reads every .amount-input field, sums them, updates display
function calculateTotal() {
    var inputs = document.querySelectorAll('.amount-input');
    var total = 0;

    for (var i = 0; i < inputs.length; i++) {
        var val = parseFloat(inputs[i].value);
        if (!isNaN(val) && val > 0) {
            total += val;
        }
    }

    // Round to 2 decimal places to avoid float artifacts
    total = Math.round(total * 100) / 100;

    // Update total display
    var display = document.getElementById('totalAmountDisplay');
    if (display) {
        display.textContent = '\u20B9' + formatNum(total.toFixed(2));
    }

    // Update hidden input (submitted with form)
    var hidden = document.getElementById('totalAmountHidden');
    if (hidden) hidden.value = total.toFixed(2);

    updateBalance(total);
}

// ── updateBalance ────────────────────────────────────────────
function updateBalance(total) {
    var paidEl = document.getElementById('initialPayment');
    var paid   = paidEl ? (parseFloat(paidEl.value) || 0) : 0;
    var bal    = Math.max(0, total - paid);
    bal        = Math.round(bal * 100) / 100;

    var balDisplay = document.getElementById('balanceDisplay');
    if (balDisplay) {
        balDisplay.textContent = '\u20B9' + formatNum(bal.toFixed(2));
        // Red when balance > 0, green when fully paid
        balDisplay.style.color = bal > 0 ? '#dc3545' : '#198754';
    }

    // Update status preview badge
    var badge = document.getElementById('statusPreview');
    if (badge) {
        if (paid <= 0 || total === 0) {
            badge.textContent  = 'PENDING';
            badge.className    = 'badge badge-pending';
        } else if (paid >= total) {
            badge.textContent  = 'PAID';
            badge.className    = 'badge badge-paid';
        } else {
            badge.textContent  = 'PARTIAL';
            badge.className    = 'badge badge-partial';
        }
    }
}

// ── formatNum ────────────────────────────────────────────────
// "15000.50" → "15,000.50"
function formatNum(numStr) {
    var parts  = numStr.split('.');
    parts[0]   = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    return parts.join('.');
}

// ── loadCarsForCustomer ──────────────────────────────────────
// Called when customer dropdown changes — fetches their cars via AJAX
function loadCarsForCustomer(customerId) {
    var carSelect = document.getElementById('existingCarId');
    if (!carSelect) return;

    if (!customerId || customerId === '' || customerId === '0') {
        carSelect.innerHTML =
            '<option value="">-- Select or add new --</option>' +
            '<option value="0">+ Register new car</option>';
        return;
    }

    carSelect.innerHTML = '<option value="">Loading cars...</option>';
    carSelect.disabled  = true;

    fetch('/bill/cars?customerId=' + customerId)
        .then(function(response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        })
        .then(function(cars) {
            carSelect.disabled = false;

            var html = '<option value="">-- Select Car --</option>';

            if (cars.length === 0) {
                html += '<option value="" disabled>' +
                        'No cars found for this customer</option>';
            } else {
                for (var i = 0; i < cars.length; i++) {
                    html += '<option value="' + cars[i].id + '">' +
                            cars[i].carNumber + ' \u2014 ' +
                            cars[i].carModel  +
                            '</option>';
                }
            }

            html += '<option value="0">+ Register new car</option>';
            carSelect.innerHTML = html;

            // If a car was pre-selected via URL param, select it now
            if (preselectedCarId) {
                carSelect.value = String(preselectedCarId);
                handleCarChange(String(preselectedCarId));
            }
        })
        .catch(function(err) {
            carSelect.disabled  = false;
            carSelect.innerHTML =
                '<option value="">Error — please retry</option>' +
                '<option value="0">+ Register new car</option>';
            console.error('loadCarsForCustomer failed:', err);
        });
}

// ── Page init ────────────────────────────────────────────────
// DOMContentLoaded runs after HTML parsed, before images load
document.addEventListener('DOMContentLoaded', function() {
    calculateTotal();

    // Wire payment input to recalculate balance on every keystroke
    var payInput = document.getElementById('initialPayment');
    if (payInput) {
        payInput.addEventListener('input', calculateTotal);
    }
});