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

export interface DeleteNodeJob {
    nodeId: number
    graph: StackGraph
    type: "deleteNode"
}

export type Job =
    | ParseStreamJob
    | ParseEncodedDataJob
    | MergeChildrenJob
    | IcicleGraphJob
    | DeleteNodeJob

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

    /**
     * For each index, a shortened display name for the node.
     * Parameters and line numbers are omitted unless needed for disambiguation.
     */
    displayNames: Array<string>
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

interface ParsedName {
    simpleClass: string
    method: string
    rawParams: string
    lineNumber: string | null
}

const parseMethodName = (name: string): ParsedName | null => {
    const parenOpen = name.indexOf("(")

    if (parenOpen !== -1) {
        // Format: full.class.name.method(params):line  (macOS async-profiler / JVM)
        const parenClose = name.indexOf(")", parenOpen)
        if (parenClose === -1) return null

        const beforeParen = name.substring(0, parenOpen)
        const lastDot = beforeParen.lastIndexOf(".")
        if (lastDot === -1) return null

        const classPath = beforeParen.substring(0, lastDot)
        if (!classPath) return null

        const method = beforeParen.substring(lastDot + 1)
        if (!method) return null

        const lastSep = Math.max(classPath.lastIndexOf("."), classPath.lastIndexOf("/"))
        const simpleClass =
            lastSep !== -1 ? classPath.substring(lastSep + 1) : classPath
        if (!simpleClass) return null

        const rawParams = name.substring(parenOpen + 1, parenClose)

        let lineNumber: string | null = null
        const rest = name.substring(parenClose + 1)
        const colonMatch = rest.match(/^:(\d+)/)
        if (colonMatch) {
            lineNumber = colonMatch[1]!
        }

        return { simpleClass, method, rawParams, lineNumber }
    }

    // Format: pkg/path/ClassName.method_[bci]  (Linux async-profiler)
    // Packages are separated by '/' and the method may have a _[N] BCI suffix.
    const lastSlash = name.lastIndexOf("/")
    const lastPart =
        lastSlash !== -1 ? name.substring(lastSlash + 1) : name

    const dotIdx = lastPart.indexOf(".")
    if (dotIdx === -1) return null

    const simpleClass = lastPart.substring(0, dotIdx)
    if (!simpleClass) return null

    let methodPart = lastPart.substring(dotIdx + 1)
    if (!methodPart) return null

    // Strip _[bci] suffix, e.g. _[0], _[j]
    let lineNumber: string | null = null
    const bciMatch = methodPart.match(/_\[(\w+)\]$/)
    if (bciMatch) {
        lineNumber = bciMatch[1]!
        methodPart = methodPart.substring(0, methodPart.length - bciMatch[0].length)
    }

    if (!methodPart) return null

    return { simpleClass, method: methodPart, rawParams: "", lineNumber }
}

const simplifyParam = (param: string): string => {
    const trimmed = param.trim()
    if (!trimmed) return trimmed

    // Split base type from array brackets or varargs suffix
    const bracketIdx = trimmed.indexOf("[")
    const varargIdx = trimmed.endsWith("...") ? trimmed.length - 3 : -1
    const suffixStart =
        bracketIdx !== -1 ? bracketIdx : varargIdx !== -1 ? varargIdx : -1

    const base = suffixStart !== -1 ? trimmed.substring(0, suffixStart) : trimmed
    const suffix = suffixStart !== -1 ? trimmed.substring(suffixStart) : ""

    const lastSep = Math.max(base.lastIndexOf("."), base.lastIndexOf("/"))
    const simple = lastSep !== -1 ? base.substring(lastSep + 1) : base
    return simple + suffix
}

const simplifyParams = (rawParams: string): string => {
    if (!rawParams) return ""
    return rawParams
        .split(",")
        .map(simplifyParam)
        .join(", ")
}

