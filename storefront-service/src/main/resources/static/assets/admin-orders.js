(() => {
    'use strict';
    const rows = document.getElementById('admin-order-rows');
    const filter = document.getElementById('order-status');
    const form = document.getElementById('admin-filter-form');
    const message = document.getElementById('admin-message');
    let busy = false;

    function notify(text, error = false) {
        message.textContent = text;
        message.className = 'message ' + (error ? 'error' : 'success');
    }
    function cell(text) {
        const node = document.createElement('td');
        node.textContent = text;
        return node;
    }
    function lock(value) {
        busy = value;
        document.querySelectorAll('main button, main select').forEach(control => { control.disabled = value; });
    }
    async function request(path, method = 'GET') {
        const headers = {'Accept': 'application/json'};
        if (method !== 'GET') {
            headers[document.querySelector('meta[name="_csrf_header"]').content] =
                document.querySelector('meta[name="_csrf"]').content;
        }
        const response = await fetch(path, {method, credentials: 'same-origin', headers});
        if (response.status === 401) window.location.assign('/login');
        if (!response.ok) {
            const error = new Error(response.status === 409 ? 'This order was already processed. The list has been refreshed.'
                : response.status === 403 ? 'Access or security token denied. Reload the page and try again.'
                : response.status === 404 ? 'Order not found. Refresh the list.'
                : 'Order management is temporarily unavailable. Refresh before retrying a decision.');
            error.status = response.status;
            throw error;
        }
        return response.json();
    }
    async function load() {
        const query = filter.value ? '?status=' + encodeURIComponent(filter.value) : '';
        const orders = await request('/api/admin/orders' + query);
        rows.replaceChildren();
        for (const order of orders) {
            const row = document.createElement('tr');
            row.append(cell(order.orderId), cell(order.username), cell(new Date(order.createdAt).toLocaleString()),
                cell(order.locale), cell(new Intl.NumberFormat('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(order.totalPrice)),
                cell(order.status));
            const actions = cell('');
            if (order.status === 'PENDING') {
                for (const action of ['approve', 'deny']) {
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.textContent = action === 'approve' ? 'Approve' : 'Deny';
                    button.addEventListener('click', () => decide(order.orderId, action));
                    actions.append(button);
                }
            }
            row.append(actions);
            rows.append(row);
        }
        if (!orders.length) {
            const row = document.createElement('tr');
            const empty = cell('No orders match this status.');
            empty.colSpan = 7;
            row.append(empty); rows.append(row);
        }
    }
    async function refresh() {
        if (busy) return;
        lock(true);
        notify('Loading orders…');
        try { await load(); notify('Orders refreshed.'); }
        catch (error) { notify(error.status ? error.message : 'Order management is temporarily unavailable. Please retry Refresh.', true); }
        finally { lock(false); }
    }
    async function decide(id, action) {
        if (busy) return;
        lock(true);
        try {
            await request('/api/admin/orders/' + encodeURIComponent(id) + '/' + action, 'POST');
            await load();
            notify(action === 'approve' ? 'Approval accepted.' : 'Order denied.');
        } catch (error) {
            if (error.status === 409) {
                try { await load(); } catch { notify('Order was already processed, but refreshing failed. Please retry Refresh.', true); return; }
            }
            // Never show downstream bodies, and never assume a failed decision left the order unchanged.
            notify(error.status ? error.message : 'Order management is temporarily unavailable. Refresh before retrying a decision.', true);
        } finally { lock(false); }
    }
    form.addEventListener('submit', event => { event.preventDefault(); refresh(); });
    filter.addEventListener('change', refresh);
    refresh();
})();
