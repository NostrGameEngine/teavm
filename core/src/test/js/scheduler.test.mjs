import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';

const compiled = await build({
    entryPoints: [new URL('../../main/js/wasm-gc-runtime/scheduler.ts', import.meta.url).pathname],
    bundle: true, write: false, format: 'esm', platform: 'node'
});
const { createScheduler } = await import('data:text/javascript;base64,'
    + Buffer.from(compiled.outputFiles[0].text).toString('base64'));

function withClock(run) {
    const original = { setTimeout, clearTimeout, now: Date.now, performance };
    let time = 0;
    let id = 0;
    const timers = new Map();
    globalThis.setTimeout = (callback, delay = 0) => {
        timers.set(++id, { callback, at: time + delay });
        return id;
    };
    globalThis.clearTimeout = id => timers.delete(id);
    Date.now = () => time;
    globalThis.performance = { now: () => time };
    const clock = {
        advance: duration => { time += duration; },
        next() {
            const first = [...timers].sort((a, b) => a[1].at - b[1].at)[0];
            if (!first) return false;
            timers.delete(first[0]);
            time = Math.max(time, first[1].at);
            first[1].callback();
            return true;
        },
        drain() {
            for (let i = 0; i < 1000; i++) if (!this.next()) return;
            throw new Error('Scheduler did not become idle');
        }
    };
    try { run(clock); } finally {
        globalThis.setTimeout = original.setTimeout;
        globalThis.clearTimeout = original.clearTimeout;
        globalThis.performance = original.performance;
        Date.now = original.now;
    }
}

test('priorities order ready work and preserve FIFO within each priority', () => withClock(clock => {
    const scheduler = createScheduler();
    const result = [];
    for (const [value, priority] of [['low', 1], ['high1', 10], ['normal', 5], ['high2', 10]]) {
        scheduler.offer(value, value => result.push(value), 0, priority);
    }
    clock.drain();
    assert.deepEqual(result, ['high1', 'high2', 'normal', 'low']);
}));

test('time budget yields between callbacks', () => withClock(clock => {
    const scheduler = createScheduler({ timeSliceMillis: 4 });
    let count = 0;
    for (let i = 0; i < 8; i++) scheduler.offer(null, () => { count++; clock.advance(2); }, 0);
    clock.next();
    assert.equal(count, 2);
    clock.drain();
    assert.equal(count, 8);
}));

test('aging lets low priority work run under sustained high priority load', () => withClock(clock => {
    const scheduler = createScheduler();
    let lowRan = false;
    let attempts = 0;
    const high = () => {
        clock.advance(4);
        if (++attempts < 100) scheduler.offer(null, high, Date.now(), 10);
    };
    scheduler.offer(null, high, 0, 10);
    scheduler.offer(null, () => { lowRan = true; assert.ok(attempts < 50); }, 0, 1);
    clock.drain();
    assert.ok(lowRan);
}));

test('priority never advances a deadline', () => withClock(clock => {
    const scheduler = createScheduler();
    const result = [];
    scheduler.offer(null, () => result.push(['late', Date.now()]), 50, 10);
    scheduler.offer(null, () => result.push(['now', Date.now()]), 0, 1);
    clock.drain();
    assert.deepEqual(result, [['now', 0], ['late', 50]]);
}));

test('cancellation works before and after a timer becomes ready', () => withClock(clock => {
    const scheduler = createScheduler();
    const fail = () => assert.fail('Cancelled callback ran');
    scheduler.kill(scheduler.offer(null, fail, 0));
    scheduler.kill(scheduler.offer(null, fail, 100));
    const later = scheduler.offer(null, fail, 10);
    clock.next(); // Drain cancelled ready entry.
    clock.next(); // Timer places its task on the ready queue.
    scheduler.kill(later);
    clock.drain();
}));

test('throwing callback does not strand queued work', () => withClock(clock => {
    const scheduler = createScheduler();
    let ran = false;
    scheduler.offer(null, () => { throw new Error('expected'); }, 0);
    scheduler.offer(null, () => { ran = true; }, 0);
    assert.throws(() => clock.next(), /expected/);
    clock.drain();
    assert.ok(ran);
}));

test('recursive producers return to the event loop even with a stationary clock', () => withClock(clock => {
    const scheduler = createScheduler();
    let count = 0;
    const task = () => { if (++count < 200) scheduler.offer(null, task, 0); };
    scheduler.offer(null, task, 0);
    clock.next();
    assert.equal(count, 64);
    clock.drain();
    assert.equal(count, 200);
}));

test('instances are isolated and budgets validated', () => withClock(clock => {
    const first = createScheduler();
    const second = createScheduler({ timeSliceMillis: 2 });
    let ran = false;
    const id = second.offer(null, () => { ran = true; }, 0);
    first.kill(id);
    clock.drain();
    assert.ok(ran);
    assert.equal(second.timeSlice(), 2);
    for (const value of [0, -1, 101, NaN, 1.5]) {
        assert.throws(() => createScheduler({ timeSliceMillis: value }), RangeError);
    }
}));
