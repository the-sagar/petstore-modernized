const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const resources = path.resolve(__dirname, '../../main/resources');
const html = fs.readFileSync(path.join(resources, 'templates/account.html'), 'utf8');
const script = html.match(/<script th:inline="javascript">([\s\S]*?)<\/script>/)[1];

async function page(account, suffix = '') {
    const messages = Object.fromEntries(fs.readFileSync(path.join(resources, `messages${suffix}.properties`), 'utf8')
        .split('\n').filter(line => line.includes('=')).map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
    const nodes = {};
    for (const [, id] of html.matchAll(/id="([^"]+)"/g)) nodes[id] = {textContent: '', hidden: true, addEventListener() {}};
    const inputs = ['cardNumber', 'cardType', 'expiryDate'].map(name => ({name, value: '', type: name === 'cardNumber' ? 'password' : 'text'}));
    let submit;
    let response = account;
    const requests = [];
    Object.assign(nodes['account-form'], {action: '/api/account', querySelectorAll: () => inputs,
        addEventListener: (event, handler) => { if (event === 'submit') submit = handler; }});
    const text = (key, ...args) => messages[key].replace(/\{(\d+)\}/g, (_, i) => args[Number(i)] ?? '');
    vm.runInNewContext(script, {
        document: {getElementById: id => nodes[id], querySelector: () => ({content: 'csrf'})},
        window: {petstoreText: text, petstoreUrl: value => value, location: {assign() {}}},
        fetch: async (url, options) => { requests.push(options); return {status: 200, ok: true, json: async () => response}; }
    });
    await new Promise(resolve => setImmediate(resolve));
    return {nodes, inputs, requests, text, async save(number, next) {
        inputs[0].value = number; response = next; await submit({preventDefault() {}});
    }};
}

for (const [suffix, ending, expiry] of [['', 'Card ending in 1111', 'Expires 12/2030'],
    ['_ja_JP', '下4桁が 1111 のカード', '有効期限 12/2030'], ['_zh_CN', '尾号为 1111 的卡片', '有效期 12/2030']]) {
    test(`saved card is masked, accessible and localized ${suffix || 'English'}`, async () => {
        const p = await page({cardType: 'VISA', last4: '1111', expiryDate: '12/2030'}, suffix);
        assert.equal(p.nodes['saved-card-number'].textContent, '•••• •••• •••• 1111');
        assert.equal(p.nodes['saved-card-type'].textContent, 'VISA');
        assert.equal(p.nodes['saved-card-accessible'].textContent, ending);
        assert.equal(p.nodes['saved-card-expiry'].textContent, expiry);
        assert.equal(p.nodes['saved-card-details'].hidden, false);
        assert.equal(p.nodes['saved-card-empty'].hidden, true);
        assert.equal(p.inputs[0].value, '');
    });
}

test('blank replacement preserves summary; successful replacement refreshes metadata and clears input', async () => {
    const old = {cardType: 'VISA', last4: '1111', expiryDate: '12/2030'};
    const p = await page(old);
    await p.save('', old);
    assert.equal(JSON.parse(p.requests.at(-1).body).cardNumber, '');
    assert.equal(p.nodes['saved-card-number'].textContent, '•••• •••• •••• 1111');
    await p.save('5555 5555 5555 4444', {cardType: 'MASTERCARD', last4: '4444', expiryDate: '11/2032'});
    assert.equal(p.nodes['saved-card-number'].textContent, '•••• •••• •••• 4444');
    assert.equal(p.nodes['saved-card-type'].textContent, 'MASTERCARD');
    assert.equal(p.nodes['saved-card-expiry'].textContent, 'Expires 11/2032');
    assert.equal(p.inputs[0].value, '');
    assert.ok(!Object.values(p.nodes).some(node => node.textContent.includes('5555')));
});

test('missing card hides summary; missing expiry is omitted; malformed last4 is not displayed', async () => {
    const p = await page({});
    assert.equal(p.nodes['saved-card-empty'].hidden, false);
    assert.equal(p.nodes['saved-card-details'].hidden, true);
    await p.save('', {cardType: 'VISA', last4: '1111'});
    assert.equal(p.nodes['saved-card-expiry'].hidden, true);
    await p.save('', {last4: '4111111111111111'});
    assert.equal(p.nodes['saved-card-details'].hidden, true);
    assert.equal(p.nodes['saved-card-number'].textContent, '');
});
