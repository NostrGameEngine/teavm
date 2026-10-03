import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';

const compiled = await build({
    entryPoints: [new URL('../../main/js/wasm-gc-runtime/strings.ts', import.meta.url).pathname],
    bundle: true, write: false, format: 'esm', platform: 'node'
});
const {createStringCache} = await import('data:text/javascript;base64,'
    + Buffer.from(compiled.outputFiles[0].text).toString('base64'));

test('uses array identity and preserves empty and arbitrary UTF-16 strings', () => {
    const cache = createStringCache(), key = {}, equalKey = {};
    assert.equal(cache.get(key), null);
    cache.put(key, '');
    assert.equal(cache.get(key), '');
    assert.equal(cache.get(equalKey), null);
    cache.put(key, '\u0000\ud800\udc00\udfff\uffff');
    assert.equal(cache.get(key), '\u0000\ud800\udc00\udfff\uffff');
});

test('bounds entry count even for empty strings and refreshes recent reads', () => {
    const cache = createStringCache(), keys = Array.from({length: 65}, () => ({}));
    for (const key of keys.slice(0, 64)) cache.put(key, '');
    assert.equal(cache.get(keys[0]), '');
    cache.put(keys[64], '');
    assert.equal(cache.get(keys[1]), null);
    assert.equal(cache.get(keys[0]), '');
    assert.equal(cache.get(keys[64]), '');
});

test('evicts large strings at the UTF-16 budget and does not retain oversized inputs', () => {
    const cache = createStringCache(), keys = Array.from({length: 6}, () => ({}));
    for (const key of keys.slice(0, 4)) cache.put(key, 'x'.repeat(65536));
    assert.equal(cache.get(keys[0]).length, 65536);
    cache.put(keys[4], 'x'.repeat(65536));
    assert.equal(cache.get(keys[1]), null);
    assert.equal(cache.get(keys[0]).length, 65536);
    cache.put(keys[5], 'x'.repeat(262145));
    assert.equal(cache.get(keys[5]), null);
    assert.equal(cache.get(keys[4]).length, 65536);
});

test('updating a key accounts for its replacement rather than accumulating size', () => {
    const cache = createStringCache(), first = {}, second = {};
    cache.put(first, 'x'.repeat(262144));
    cache.put(first, 'small');
    cache.put(second, 'x'.repeat(262139));
    assert.equal(cache.get(first), 'small');
    assert.equal(cache.get(second).length, 262139);
});

test('copies remain independent of mutable string-like sources', () => {
    const cache = createStringCache(), key = {};
    for (const value of [new String('boxed'), {length: 3, charCodeAt: () => 97}, null, undefined, 42]) {
        cache.put(key, value);
        assert.equal(cache.get(key), null);
    }
    cache.put(key, 'snapshot');
    cache.put(key, new String('changed'));
    assert.equal(cache.get(key), 'snapshot');
});

test('runtime instances own separate caches', () => {
    const first = createStringCache(), second = createStringCache(), key = {};
    first.put(key, 'first');
    assert.equal(second.get(key), null);
    second.put(key, 'second');
    assert.equal(first.get(key), 'first');
    assert.equal(second.get(key), 'second');
});
