export interface ParseStreamJob {
    stream: ReadableStream<Uint8Array>
    type: "parseStream"
}

export interface MergeChildrenJob {
    nodeName: string
    graph: StackGraph
    type: "mergeChildren"
}

export type Job = ParseStreamJob | MergeChildrenJob

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

    /**
     * Map from node name to set of node indices with that name.
     */
    nodesByName: Map<string, Set<number>>
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
        if (graph.nodesByName.has(child)) {
            graph.nodesByName.get(child)!.add(self)
        } else {
            graph.nodesByName.set(child, new Set([self]))
        }
        graph.children.push([])
        graph.values.push(0)

        // Insert in alphabetical order
        let insertIndex = children.findIndex(
            (id) => graph.nodeNames[id]! > child,
        )
        if (insertIndex == -1) {
            children.push(self)
        } else {
            children.splice(insertIndex, 0, self)
        }
    } else {
        graph.nodesByName.get(child)!.add(self)
    }
    return self
}

const parseLine = (
    offset: number,
    parent: number,
    line: string,
    graph: StackGraph,
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
    const nodesWithName = graph.nodesByName.get(name)
    if (nodesWithName != undefined) {
        // To save memory, use the name instance from the first node with this name
        const firstNodeWithName = nodesWithName.values().next().value!
        name = graph.nodeNames[firstNodeWithName]!
    }

    let self = getOrCreateChild(parent, name, graph)

    if (line[current] == ";") {
        const value = parseLine(current + 1, self, line, graph)
        graph.values[self]! += value
        return value
    } else if (line[current] == " ") {
        const value = parseInt(line.substring(current + 1))
        graph.values[self]! += value
        return value
    }

    throw new Error("Unexpected end of line")
}

const processStream = async (job: ParseStreamJob): Promise<WorkerResult> => {
    const root = 0

    const reader = job.stream.getReader()
    const decoder = new TextDecoder()

    let graph: StackGraph = {
        children: [[]],
        nodeNames: ["root"],
        nodesByName: new Map<string, Set<number>>(),
        values: [0],
    }
    let incompleteLine = ""

    while (true) {
        const { done, value } = await reader.read()
        if (done) {
            // Process any remaining text in the buffer
            if (incompleteLine) {
                graph.values[root]! += parseLine(0, root, incompleteLine, graph)
            }
            break
        }

        const chunk = decoder.decode(value, { stream: true })
        const lines = (incompleteLine + chunk).split(/\r?\n/)
        incompleteLine = lines.pop() || ""

        for (const line of lines) {
            if (line) {
                graph.values[root]! += parseLine(0, root, line, graph)
            }
        }
    }

    return { graph }
}

const mergeChildren = async (job: MergeChildrenJob): Promise<WorkerResult> => {
    const graph = job.graph

    const rootNodes = graph.nodesByName.get(job.nodeName)
    if (!rootNodes || rootNodes.size == 0) {
        throw new Error(`No nodes with name: ${job.nodeName}`)
    }

    const newGraph: StackGraph = {
        children: [[]],
        nodeNames: [job.nodeName],
        nodesByName: new Map<string, Set<number>>(),
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

    return { graph: newGraph }
}

const process = async (job: Job): Promise<WorkerResult> => {
    if (job.type == "parseStream") {
        return await processStream(job)
    } else if (job.type == "mergeChildren") {
        return await mergeChildren(job)
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
