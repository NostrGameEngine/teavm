/* Copyright 2026, TeaVM contributors. Licensed under the Apache License, Version 2.0. */

/** Per-instance reuse of immutable JS strings, keyed by opaque Wasm char arrays. */
export function createStringCache() {
    // Bound nominal UTF-16 storage and entry count. JS slices/ropes and the
    // engine's GC can use more physical memory than their logical string size.
    const maxUnits = 262144;
    const maxEntries = 64;
    const values = new Map<object, string>();
    let units = 0;
    return {
        get(data: object): string | null {
            const value = values.get(data);
            if (value === undefined) return null;
            values.delete(data);
            values.set(data, value);
            return value;
        },
        put(data: object, value: unknown): void {
            // Fallback imports can copy mutable string-like objects. Never
            // replace their Java snapshot with a reference to that source.
            if (typeof value !== "string" || value.length > maxUnits) return;
            const previous = values.get(data);
            if (previous !== undefined) {
                units -= previous.length;
                values.delete(data);
            }
            values.set(data, value);
            units += value.length;
            while (units > maxUnits || values.size > maxEntries) {
                const first = values.entries().next().value!;
                values.delete(first[0]);
                units -= first[1].length;
            }
        }
    };
}
