// Optional presentation checks: node --test storefront-service/src/test/js/catalog-pagination.test.cjs
// Uses Node's built-in runner and a minimal DOM stand-in, without a browser framework.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');
const vm = require('node:vm');
const script = readFileSync(resolve(__dirname, '../../main/resources/static/assets/storefront.js'), 'utf8');

class Node {
    constructor() { this.children = []; this.listeners = {}; this.textContent = ''; this.value = ''; this.options = []; }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = nodes; }
    addEventListener(event, listener) { this.listeners[event] = listener; }
    setAttribute(name, value) { this[name] = value; }
}

async function browser(page, address) {
    const nodes = new Map();
    const get = id => { if (!nodes.has(id)) nodes.set(id, new Node()); return nodes.get(id); };
    get('locale-select').options = [{value: 'en-US'}, {value: 'ja-JP'}];
    const calls = [];
    let href = 'http://localhost:8080' + address;
    let navigated;
    const data = ['FI-FW-01', 'FI-FW-02', 'FI-SW-01', 'FI-SW-02'];
    const context = {
        URL, Intl, console,
        window: {location: {get href() {return href;}, get pathname() {return new URL(href).pathname;},
            origin: 'http://localhost:8080', assign(value) {navigated = value;}}},
        history: {replaceState(_, __, value) { href = new URL(value, href).href; }},
        sessionStorage: {getItem() {return null;}, setItem() {}},
        document: {body: {dataset: {page}}, querySelectorAll() {return [];}, getElementById: get,
            createElement() {return new Node();}},
        fetch: async path => {
            const request = new URL(path, href); calls.push(request);
            let payload;
            if (request.pathname === '/api/catalog/categories') payload = [];
            else if (/\/categories\/[^/]+$|\/products\/[^/]+$/.test(request.pathname)) {
                payload = {id: 'parent', name: 'Parent', categoryId: 'FISH'};
            } else {
                const index = Number(request.searchParams.get('page') || 0);
                const ids = request.searchParams.get('q') === 'none' ? [] : data;
                payload = {content: ids.slice(index * 2, index * 2 + 2).map(id => ({id, name: id, listPrice: 10})),
                    page: index, size: 2, totalElements: ids.length, totalPages: ids.length / 2,
                    hasPrevious: index > 0, hasNext: index + 1 < ids.length / 2};
            }
            return {status: 200, ok: true, json: async () => payload};
        }
    };
    vm.runInNewContext(script, context);
    const settle = () => new Promise(resolve => setImmediate(resolve));
    await settle();
    return {get, calls, settle, url: () => new URL(href), navigated: () => navigated};
}

test('search pages retain submitted query/locale, refresh context and reset new submissions', async () => {
    const ui = await browser('shop', '/shop?locale=ja-JP&q=fish&page=1');
    assert.equal(ui.get('catalog-page').textContent, 'Page 2 of 2');
    assert.equal(ui.get('catalog-next').disabled, true);
    assert.equal(ui.get('catalog-previous').disabled, false);
    assert.equal(ui.get('message').textContent, '4 matching products.');
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
        assert.equal(ui.get('catalog-page').textContent, 'Page 1 of 2');
        assert.equal(ui.get('catalog-previous').disabled, true);
        await ui.get('catalog-next').onclick();
        assert.equal(ui.get(container).children.length, 2);
        assert.equal(ui.get('catalog-page').textContent, 'Page 2 of 2');
        assert.equal(ui.get('catalog-next').disabled, true);
        assert.equal(ui.calls.at(-1).searchParams.get('page'), '1');
        assert.equal(ui.calls.at(-1).searchParams.get('locale'), 'ja-JP');
        assert.equal(ui.url().searchParams.get('page'), '1');
    });
}
