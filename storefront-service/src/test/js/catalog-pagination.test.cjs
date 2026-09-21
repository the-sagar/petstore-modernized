// Optional presentation checks: node --test storefront-service/src/test/js/catalog-pagination.test.cjs
// Uses Node's built-in runner and a minimal DOM stand-in, without a browser framework.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync, existsSync, readdirSync} = require('node:fs');
const {resolve} = require('node:path');
const vm = require('node:vm');
const script = readFileSync(resolve(__dirname, '../../main/resources/static/assets/storefront.js'), 'utf8');

class Node {
    constructor() { this.hidden = true; this.children = []; this.listeners = {}; this.textContent = ''; this.value = ''; this.options = []; }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = nodes; }
    addEventListener(event, listener) { this.listeners[event] = listener; }
    setAttribute(name, value) { this[name] = value; }
}

async function browser(page, address, entity = {}) {
    const nodes = new Map();
    const get = id => { if (!nodes.has(id)) nodes.set(id, new Node()); return nodes.get(id); };
    get('locale-select').options = [{value: 'en-US'}, {value: 'ja-JP'}];
    const tag = new URL('http://localhost' + address).searchParams.get('locale') || 'en-US';
    const suffix = tag === 'ja-JP' ? '_ja_JP' : tag === 'zh-CN' ? '_zh_CN' : '';
    const messages = Object.fromEntries(readFileSync(resolve(__dirname,
        '../../main/resources/messages' + suffix + '.properties'), 'utf8').trim().split('\n').map(line => {
            const equals = line.indexOf('='); return [line.slice(0, equals), line.slice(equals + 1)];
        }));
    const text = (key, ...args) => messages[key].replace(/\{(\d+)\}/g, (_, i) => args[Number(i)] ?? '');
    const calls = [];
    const writes = [];
    const formattedLocales = [];
    let href = 'http://localhost:8080' + address;
    let navigated;
    const data = ['FI-FW-01', 'FI-FW-02', 'FI-SW-01', 'FI-SW-02'];
    const context = {
        URL, console,
        Intl: {NumberFormat: function (locale, options) {
            formattedLocales.push(locale);
            return new Intl.NumberFormat(locale, options);
        }},
        window: {petstoreLocale: tag, petstoreText: text, location: {get href() {return href;}, get pathname() {return new URL(href).pathname;},
            origin: 'http://localhost:8080', assign(value) {navigated = value;}}},
        history: {replaceState(_, __, value) { href = new URL(value, href).href; }},
        sessionStorage: {getItem() {return null;}, setItem() {}},
        document: {body: {dataset: {page}}, querySelectorAll() {return [];}, getElementById: get, querySelector() {return {content: "csrf"};},
            createElement(tag) {const node = new Node(); node.tagName = tag; return node;}},
        fetch: async (path, options) => {
            if (options.method !== "GET") writes.push({path, ...options});
            const request = new URL(path, href); calls.push(request);
            let payload;
            if (request.pathname === '/api/catalog/items/MISSING') return {status: 404, ok: false};
            if (request.pathname.startsWith('/api/catalog/items/')) payload = {id: request.pathname.split('/').at(-1), productId: 'FI-SW-01', categoryId: 'FISH', listPrice: 16.50, unitCost: 9.99, image: 'fish1.jpg', description: 'Angelfish', attributes: ['Large', 'Adult'], ...entity};
            else if (request.pathname === '/api/cart/items') payload = {lineCount: 1};
            else if (request.pathname === '/api/catalog/categories') payload = [{id: 'FISH', name: 'Fish', image: 'fish_icon.gif', ...entity}];
            else if (/\/categories\/[^/]+$|\/products\/[^/]+$/.test(request.pathname)) {
                payload = {id: request.pathname.split('/').at(-1), name: entity.parentName || 'Parent', categoryId: 'FISH'};
            } else {
                const index = Number(request.searchParams.get('page') || 0);
                const ids = request.searchParams.get('q') === 'none' ? [] : data;
                payload = {content: ids.slice(index * 2, index * 2 + 2).map(id => ({id, name: id, listPrice: 10, categoryId: 'FISH', image: 'fish2.gif', ...entity})),
                    page: index, size: 2, totalElements: ids.length, totalPages: ids.length / 2,
                    hasPrevious: index > 0, hasNext: index + 1 < ids.length / 2};
            }
            return {status: 200, ok: true, json: async () => payload};
        }
    };
    vm.runInNewContext(script, context);
    const settle = () => new Promise(resolve => setImmediate(resolve));
    await settle();
    return {get, calls, writes, settle, text, formattedLocales, url: () => new URL(href), navigated: () => navigated};
}

