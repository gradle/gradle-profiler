export interface WorkerParams {
    stream: ReadableStream<Uint8Array>
}

export interface StackGraph {
    children: Array<Map<string, number>>
    nodeNames: string[]
    names: Map<string, string>
    values: Array<number>
}

export interface WorkerResult {
    graph: StackGraph
}

export interface WorkerResponse {
    result?: WorkerResult
    error?: {
        message: string
        stack?: string
    }
}

const intern = (value: string, graph: StackGraph) => {
    const existing = graph.names.get(value)
    if (existing != undefined) {
        return existing
    }
    graph.names.set(value, value)
    return value
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
    let dedupName = intern(name, graph)

    let existingChildren = graph.children[parent]!
    let self = existingChildren.get(dedupName)
    if (self == undefined) {
        self = graph.nodeNames.length
        graph.nodeNames.push(dedupName)
        graph.children.push(new Map())
        graph.values.push(0)
        existingChildren.set(dedupName, self)
    }

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

const process = async (params: WorkerParams): Promise<WorkerResult> => {
    const root = 0

    const reader = params.stream.getReader()
    const decoder = new TextDecoder()

    let graph: StackGraph = {
        children: [new Map()], // Start with root node
        nodeNames: ["root"],
        names: new Map<string, string>(),
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

const tryProcess = async (params: WorkerParams): Promise<WorkerResponse> => {
    try {
        const result = await process(params)
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
    const response = await tryProcess(event.data)
    self.postMessage(response)
}
