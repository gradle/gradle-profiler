/**
 * Keeps the K items with the largest keys seen so far.
 *
 * Internally maintained as a flat array with a linear min-scan on eviction.
 * This is O(K) per insertion in the worst case, which is fine for small K (≤ ~500).
 */
export class TopKHeap<V> {
    private readonly capacity: number
    private readonly items: Array<{ key: bigint; value: V }> = []
    private minIdx = 0
    private minKey: bigint = 0n

    constructor(capacity: number) {
        this.capacity = capacity
    }

    push(key: bigint, value: V): void {
        if (this.items.length < this.capacity) {
            this.items.push({ key, value })
            if (this.items.length === this.capacity) {
                this.updateMin()
            }
        } else if (key > this.minKey) {
            this.items[this.minIdx] = { key, value }
            this.updateMin()
        }
    }

    /** Returns all collected items sorted by key descending. */
    toSortedArray(): V[] {
        return this.items
            .slice()
            .sort((a, b) => (b.key > a.key ? 1 : b.key < a.key ? -1 : 0))
            .map((item) => item.value)
    }

    private updateMin(): void {
        this.minIdx = 0
        this.minKey = this.items[0]!.key
        for (let i = 1; i < this.items.length; i++) {
            if (this.items[i]!.key < this.minKey) {
                this.minKey = this.items[i]!.key
                this.minIdx = i
            }
        }
    }
}
