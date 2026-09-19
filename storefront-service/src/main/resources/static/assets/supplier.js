(() => {
    'use strict';
    const inventoryPage = document.body.dataset.supplierPage === 'inventory';
    const message = document.getElementById('supplier-message');
    const filter = document.getElementById('inventory-filter');
    const status = document.getElementById('fulfilment-status');
    let inventory = [];
    let busy = false;
    const unavailable = 'Supplier service is temporarily unavailable.';
    function notify(text, error = false) {
        message.textContent = text;
        message.className = 'message ' + (error ? 'error' : 'success');
    }
    function cell(text) {
        const node = document.createElement('td'); node.textContent = text; return node;
    }
    async function request(path, method = 'GET', body, json = true) {
        const headers = {'Accept': 'application/json'};
        if (method !== 'GET') {
            headers[document.querySelector('meta[name="_csrf_header"]').content] = document.querySelector('meta[name="_csrf"]').content;
        }
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const response = await fetch('/api/supplier/' + path, {method, credentials: 'same-origin', headers,
            body: body === undefined ? undefined : JSON.stringify(body)});
        if (response.status === 401) window.location.assign('/login');
        if (!response.ok) {
            const error = new Error(response.status === 403 ? 'Access or security token denied. Reload and try again.'
                : response.status === 400 ? 'Enter a valid nonnegative whole quantity or status.'
                : response.status === 404 ? 'Supplier item or order not found. Refresh the page.' : unavailable);
            error.safe = true; throw error;
        }
        return json ? response.json() : null;
    }
    async function act(action) {
        if (busy) return;
        busy = true;
        document.querySelectorAll('main button, main input, main select').forEach(node => { node.disabled = true; });
        notify('Working…');
        try { await action(); }
        catch (error) { notify(error.safe ? error.message : unavailable, true); }
        finally {
            busy = false;
            document.querySelectorAll('main button, main input, main select').forEach(node => { node.disabled = false; });
        }
    }
    function renderInventory() {
        const rows = document.getElementById('inventory-rows'); rows.replaceChildren();
        const query = filter.value.trim().toUpperCase();
        for (const item of inventory.filter(item => item.itemId.toUpperCase().includes(query))) {
            const row = document.createElement('tr'); row.dataset.itemId = item.itemId;
            row.append(cell(item.itemId), cell(item.quantity), cell(new Date(item.updatedAt).toLocaleString()));
            const inputCell = cell(''); const input = document.createElement('input');
            input.type = 'number'; input.min = '0'; input.max = '2147483647'; input.step = '1'; input.required = true;
            input.value = item.quantity; input.setAttribute('aria-label', 'New quantity for ' + item.itemId); inputCell.append(input);
            const action = cell(''); const button = document.createElement('button'); button.type = 'button'; button.textContent = 'Set quantity';
            button.addEventListener('click', () => {
                if (!input.reportValidity()) return;
                act(async () => {
                    const updated = await request('inventory/' + encodeURIComponent(item.itemId), 'PUT', {quantity: Number(input.value)});
                    inventory = inventory.map(current => current.itemId === updated.itemId ? updated : current);
                    renderInventory();
                    notify(updated.itemId + ' current quantity: ' + updated.quantity + '. Pending fulfilments were retried for positive stock.');
                });
            });
            action.append(button); row.append(inputCell, action); rows.append(row);
        }
        if (!rows.children.length) empty(rows, 5, 'No inventory items match.');
    }
    function empty(rows, columns, text) {
        const row = document.createElement('tr'); const textCell = cell(text); textCell.colSpan = columns;
        row.append(textCell); rows.append(row);
    }
    async function loadOrders() {
        const orders = await request('orders' + (status.value ? '?status=' + encodeURIComponent(status.value) : ''));
        const rows = document.getElementById('fulfilment-rows'); rows.replaceChildren();
        for (const order of orders) {
            const row = document.createElement('tr');
            row.append(cell(order.orderId), cell(order.status), cell(new Date(order.createdAt).toLocaleString()),
                cell(new Date(order.updatedAt).toLocaleString()), cell(order.requestedLineCount),
                cell(order.shippedLineCount + ' / ' + order.requestedLineCount + ' lines fully shipped'));
            rows.append(row);
            const detailRow = document.createElement('tr'); const detailCell = cell(''); detailCell.colSpan = 6;
            const details = document.createElement('details'); details.open = order.status === 'PENDING';
            const summary = document.createElement('summary'); summary.textContent = 'Line progress and shipment history'; details.append(summary);
            for (const line of order.lines) {
                const text = document.createElement('p'); text.className = 'supplier-line-details';
                text.textContent = line.itemId + ' — requested: ' + line.quantityRequested + ', shipped: ' + line.quantityShipped + ', remaining: ' + line.remainingQuantity;
                details.append(text);
            }
            for (const shipment of order.shipments) {
                const text = document.createElement('p'); text.className = 'supplier-line-details';
                text.textContent = 'Shipment ' + shipment.eventId + ' · ' + new Date(shipment.createdAt).toLocaleString()
                    + ' · ' + (shipment.complete ? 'Complete' : 'Partial') + ': '
                    + shipment.shippedLines.map(line => line.itemId + ' × ' + line.quantity).join(', ');
                details.append(text);
            }
            detailCell.append(details); detailRow.append(detailCell); rows.append(detailRow);
        }
        if (!orders.length) empty(rows, 6, 'No fulfilments match this status.');
    }
    async function refresh() {
        if (inventoryPage) { inventory = await request('inventory'); renderInventory(); }
        else await loadOrders();
    }
    document.getElementById('refresh-supplier').addEventListener('click', () => act(async () => { await refresh(); notify('Refreshed.'); }));
    if (inventoryPage) filter.addEventListener('input', renderInventory);
    else {
        status.addEventListener('change', () => act(async () => { await loadOrders(); notify('Refreshed.'); }));
        document.getElementById('retry-pending').addEventListener('click', () => act(async () => {
            await request('inventory/retry-pending', 'POST', undefined, false);
            await loadOrders(); notify('Pending fulfilments retried. Showing current fulfilment state.');
        }));
    }
    act(async () => { await refresh(); notify('Loaded.'); });
})();