const computeDisplayNames = (nodeNames: Array<string>): Array<string> => {
    const parsed = nodeNames.map(parseMethodName)

    // For each method name, collect distinct rawParams values
    const methodToParams = new Map<string, Set<string>>()
    for (const p of parsed) {
        if (!p) continue
        if (!methodToParams.has(p.method)) {
            methodToParams.set(p.method, new Set())
        }
        methodToParams.get(p.method)!.add(p.rawParams)
    }

    // For each method+params combo, collect distinct line numbers
    const methodParamsToLines = new Map<string, Set<string | null>>()
    for (const p of parsed) {
        if (!p) continue
        const key = `${p.method}|${p.rawParams}`
        if (!methodParamsToLines.has(key)) {
            methodParamsToLines.set(key, new Set())
        }
        methodParamsToLines.get(key)!.add(p.lineNumber)
    }

    return nodeNames.map((name, i) => {
        const p = parsed[i]
        if (!p) return name

        const { simpleClass, method, rawParams, lineNumber } = p
        const base = `${simpleClass}.${method}`

        const paramsVariants = methodToParams.get(method)!
        if (paramsVariants.size <= 1) {
            return base
        }

        const simplifiedParams = simplifyParams(rawParams)
        const lineVariants = methodParamsToLines.get(`${method}|${rawParams}`)!
        if (lineVariants.size <= 1 || !lineNumber) {
            return `${base}(${simplifiedParams})`
        }

        return `${base}(${simplifiedParams}):${lineNumber}`
    })
}

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
        displayNames: computeDisplayNames(graph.nodeNames),
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
    line: string,
    graph: InternalGraph,
    nameCache: Map<string, string>,
): bigint => {
    const lastSpace = line.lastIndexOf(" ")
    if (lastSpace === -1) throw new Error("Unexpected end of line: " + line)

    let value = 0n
    for (let i = lastSpace + 1; i < line.length; i++) {
        value = value * 10n + BigInt(line.charCodeAt(i) - 48)
    }

    let parent = 0 // root
    let start = 0

    while (start < lastSpace) {
        const semi = line.indexOf(";", start)
        const end = semi === -1 || semi > lastSpace ? lastSpace : semi

        let name = line.substring(start, end)
        const cached = nameCache.get(name)
        if (cached !== undefined) {
            name = cached
        } else {
            nameCache.set(name, name)
        }

        const self = getOrCreateChild(parent, name, graph)
        graph.values[self]! += value
        parent = self

        if (semi === -1 || semi >= lastSpace) break
        start = semi + 1
    }

    return value
}

const processStream = async (
    stream: ReadableStream<Uint8Array>,
): Promise<WorkerResult> => {
    const graph: InternalGraph = {
        children: [new Map()],
        nodeNames: ["root"],
        values: [0n],
    }
    const nameCache = new Map<string, string>()
    const decoder = new TextDecoder()
    const reader = stream.getReader()

    // Leftover bytes from the previous chunk whose line wasn't terminated yet.
    // Allocated at most once per reader.read() call (only when a line spans a
    // chunk boundary), so far less frequent than the old per-line allocations.
    let pending: Uint8Array | null = null

    while (true) {
        const { done, value } = await reader.read()

        let data: Uint8Array
        if (pending !== null) {
            if (value !== undefined && value.length > 0) {
                const combined = new Uint8Array(pending.length + value.length)
                combined.set(pending)
                combined.set(value, pending.length)
                data = combined
            } else {
                data = pending
            }
            pending = null
        } else {
            data = value ?? new Uint8Array(0)
        }

        let lineStart = 0
        for (let i = 0; i < data.length; i++) {
            if (data[i] === 0x0a) {
                // Strip optional \r before \n
                const lineEnd =
                    i > lineStart && data[i - 1] === 0x0d ? i - 1 : i
                if (lineEnd > lineStart) {
                    const line = decoder.decode(data.subarray(lineStart, lineEnd))
                    graph.values[0]! += parseLine(line, graph, nameCache)
                }
                lineStart = i + 1
            }
        }

        if (done) {
            if (lineStart < data.length) {
                const line = decoder.decode(data.subarray(lineStart))
                graph.values[0]! += parseLine(line, graph, nameCache)
            }
            break
        }

        pending = lineStart < data.length ? data.slice(lineStart) : null
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

const deleteNodeFromGraph = (job: DeleteNodeJob): WorkerResult => {
    const { graph, nodeId } = job
    if (nodeId === 0) return { graph }

    // Build a parent map for the entire graph
    const parents = new Int32Array(graph.nodeNames.length).fill(-1)
    for (let i = 0; i < graph.children.length; i++) {
        for (const childId of graph.children[i]!) {
            parents[childId] = i
        }
    }

    const parentId = parents[nodeId]
    if (parentId == null || parentId === -1) return { graph }

    const deletedValue = graph.values[nodeId]!

    // Copy values and subtract the deleted node's weight from all ancestors
    const newValues = new BigInt64Array(graph.values)
    let curr = parentId
    while (curr !== -1) {
        newValues[curr] = newValues[curr]! - deletedValue
        curr = parents[curr]!
    }

    // Shallow-copy children array and splice out the deleted node from its parent
    const newChildren = graph.children.slice()
    newChildren[parentId] = newChildren[parentId]!.filter(
        (id) => id !== nodeId,
    )

    return {
        graph: {
            ...graph,
            values: newValues,
            children: newChildren,
        },
    }
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
    } else if (job.type == "deleteNode") {
        return deleteNodeFromGraph(job)
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