test('search pages retain submitted query/locale, refresh context and reset new submissions', async () => {
    const ui = await browser('shop', '/shop?locale=ja-JP&q=fish&page=1');
    assert.equal(ui.get('catalog-page').textContent, ui.text('js.page', 2, 2));
    assert.equal(ui.get('catalog-next').disabled, true);
    assert.equal(ui.get('catalog-previous').disabled, false);
    assert.equal(ui.get('message').textContent, ui.text('js.matching', 4));
    ui.get('search-query').value = 'not submitted';
    await ui.get('catalog-previous').onclick();
    assert.equal(ui.calls.at(-1).searchParams.get('q'), 'fish');
    assert.equal(ui.calls.at(-1).searchParams.get('locale'), 'ja-JP');
    assert.equal(ui.url().searchParams.get('page'), '0');
    assert.equal(ui.url().searchParams.get('q'), 'fish');
    assert.equal(ui.get('catalog-previous').disabled, true);
    assert.equal(ui.get('catalog-next').disabled, false);
    ui.get('search-query').value = 'new query';
    ui.get('search-form').listeners.submit({preventDefault() {}});
    await ui.settle();
    assert.equal(ui.url().searchParams.get('q'), 'new query');
    assert.equal(ui.url().searchParams.get('page'), '0');
    ui.get('locale-select').value = 'en-US';
    ui.get('locale-select').listeners.change();
    const changed = new URL(ui.navigated(), ui.url());
    assert.equal(changed.searchParams.get('q'), 'new query');
    assert.equal(changed.searchParams.has('page'), false);
});

test('empty search results never display Page 1 of 0', async () => {
    const ui = await browser('shop', '/shop?q=none');
    assert.equal(ui.get('catalog-page').textContent, 'No results');
    assert.equal(ui.get('catalog-previous').disabled, true);
    assert.equal(ui.get('catalog-next').disabled, true);
    assert.equal(ui.get('message').textContent, '0 matching products.');
    assert.match(ui.get('search-results').children[0].textContent, /No products found/);
});

for (const [page, path, container] of [['category', '/shop/categories/FISH', 'products'],
        ['product', '/shop/products/FI-SW-01', 'items']]) {
    test(page + ' pages fetch only the selected page and replace rendered cards', async () => {
        const ui = await browser(page, path + '?locale=ja-JP');
        assert.equal(ui.get(container).children.length, 2);
        assert.equal(ui.get('catalog-page').textContent, ui.text('js.page', 1, 2));
        assert.equal(ui.get('catalog-previous').disabled, true);
        await ui.get('catalog-next').onclick();
        assert.equal(ui.get(container).children.length, 2);
        assert.equal(ui.get('catalog-page').textContent, ui.text('js.page', 2, 2));
        assert.equal(ui.get('catalog-next').disabled, true);
        assert.equal(ui.calls.at(-1).searchParams.get('page'), '1');
        assert.equal(ui.calls.at(-1).searchParams.get('locale'), 'ja-JP');
        assert.equal(ui.url().searchParams.get('page'), '1');
    });
}

for (const locale of ['en-US', 'ja-JP', 'zh-CN']) {
    test('server-rendered messages drive dynamic UI for ' + locale, async () => {
        const ui = await browser('shop', '/shop?q=fish&locale=' + locale);
        assert.equal(ui.get('message').textContent, ui.text('js.matching', 4));
        assert.equal(ui.get('catalog-page').textContent, ui.text('js.page', 1, 2));
        assert.equal(ui.calls.at(-1).searchParams.get('locale'), locale);
    });
}

