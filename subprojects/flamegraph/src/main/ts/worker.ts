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
    values: Array<number>
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

const sortChildren = (graph: StackGraph) => {
    graph.children.forEach((children) => {
        children.sort((a, b) => {
            const nameA = graph.nodeNames[a]!
            const nameB = graph.nodeNames[b]!
            return nameA > nameB ? 1 : -1
        })
    })
}

const getOrCreateChild = (
    parent: number,
    child: string,
    graph: StackGraph,
): number => {
    let children = graph.children[parent]!
    let self = children.find((id) => graph.nodeNames[id] == child)
    if (self == undefined) {
        self = graph.nodeNames.length
        graph.nodeNames.push(child)
        graph.children.push([])
        graph.values.push(0)
        children.push(self)
    }
    return self
}

const parseLine = (
    offset: number,
    parent: number,
    line: string,
    graph: StackGraph,
    nameCache: Map<string, string>,
): number => {
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

    let self = getOrCreateChild(parent, name, graph)

    if (line[current] == ";") {
        const value = parseLine(current + 1, self, line, graph, nameCache)
        graph.values[self]! += value
        return value
    } else if (line[current] == " ") {
        const value = parseInt(line.substring(current + 1))
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

    let graph: StackGraph = {
        children: [[]],
        nodeNames: ["root"],
        values: [0],
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

        const chunk = decoder.decode(value, { stream: true })
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

    sortChildren(graph)
    return { graph }
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

    const newGraph: StackGraph = {
        children: [[]],
        nodeNames: [job.nodeName],
        values: [0],
    }

    // Create an aggregate view of all children of all root nodes
    // merging them into a single graph. If we encounter `job.nodeName`
    // in any of the sub-trees, skip them, as they are already represented
    // as part of another sub-tree.
    for (const rootNode of rootNodes) {
        const stack: number[] = []
        const parentStack: number[] = []

        for (const childIndex of graph.children[rootNode]!) {
            stack.push(childIndex)
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

            let currentInNewGraph = getOrCreateChild(
                parent,
                graph.nodeNames[current]!,
                newGraph,
            )
            newGraph.values[currentInNewGraph]! += graph.values[current]!
            for (const childIndex of graph.children[current]!) {
                stack.push(childIndex)
                parentStack.push(currentInNewGraph)
            }
        }
    }

    for (const rootNode of rootNodes) {
        newGraph.values[0]! += graph.values[rootNode]!
    }

    sortChildren(newGraph)
    return { graph: newGraph }
}

const icicleGraph = async (job: IcicleGraphJob): Promise<WorkerResult> => {
    const graph = job.graph
    const targetNodeId = job.nodeId

    const parents = new Int32Array(graph.nodeNames.length).fill(-1)
    for (let i = 0; i < graph.children.length; i++) {
        const children = graph.children[i]!
        for (const child of children) {
            parents[child] = i
        }
    }

    const newGraph: StackGraph = {
        children: [[]],
        nodeNames: ["root"],
        values: [0],
    }

    const stack = [targetNodeId]

    while (stack.length > 0) {
        const nodeId = stack.pop()!
        const value = graph.values[nodeId]!

        let childrenSum = 0
        for (const child of graph.children[nodeId]!) {
            childrenSum += graph.values[child]!
            stack.push(child)
        }

        const selfWeight = value - childrenSum
        if (selfWeight > 0) {
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

    sortChildren(newGraph)
    return { graph: newGraph }
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
            error: {
                message,
                stack,
            },
        }
    }
}

self.onmessage = async (event: MessageEvent<WorkerParams>) => {
    const response = await tryProcess(event.data.job)
    self.postMessage(response)
}
