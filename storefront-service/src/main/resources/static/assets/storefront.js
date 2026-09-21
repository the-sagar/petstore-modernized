(() => {
    'use strict';

    const current = new URL(window.location.href);
    const locale = window.petstoreLocale;
    const t = window.petstoreText;

    function url(path) {
        const result = new URL(path, window.location.origin);
        result.searchParams.set('locale', locale);
        return result.pathname + result.search;
    }

    document.querySelectorAll('[data-locale-link]').forEach(link => { link.href = url(link.getAttribute('href')); });
    const selector = document.getElementById('locale-select');
    if (selector) {
        selector.value = locale;
        selector.addEventListener('change', () => {
            const address = new URL(window.location.href);
            address.searchParams.set('locale', selector.value);
            address.searchParams.delete('page');
            window.location.assign(address.pathname + address.search);
        });
    }

    document.querySelectorAll('input, select').forEach(input => {
        input.addEventListener('invalid', () => input.setCustomValidity(t('js.fieldInvalid')));
        input.addEventListener('input', () => input.setCustomValidity(''));
        input.addEventListener('change', () => input.setCustomValidity(''));
    });

    const page = document.body.dataset.page;
    if (!page) return; // Existing account/authentication scripts keep their behavior.
    const message = document.getElementById('message');
    const contactFields = ['firstName', 'lastName', 'email', 'phone', 'street1', 'street2',
        'city', 'stateOrProvince', 'postalCode', 'country'];

    function notify(text = '', type = '') {
        message.textContent = text;
        message.className = 'message ' + type;
    }

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined && text !== null) node.textContent = text;
        if (className) node.className = className;
        return node;
    }

    function link(text, path) {
        const node = element('a', text);
        node.href = url(path);
        return node;
    }

    function price(value) {
        if (value == null) return '—';
        // Display server-provided amounts only; totals and prices are never calculated here.
        return new Intl.NumberFormat(locale, {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(value);
    }

    async function api(path, method = 'GET', body) {
        const headers = {'Accept': 'application/json'};
        if (method !== 'GET') {
            headers['Content-Type'] = 'application/json';
            headers[document.querySelector('meta[name="_csrf_header"]').content] =
                document.querySelector('meta[name="_csrf"]').content;
        }
        const response = await fetch(url(path), {
            method, credentials: 'same-origin', headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (response.status === 401) {
            window.location.assign(url('/login'));
            throw new Error(t('js.signin'));
        }
        if (!response.ok) {
            const messages = {
                400: t('js.invalid'),
                403: t('js.session'),
                404: t('js.notFound'),
                409: t('js.checkoutMissing'),
                502: t('js.unavailable')
            };
            // Never display remote exception bodies.
            throw new Error(messages[response.status] || t('js.failed'));
        }
        if (path === '/api/checkout' && response.status !== 201) {
            throw new Error(t('js.unconfirmed'));
        }
        return response.json();
    }

    function showError(error) {
        notify(error instanceof TypeError ? t('js.connection')
            : error instanceof SyntaxError ? t('js.response')
            : error.message, 'error');
    }

    function catalogImage(_filename, alt, categoryId) {
        // Legacy artwork is not redistributed. Keep filenames as catalog metadata,
        // but never turn them into URLs; this presentation makes no image requests.
        const symbols = {FISH: '🐟', DOGS: '🐕', CATS: '🐈', BIRDS: '🐦', REPTILES: '🦎'};
        const area = element('div', undefined, 'catalog-image');
        area.setAttribute('role', 'img');
        area.setAttribute('aria-label', alt);
        const symbol = element('span', Object.hasOwn(symbols, categoryId) ? symbols[categoryId] : '◇',
            'catalog-symbol');
        symbol.setAttribute('aria-hidden', 'true');
        area.append(symbol);
        return area;
    }

    function productCards(container, products) {
        container.replaceChildren();
        if (!products.length) {
            container.append(element('p', t('js.noProducts')));
        }
        for (const product of products) {
            const card = element('article', undefined, 'card');
            const heading = element('h3');
            heading.append(link(product.name || product.id, '/shop/products/' + encodeURIComponent(product.id)));
            card.append(catalogImage(product.image, product.name || product.id, product.categoryId),
                heading, element('p', product.description || ''), element('p', product.id, 'hint'));
            container.append(card);
        }
    }

    function pagingState(result, load) {
        const pager = document.getElementById('catalog-pagination');
        pager.hidden = false;
        const previous = document.getElementById('catalog-previous');
        const next = document.getElementById('catalog-next');
        previous.disabled = !result.hasPrevious;
        next.disabled = !result.hasNext;
        document.getElementById('catalog-page').textContent = result.totalPages === 0 ? t('js.noResults')
            : result.page >= result.totalPages ? t('js.outOfRange', result.totalPages)
            : t('js.page', result.page + 1, result.totalPages);
        previous.onclick = () => load(result.page - 1);
        next.onclick = () => load(result.page + 1);
    }

    function pagingBusy() {
        document.getElementById('catalog-previous').disabled = true;
        document.getElementById('catalog-next').disabled = true;
    }

    function retainPage(pageNumber, query) {
        const address = new URL(window.location.href);
        address.searchParams.set('locale', locale);
        address.searchParams.set('page', pageNumber);
        if (query !== undefined) {
            if (query) address.searchParams.set('q', query); else address.searchParams.delete('q');
        }
        history.replaceState(null, '', address.pathname + address.search);
    }

    async function shop() {
        let searchVersion = 0;
        const form = document.getElementById('search-form');
        const input = document.getElementById('search-query');
        const categorySection = document.getElementById('category-section');
        const searchSection = document.getElementById('search-section');
        async function search(pageNumber, query) {
            const version = ++searchVersion;
            categorySection.hidden = query.length > 0;
            searchSection.hidden = !query;
            document.getElementById('search-results').replaceChildren();
            pagingBusy();
            document.getElementById('catalog-pagination').hidden = true;
            retainPage(pageNumber, query);
            if (!query) { notify(); return; }
            notify(t('js.searching'));
            try {
                const result = await api('/api/catalog/search?q=' + encodeURIComponent(query) + '&page=' + encodeURIComponent(pageNumber));
                if (version !== searchVersion) return;
                productCards(document.getElementById('search-results'), result.content);
                pagingState(result, nextPage => search(nextPage, query));
                notify(t('js.matching', result.totalElements));
            } catch (error) { if (version === searchVersion) showError(error); }
        }
        form.addEventListener('submit', event => { event.preventDefault(); search(0, input.value.trim()); });
        const categories = await api('/api/catalog/categories');
        const container = document.getElementById('categories');
        for (const category of categories) {
            const card = element('article', undefined, 'card');
            const heading = element('h3');
            heading.append(link(category.name || category.id, '/shop/categories/' + encodeURIComponent(category.id)));
            card.append(catalogImage(category.image, category.name || category.id, category.id),
                heading, element('p', category.description || t('js.browse')));
            container.append(card);
        }
        notify();
        input.value = current.searchParams.get('q') || '';
        if (input.value.trim()) await search(current.searchParams.get('page') || 0, input.value.trim());
    }

    async function loadCatalogPages(path, render, label) {
        async function load(pageNumber) {
            pagingBusy();
            try {
                const result = await api(path + '?page=' + encodeURIComponent(pageNumber));
                render(result.content);
                retainPage(result.page);
                pagingState(result, load);
                notify(t(label, result.totalElements));
            } catch (error) { showError(error); }
        }
        await load(current.searchParams.get('page') || 0);
    }

    function routeId() {
        return encodeURIComponent(decodeURIComponent(window.location.pathname.split('/').filter(Boolean).pop()));
    }

    async function category() {
        const id = routeId();
        const details = await api('/api/catalog/categories/' + id);
        document.getElementById('category-name').textContent = details.name || details.id;
        document.getElementById('category-description').textContent = details.description || '';
        await loadCatalogPages('/api/catalog/categories/' + id + '/products',
            products => productCards(document.getElementById('products'), products), 'js.products');
    }

    function addToCartButton(item) {
        const add = element('button', t('js.add'));
        add.type = 'button';
        add.setAttribute('aria-label', t('js.addLabel', item.id));
        add.addEventListener('click', async () => {
            add.disabled = true;
            try {
                const cart = await api('/api/cart/items', 'POST', {itemId: item.id});
                notify(t('js.added', item.id, cart.lineCount), 'success');
            } catch (error) { showError(error); } finally { add.disabled = false; }
        });
        return add;
    }

    async function product() {
        const id = routeId();
        const details = await api('/api/catalog/products/' + id);
        const category = await api('/api/catalog/categories/' + encodeURIComponent(details.categoryId));
        const categoryLink = document.getElementById('category-link');
        categoryLink.textContent = category.name || category.id;
        categoryLink.href = url('/shop/categories/' + encodeURIComponent(category.id));
        document.getElementById('product-name').textContent = details.name || details.id;
        document.getElementById('product-description').textContent = details.description || '';
        const container = document.getElementById('items');
        await loadCatalogPages('/api/catalog/products/' + id + '/items', items => {
            container.replaceChildren();
            if (!items.length) container.append(element('p', t('js.noItems')));
            for (const item of items) {
                const card = element('article', undefined, 'card');
                const add = addToCartButton(item);
                const heading = element('h3');
                heading.append(link(item.id, '/shop/items/' + encodeURIComponent(item.id)));
                card.append(catalogImage(item.image, item.description || item.id, item.categoryId || details.categoryId),
                    heading, element('p', item.description || ''),
                    element('p', (item.attributes || []).join(' · '), 'hint'), element('p', price(item.listPrice), 'item-price'), add);
                container.append(card);
            }
        }, 'js.items');
    }

    async function item() {
        const details = await api('/api/catalog/items/' + routeId());
        const product = await api('/api/catalog/products/' + encodeURIComponent(details.productId));
        const category = await api('/api/catalog/categories/' + encodeURIComponent(product.categoryId));
        const categoryLink = document.getElementById('category-link');
        categoryLink.textContent = category.name || category.id;
        categoryLink.href = url('/shop/categories/' + encodeURIComponent(category.id));
        const productLink = document.getElementById('product-link');
        productLink.textContent = product.name || product.id;
        productLink.href = url('/shop/products/' + encodeURIComponent(product.id));
        document.getElementById('item-context').hidden = false;
        document.getElementById('item-product-name').textContent = product.name || product.id;
        document.getElementById('item-id').textContent = details.id;
        document.getElementById('item-description').textContent = details.description || '';
        document.getElementById('item-attributes').textContent = (details.attributes || []).join(' · ');
        document.getElementById('item-price').textContent = price(details.listPrice);
        document.getElementById('item-image').replaceChildren(catalogImage(details.image,
            details.description || details.id, details.categoryId || product.categoryId));
        document.getElementById('item-actions').append(addToCartButton(details));
        document.getElementById('item-content').hidden = false;
        notify();
    }

    function lineDescription(line) {
        return [line.description, ...(line.attributes || [])].filter(Boolean).join(' · ');
    }

    async function cart() {
        const content = document.getElementById('cart-content');
        let busy = false;
        async function mutate(path, method, payload) {
            if (busy) return;
            busy = true;
            content.querySelectorAll('button, input').forEach(node => { node.disabled = true; });
            try {
                render(await api(path, method, payload));
                notify(t('js.cartUpdated'), 'success');
            } catch (error) { showError(error); } finally {
                busy = false;
                content.querySelectorAll('button, input').forEach(node => { node.disabled = false; });
            }
        }
        function render(cart) {
            content.hidden = cart.lineCount === 0;
            document.getElementById('empty-cart').hidden = cart.lineCount !== 0;
            document.getElementById('cart-count').textContent = t('js.distinct', cart.lineCount);
            document.getElementById('subtotal').textContent = price(cart.subtotal);
            const body = document.getElementById('cart-lines');
            body.replaceChildren();
            for (const line of cart.items) {
                const row = element('tr');
                const description = element('td');
                description.append(link(line.productName, '/shop/products/' + encodeURIComponent(line.productId)),
                    element('p', line.itemId, 'hint'), element('p', lineDescription(line)));
                const quantity = element('input');
                quantity.type = 'number'; quantity.step = '1'; quantity.required = true;
                quantity.value = line.quantity;
                quantity.setAttribute('aria-label', t('js.quantityLabel', line.itemId));
                const quantityCell = element('td'); quantityCell.append(quantity);
                const controls = element('td');
                const update = element('button', t('js.updateQuantity')); update.type = 'button';
                const remove = element('button', t('js.remove'), 'secondary'); remove.type = 'button';
                update.addEventListener('click', () => {
                    if (!quantity.reportValidity()) return;
                    mutate('/api/cart/items/' + encodeURIComponent(line.itemId), 'PUT', {quantity: Number(quantity.value)});
                });
                remove.addEventListener('click', () => mutate('/api/cart/items/' + encodeURIComponent(line.itemId), 'DELETE'));
                controls.append(update, remove);
                row.append(description, quantityCell, element('td', price(line.unitPrice), 'price'),
                    element('td', price(line.lineTotal), 'price'), controls);
                body.append(row);
            }
        }
        render(await api('/api/cart'));
        notify();
    }

    async function checkout() {
        const form = document.getElementById('checkout-form');
        const fields = document.getElementById('checkout-fields');
        const same = document.getElementById('same-as-billing');
        const shipping = document.getElementById('shipping-fields');
        function copyBilling() {
            if (same.checked) contactFields.forEach(name => {
                form.elements['shipping.' + name].value = form.elements['billing.' + name].value;
            });
            shipping.disabled = same.checked;
        }
        same.addEventListener('change', copyBilling);
        document.getElementById('billing-fields').addEventListener('input', copyBilling);
        const [cart, account] = await Promise.all([api('/api/cart'), api('/api/account')]);
        if (!cart.lineCount) {
            document.getElementById('empty-cart').hidden = false;
            notify();
            return;
        }
        for (const prefix of ['billing', 'shipping']) contactFields.forEach(name => {
            form.elements[prefix + '.' + name].value = account[name] || '';
        });
        const summary = document.getElementById('order-summary');
        for (const line of cart.items) {
            const row = element('div', undefined, 'summary-line');
            row.append(element('span', line.productName + ' (' + line.itemId + ') × ' + line.quantity),
                element('span', price(line.lineTotal), 'price'));
            summary.append(row);
        }
        document.getElementById('subtotal').textContent = price(cart.subtotal);
        document.getElementById('checkout-content').hidden = false;
        fields.disabled = false;
        copyBilling();
        notify();
        let submitting = false;
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (submitting) return;
            copyBilling();
            const contact = prefix => Object.fromEntries(contactFields.map(name => [name, form.elements[prefix + '.' + name].value]));
            const payload = {billingInfo: contact('billing'), shippingInfo: contact('shipping')};
            submitting = true;
            fields.disabled = true;
            notify(t('js.placing'));
            try {
                const order = await api('/api/checkout', 'POST', payload);
                document.getElementById('confirmation-id').textContent = order.orderId;
                document.getElementById('confirmation-status').textContent = order.status;
                document.getElementById('confirmation-total').textContent = price(order.totalPrice);
                document.getElementById('confirmation-created').textContent = new Date(order.createdAt).toLocaleString(locale);
                document.getElementById('checkout-content').hidden = true;
                const confirmation = document.getElementById('order-confirmation');
                confirmation.hidden = false;
                confirmation.focus();
                notify();
            } catch (error) { showError(error); } finally {
                fields.disabled = false;
                submitting = false;
            }
        });
    }

    const pages = {shop, category, product, item, cart, checkout};
    if (pages[page]) pages[page]().catch(showError);
})();
