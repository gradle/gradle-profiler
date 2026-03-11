import { decodeAndDecompressData } from "./encoding.ts"

export interface ParseEncodedDataJob {
    encodedData: string
    type: "parseEncodedData"
}

export interface ParseStreamJob {
    stream: ReadableStream<Uint8Array>
    type: "parseStream"
}

export interface MergeChildrenJob {
    nodeName: string
    graph: StackGraph
    type: "mergeChildren"
}

export interface IcicleGraphJob {
    nodeId: number
    graph: StackGraph
    type: "icicleGraph"
}

export type Job =
    | ParseStreamJob
    | ParseEncodedDataJob
    | MergeChildrenJob
    | IcicleGraphJob

export interface WorkerParams {
    job: Job
}

interface InternalGraph {
    /**
     * For each index, the (name to id of) children of the node at that index.
     */
    children: Array<Map<string, number>>

    /**
     * For each index, the name of the node at that index.
     */
    nodeNames: Array<string>

    /**
     * For each index, the value of the node at that index.
     */
    values: Array<bigint>
}

export interface StackGraph {
    /**
     * For each index, the children of the node at that index.
     * Children are stored in alphabetical order by node name.
     */
    children: Array<Array<number>>

    /**
     * For each index, the name of the node at that index.
     */
    nodeNames: Array<string>

    /**
     * For each index, the value of the node at that index.
     */
    values: BigInt64Array
}

export interface WorkerResult {
    graph: StackGraph
}

export interface WorkerSuccess {
    result: WorkerResult
}

export interface WorkerFailure {
    error: {
        message: string
        stack?: string
    }
}

export type WorkerResponse = WorkerSuccess | WorkerFailure

const finalize = (graph: InternalGraph): StackGraph => {
    const sortedChildren = new Array(graph.children.length)
    for (let i = 0; i < graph.children.length; i++) {
        const childMap = graph.children[i]!
        let result: Array<number>
        if (childMap.size === 0) {
            result = []
        } else if (childMap.size === 1) {
            const childId = childMap.values().next().value!
            result = [childId]
        } else {
            const ids = Array.from(childMap.values())
            ids.sort((a, b) => {
                const nameA = graph.nodeNames[a]!
                const nameB = graph.nodeNames[b]!
                return nameA < nameB ? -1 : 1
            })
            result = ids
        }
        sortedChildren[i] = result
    }

    return {
        children: sortedChildren,
        nodeNames: graph.nodeNames,
        values: new BigInt64Array(graph.values),
    }
}

const getOrCreateChild = (
    parent: number,
    name: string,
    graph: InternalGraph,
): number => {
    const children = graph.children[parent]!
    let self = children.get(name)
    if (self === undefined) {
        self = graph.nodeNames.length
        graph.nodeNames.push(name)
        graph.children.push(new Map())
        graph.values.push(0n)
        children.set(name, self)
    }
    return self
}

const parseLine = (
    offset: number,
    parent: number,
    line: string,
    graph: InternalGraph,
    nameCache: Map<string, string>,
): bigint => {
    let current = offset
    while (
        current < line.length &&
        !(
            line[current] == ";" ||
            (line[current] == " " && line.indexOf(" ", current + 1) == -1)
        )
    ) {
        current = current + 1
    }

    let name = line.substring(offset, current)
    const cachedName = nameCache.get(name)
    if (cachedName != undefined) {
        // To save memory, use the cached name instance
        name = cachedName
    } else {
        nameCache.set(name, name)
    }

    const self = getOrCreateChild(parent, name, graph)

    if (line[current] == ";") {
        const value = parseLine(current + 1, self, line, graph, nameCache)
        graph.values[self]! += value
        return value
    } else if (line[current] == " ") {
        const value = BigInt(line.substring(current + 1))
        graph.values[self]! += value
        return value
    }

    throw new Error("Unexpected end of line: " + line)
}

const processStream = async (
    stream: ReadableStream<Uint8Array>,
): Promise<WorkerResult> => {
    const root = 0
    const reader = stream.getReader()
    const decoder = new TextDecoder()
    const DECODE_CHUNK_SIZE = 1024 * 1024 * 4 // 4MB chunks

    let graph: InternalGraph = {
        children: [new Map()],
        nodeNames: ["root"],
        values: [0n],
    }
    const nameCache = new Map<string, string>()
    let incompleteLine = ""

    while (true) {
        const { done, value } = await reader.read()
        if (done) {
            // Process any remaining text in the buffer
            if (incompleteLine) {
                graph.values[root]! += parseLine(
                    0,
                    root,
                    incompleteLine,
                    graph,
                    nameCache,
                )
            }
            break
        }

        for (
            let offset = 0;
            offset < value.length;
            offset += DECODE_CHUNK_SIZE
        ) {
            const sub = value.subarray(offset, offset + DECODE_CHUNK_SIZE)
            const chunk = decoder.decode(sub, { stream: true })
            const lines = (incompleteLine + chunk).split(/\r?\n/)
            incompleteLine = lines.pop() || ""

            for (const line of lines) {
                if (line) {
                    graph.values[root]! += parseLine(
                        0,
                        root,
                        line,
                        graph,
                        nameCache,
                    )
                }
            }
        }
    }

    return { graph: finalize(graph) }
}