for (const locale of ['en-US', 'ja-JP', 'zh-CN']) {
    test('price display uses effective locale without currency conversion: ' + locale, async () => {
        const ui = await browser('product', '/shop/products/FI-SW-01?locale=' + locale);
        assert.deepEqual(ui.formattedLocales, [locale, locale]);
        assert.equal(ui.get('items').children[0].children.find(node => node.className === 'item-price').textContent,
            new Intl.NumberFormat(locale, {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(10));
    });
}


test('legacy image metadata remains, without redistributed artwork', () => {
    const xml = readFileSync(resolve(__dirname, '../../main/resources/legacy/catalog.xml'), 'utf8');
    const referenced = [...new Set([...xml.matchAll(/<Image>([^<]+)<\/Image>/g)].map(match => match[1]))].sort();
    assert.deepEqual(referenced, ['bird1.gif', 'bird4.gif', 'birds_icon.gif', 'cat1.gif', 'cat3.gif', 'cats_icon.gif',
        'dog1.gif', 'dog2.gif', 'dog4.gif', 'dog5.gif', 'dog6.gif', 'dogs_icon.gif', 'fish1.jpg', 'fish2.gif',
        'fish3.gif', 'fish4.gif', 'fish_icon.gif', 'lizard2.gif', 'lizard3.gif', 'reptiles_icon.gif']);
    const directory = resolve(__dirname, '../../main/resources/static/assets/images/catalog');
    assert.ok(!existsSync(directory) || readdirSync(directory).length === 0);
});

function assertPlaceholder(area, label, symbol = '🐟') {
    assert.equal(area.tagName, 'div');
    assert.equal(area.role, 'img');
    assert.equal(area['aria-label'], label);
    assert.equal(area.children[0].textContent, symbol);
    assert.equal(area.children[0]['aria-hidden'], 'true');
    function noImage(node) {
        assert.notEqual(node.tagName, 'img');
        assert.equal(node.src, undefined);
        node.children.forEach(noImage);
    }
    noImage(area);
}

for (const [page, path, container] of [['shop', '/shop', 'categories'],
    ['category', '/shop/categories/FISH', 'products'], ['shop', '/shop?q=fish', 'search-results'],
    ['product', '/shop/products/FI-SW-01', 'items']]) {
    for (const [locale, label] of [['en-US', 'Fish'], ['ja-JP', '魚'], ['zh-CN', '鱼']]) {
        test(`${container} shows safe category placeholders and localized accessible text: ${locale}`, async () => {
            const ui = await browser(page, path + (path.includes('?') ? '&' : '?') + 'locale=' + locale,
                {name: label, description: label});
            const card = ui.get(container).children[0];
            assertPlaceholder(card.children[0], label);
            assert.ok(card.children.some(node => node.tagName === 'h3'));
        });
    }
}

for (const filename of ['fish1.jpg', 'fish2.gif', 'missing.gif']) {
    test(`legacy or missing filename immediately uses placeholder without an image request: ${filename}`, async () => {
        const ui = await browser('category', '/shop/categories/FISH', {image: filename, name: 'Angelfish'});
        assertPlaceholder(ui.get('products').children[0].children[0], 'Angelfish');
        assert.ok(ui.calls.every(call => call.pathname.startsWith('/api/')));
    });
}

for (const image of ['https://example.com/pet.gif', 'http://example.com/pet.gif', '//example.com/pet.gif',
    '../fish2.gif', '%2e%2e%2ffish2.gif', 'folder/fish2.gif', 'data:image/png;base64,AAAA', '', null]) {
    test(`unsafe or absent image metadata stays local: ${image}`, async () => {
        const ui = await browser('category', '/shop/categories/FISH', {image});
        assertPlaceholder(ui.get('products').children[0].children[0], 'FI-FW-01');
    });
}

test('unknown category with no image renders neutral text', async () => {
    const ui = await browser('category', '/shop/categories/FISH', {image: null, categoryId: 'UNKNOWN', name: 'Pet'});
    assertPlaceholder(ui.get('products').children[0].children[0], 'Pet', '◇');
});

test('Add to Cart still posts the item ID after image fallback', async () => {
    const ui = await browser('product', '/shop/products/FI-SW-01', {image: 'fish1.jpg'});
    const add = ui.get('items').children[0].children.find(node => node.tagName === 'button');
    await add.listeners.click();
    assert.equal(ui.writes.length, 1);
    assert.equal(ui.writes[0].method, 'POST');
    assert.deepEqual(JSON.parse(ui.writes[0].body), {itemId: 'FI-FW-01'});
    assert.equal(add.disabled, false);
    assert.equal(ui.get('message').className, 'message success');
});


for (const [locale, name, description] of [['en-US', 'Angelfish', 'Large Angelfish'],
    ['ja-JP', 'エンゼルフィッシュ', '大きいエンゼルフィッシュ'], ['zh-CN', '神仙鱼', '大型神仙鱼']]) {
    test(`Item Detail renders selected SKU and localized data using listPrice: ${locale}`, async () => {
        const ui = await browser('item', '/shop/items/EST-1?locale=' + locale, {parentName: name, description});
        assert.equal(ui.calls[0].pathname, '/api/catalog/items/EST-1');
        assert.equal(ui.get('item-id').textContent, 'EST-1');
        assert.equal(ui.get('item-product-name').textContent, name);
        assert.equal(ui.get('item-description').textContent, description);
        assert.equal(ui.get('item-attributes').textContent, 'Large · Adult');
        assert.equal(ui.get('item-price').textContent, new Intl.NumberFormat(locale,
            {minimumFractionDigits: 2, maximumFractionDigits: 2}).format(16.50));
        assert.equal(ui.get('item-content').hidden, false);
        assertPlaceholder(ui.get('item-image').children[0], description);
        for (const id of ['category-link', 'product-link']) {
            assert.equal(new URL(ui.get(id).href, ui.url()).searchParams.get('locale'), locale);
        }
        assert.equal(new URL(ui.get('product-link').href, ui.url()).pathname, '/shop/products/FI-SW-01');
        assert.ok(ui.calls.every(call => call.searchParams.get('locale') === locale));
        const add = ui.get('item-actions').children[0];
        assert.equal(add['aria-label'], ui.text('js.addLabel', 'EST-1'));
        await add.listeners.click();
        assert.equal(ui.writes[0].path.split('?')[0], '/api/cart/items');
        assert.deepEqual(JSON.parse(ui.writes[0].body), {itemId: 'EST-1'});
    });
}

test('Product item ID links to detail without removing direct Add to Cart', async () => {
    const ui = await browser('product', '/shop/products/FI-SW-01?locale=ja-JP&page=1');
    const card = ui.get('items').children[0];
    const anchor = card.children.find(node => node.tagName === 'h3').children[0];
    assert.equal(anchor.href, '/shop/items/FI-SW-01?locale=ja-JP');
    assert.ok(card.children.some(node => node.tagName === 'button'));
    assert.equal(ui.get('catalog-page').textContent, ui.text('js.page', 2, 2));
});

test('unknown Item shows controlled localized error and no purchase action', async () => {
    const ui = await browser('item', '/shop/items/MISSING?locale=ja-JP');
    assert.equal(ui.get('message').textContent, ui.text('js.notFound'));
    assert.equal(ui.get('message').className, 'message error');
    assert.equal(ui.get('item-content').hidden, true);
    assert.equal(ui.get('item-actions').children.length, 0);
    assert.equal(ui.calls.length, 1);
});

for (const [categoryId, symbol] of [['DOGS', '🐕'], ['CATS', '🐈'], ['BIRDS', '🐦'], ['REPTILES', '🦎']]) {
    test(`category presentation uses its own symbol: ${categoryId}`, async () => {
        const ui = await browser('shop', '/shop', {id: categoryId, name: 'Localized category'});
        assertPlaceholder(ui.get('categories').children[0].children[0], 'Localized category', symbol);
    });
}

for (const locale of ['en-US', 'ja-JP', 'zh-CN']) {
    test(`Item Detail tolerates absent optional text, attributes and image: ${locale}`, async () => {
        const ui = await browser('item', '/shop/items/EST-1?locale=' + locale,
            {description: null, attributes: null, image: null, parentName: null});
        assert.equal(ui.get('item-description').textContent, '');
        assert.equal(ui.get('item-attributes').textContent, '');
        assert.equal(ui.get('item-id').textContent, 'EST-1');
        assertPlaceholder(ui.get('item-image').children[0], 'EST-1');
        const add = ui.get('item-actions').children[0];
        await add.listeners.click();
        assert.deepEqual(JSON.parse(ui.writes[0].body), {itemId: 'EST-1'});
        assert.ok(ui.calls.every(call => call.origin === 'http://localhost:8080'));
    });
}

test('missing Item price is not presented as a free item', async () => {
    const ui = await browser('item', '/shop/items/EST-1', {listPrice: null});
    assert.equal(ui.get('item-price').textContent, '—');
});
