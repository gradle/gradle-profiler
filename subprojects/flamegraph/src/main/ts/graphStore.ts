/**
 * Out-of-band store for StackGraph objects.
 *
 * StackGraph can be very large (hundreds of MB of node names, children arrays,
 * and BigInt64Array values). React DevTools recursively introspects every prop
 * on every component during development, which causes severe performance
 * degradation when large objects are passed as props.
 *
 * To avoid this, StackGraph objects are never passed as React props. Instead,
 * they are stored here (outside React's awareness) and components receive only
 * a lightweight string ID. Components look up the graph via getGraph() when
 * they actually need it for rendering or computation.
 *
 * Lifecycle:
 *   - storeGraph() is called when a graph arrives from a worker (in
 *     useGraphTabs or useGraphMutation) and returns an opaque ID.
 *   - removeGraph() is called when a tab is deleted or a mutation replaces
 *     the graph with a new one, preventing unbounded memory growth.
 */
import type { StackGraph } from "./worker"

const store = new Map<string, StackGraph>()
let counter = 0

export function storeGraph(graph: StackGraph): string {
    const id = `graph-${counter++}`
    store.set(id, graph)
    return id
}

export function getGraph(id: string): StackGraph | undefined {
    return store.get(id)
}

export function removeGraph(id: string): void {
    store.delete(id)
}