const mergeChildren = async (job: MergeChildrenJob): Promise<WorkerResult> => {
    const graph = job.graph
    const rootNodesList: number[] = []
    for (let i = 0; i < graph.nodeNames.length; i++) {
        if (graph.nodeNames[i] === job.nodeName) {
            rootNodesList.push(i)
        }
    }
    if (rootNodesList.length == 0) {
        throw new Error(`No nodes with name: ${job.nodeName}`)
    }
    const rootNodes = new Set(rootNodesList)
    const newGraph: InternalGraph = {
        children: [new Map()],
        nodeNames: [job.nodeName],
        values: [0n],
    }

    // Create an aggregate view of all children of all root nodes
    // merging them into a single graph. If we encounter `job.nodeName`
    // in any of the sub-trees, skip them, as they are already represented
    // as part of another sub-tree.
    for (const rootNode of rootNodes) {
        const stack: number[] = []
        const parentStack: number[] = []

        for (const childId of graph.children[rootNode]!.values()) {
            stack.push(childId)
            parentStack.push(0)
        }

        while (stack.length > 0) {
            const current = stack.pop()!
            const parent = parentStack.pop()!

            if (rootNodes.has(current)) {
                // Skip sub-trees with the same name as the root node,
                // as they are already represented in another sub-tree
                continue
            }

            const currentInNewGraph = getOrCreateChild(
                parent,
                graph.nodeNames[current]!,
                newGraph,
            )
            newGraph.values[currentInNewGraph]! += graph.values[current]!
            for (const childId of graph.children[current]!.values()) {
                stack.push(childId)
                parentStack.push(currentInNewGraph)
            }
        }
    }

    for (const rootNode of rootNodes) {
        newGraph.values[0]! += graph.values[rootNode]!
    }

    return { graph: finalize(newGraph) }
}

const icicleGraph = async (job: IcicleGraphJob): Promise<WorkerResult> => {
    const graph = job.graph
    const targetNodeId = job.nodeId

    const parents = new Int32Array(graph.nodeNames.length).fill(-1)
    for (let i = 0; i < graph.children.length; i++) {
        for (const childId of graph.children[i]!.values()) {
            parents[childId] = i
        }
    }

    const newGraph: InternalGraph = {
        children: [new Map()],
        nodeNames: ["root"],
        values: [0n],
    }

    const stack = [targetNodeId]

    while (stack.length > 0) {
        const nodeId = stack.pop()!
        const value = graph.values[nodeId]!

        let childrenSum = 0n
        for (const childId of graph.children[nodeId]!.values()) {
            childrenSum += graph.values[childId]!
            stack.push(childId)
        }

        const selfWeight = value - childrenSum
        if (selfWeight > 0n) {
            let newCurrent = 0
            newGraph.values[0]! += selfWeight

            let originalCurrent = nodeId
            while (true) {
                const name = graph.nodeNames[originalCurrent]!
                newCurrent = getOrCreateChild(newCurrent, name, newGraph)
                newGraph.values[newCurrent]! += selfWeight

                originalCurrent = parents[originalCurrent]!

                if (originalCurrent === targetNodeId) {
                    break
                }
            }
        }
    }

    return { graph: finalize(newGraph) }
}

const process = async (job: Job): Promise<WorkerResult> => {
    if (job.type == "parseStream") {
        return await processStream(job.stream)
    } else if (job.type == "mergeChildren") {
        return await mergeChildren(job)
    } else if (job.type == "icicleGraph") {
        return await icicleGraph(job)
    } else if (job.type == "parseEncodedData") {
        const stream = decodeAndDecompressData(job.encodedData)
        return await processStream(stream)
    }

    throw new Error("Unknown job type")
}

const tryProcess = async (job: Job): Promise<WorkerResponse> => {
    try {
        const result = await process(job)
        return { result }
    } catch (error) {
        const message = error instanceof Error ? error.message : "Unknown error"
        const stack = error instanceof Error ? error.stack : undefined
        return {
            error: { message, stack },
        }
    }
}

self.onmessage = async (event: MessageEvent<WorkerParams>) => {
    const response = await tryProcess(event.data.job)
    const transfer: Transferable[] = []
    if (
        "result" in response &&
        response.result.graph.values instanceof BigInt64Array
    ) {
        transfer.push(response.result.graph.values.buffer)
    }
    self.postMessage(response, transfer)
}
