const decoder = new TextDecoder()

/**
 * Flat Compressed Sparse Row (CSR) representation of a stack graph, used as
 * the wire format between ust/WASM and JS. This is the shape that worker job
 * types carry.
 *
 * Children are stored in CSR format:
 *   children of node i = childrenData[childrenOffsets[i] .. childrenOffsets[i+1]]
 *
 * Names and display names are UTF-8 byte buffers with parallel offset arrays:
 *   name of node i = namesData[namesOffsets[i] .. namesOffsets[i+1]]
 */
export interface StackGraph {
    childrenOffsets: Int32Array
    childrenData: Int32Array
    namesData: Uint8Array
    namesOffsets: Int32Array
    values: BigInt64Array
    displayNamesData: Uint8Array
    displayNamesOffsets: Int32Array
}

/**
 * A stack graph with lazily-decoded string caches.
 */
export class Graph {

    private readonly data: StackGraph
    private readonly nameCache: (string | undefined)[]
    private readonly displayNameCache: (string | undefined)[]
    private readonly lowerNameCache: (string | undefined)[]

    constructor(data: StackGraph) {
        this.data = data
        const n = data.namesOffsets.length - 1
        this.nameCache = new Array(n)
        this.displayNameCache = new Array(n)
        this.lowerNameCache = new Array(n)
    }

    get nodeCount(): number {
        return nodeCount(this.data)
    }

    /** Sample counts indexed by node ID. */
    get values(): BigInt64Array {
        return this.data.values
    }

    /** Returns the children of node `nodeId` as a typed array view. */
    getChildren(nodeId: number): Int32Array {
        return this.data.childrenData.subarray(
            this.data.childrenOffsets[nodeId],
            this.data.childrenOffsets[nodeId + 1],
        )
    }

    /** Returns the full name of node `i`. */
    getNodeName(i: number): string {
        return (this.nameCache[i] ??= decoder.decode(
            this.data.namesData.subarray(
                this.data.namesOffsets[i],
                this.data.namesOffsets[i + 1],
            ),
        ))
    }

    /** Returns the display name of node `i`. */
    getDisplayName(i: number): string {
        return (this.displayNameCache[i] ??= decoder.decode(
            this.data.displayNamesData.subarray(
                this.data.displayNamesOffsets[i],
                this.data.displayNamesOffsets[i + 1],
            ),
        ))
    }

    /** Returns the lowercased name of node `i`. */
    getNodeNameLower(i: number): string {
        return (this.lowerNameCache[i] ??= this.getNodeName(i).toLowerCase())
    }

    /**
     * Returns the underlying raw typed-array data, suitable for sending to a
     * worker via postMessage.
     */
    toRaw(): StackGraph {
        return this.data
    }

}

/** Returns the number of nodes in a raw StackGraph. Used in the worker where Graph is not available. */
export function nodeCount(graph: StackGraph): number {
    return graph.namesOffsets.length - 1
}
