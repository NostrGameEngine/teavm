/* Copyright 2026, TeaVM contributors. Licensed under the Apache License, Version 2.0. */

export interface SchedulerOptions {
    /** Cooperative time budget. Running synchronous code cannot be preempted. */
    timeSliceMillis?: number;
}

interface Task {
    id: number;
    instance: unknown;
    fn: (instance: unknown) => void;
    priority: number;
    readyAt: number;
    next: Task | null;
    timer?: ReturnType<typeof setTimeout>;
}

/** Per-instance ready queue; deadlines control eligibility, not Java thread priority. */
export function createScheduler(options: SchedulerOptions = {}) {
    const budget = options.timeSliceMillis ?? 4;
    if (!Number.isInteger(budget) || budget < 1 || budget > 100) {
        throw new RangeError("timeSliceMillis must be an integer between 1 and 100");
    }
    const heads: (Task | null)[] = Array(10).fill(null);
    const tails: (Task | null)[] = Array(10).fill(null);
    const tasks = new Map<number, Task>();
    let nextId = 1;
    let scheduled = false;
    const now = () => performance.now();

    function requestRun() {
        if (!scheduled) {
            scheduled = true;
            setTimeout(run, 0);
        }
    }

    function ready(task: Task) {
        task.timer = undefined;
        task.readyAt = now();
        const index = task.priority - 1;
        if (tails[index]) {
            tails[index]!.next = task;
        } else {
            heads[index] = task;
        }
        tails[index] = task;
        requestRun();
    }

    function run() {
        const started = now();
        let count = 0;
        try {
            while (count++ < 64) {
                const time = now();
                let selected = -1;
                let best = -1;
                for (let i = 0; i < 10; i++) {
                    // Cancelled entries release their payload immediately in kill().
                    while (heads[i] && !tasks.has(heads[i]!.id)) {
                        heads[i] = heads[i]!.next;
                        if (!heads[i]) tails[i] = null;
                    }
                    const task = heads[i];
                    if (!task) continue;
                    // Aging prevents a continuous high-priority producer starving other threads.
                    const score = Math.min(10, task.priority + Math.floor((time - task.readyAt) / 16));
                    if (score > best || (score === best
                            && task.readyAt < heads[selected]!.readyAt)) {
                        best = score;
                        selected = i;
                    }
                }
                if (selected < 0) return;
                const task = heads[selected]!;
                heads[selected] = task.next;
                if (!task.next) tails[selected] = null;
                tasks.delete(task.id);
                task.next = null;
                task.fn(task.instance);
                if (now() - started >= budget) return;
            }
        } finally {
            scheduled = false;
            if (heads.some(head => head !== null)) requestRun();
        }
    }

    return {
        timeSlice: () => budget,
        offer(instance: unknown, fn: (instance: unknown) => void, time: number, priority = 5) {
            while (tasks.has(nextId)) nextId = nextId === 0x7fffffff ? 1 : nextId + 1;
            const id = nextId;
            nextId = nextId === 0x7fffffff ? 1 : nextId + 1;
            const task: Task = { id, instance, fn, priority: Math.max(1, Math.min(10, priority)),
                readyAt: 0, next: null };
            tasks.set(id, task);
            const delay = Math.max(0, time - Date.now());
            if (delay > 0) {
                task.timer = setTimeout(() => ready(task), delay);
            } else {
                ready(task);
            }
            return id;
        },
        kill(id: number) {
            const task = tasks.get(id);
            if (!task) return;
            if (task.timer !== undefined) clearTimeout(task.timer);
            tasks.delete(id);
            task.instance = null;
            task.fn = () => {};
        }
    };
}
