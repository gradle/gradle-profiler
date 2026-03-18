import { decodeBase64 } from "./encoding.ts"
import init, {
    StacksParser,
    wasm_delete_node,
    wasm_icicle_graph,
    wasm_merge_children,
    wasm_simplify_graph,
    WasmStackGraph,
} from "@flamegraph-wasm"
import type { StackGraph } from "./stackGraph"
import { nodeCount } from "./stackGraph"

let resolveWasmReady: () => void
const wasmReady = new Promise<void>((resolve) => {
    resolveWasmReady = resolve
})

export interface ParseEncodedDataJob {
    encodedData: string
    type: "parseEncodedData"
}

export interface InitWorkerJob {
    module: WebAssembly.Module
    type: "initWasm"
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

export interface SimplifyGraphJob {
    nodeId: number
    graph: StackGraph
    type: "simplifyGraph"
}

export type Job =
    | InitWorkerJob
    | ParseStreamJob
    | ParseEncodedDataJob
    | MergeChildrenJob
    | IcicleGraphJob
    | DeleteNodeJob
    | SimplifyGraphJob

export interface WorkerParams {
    job: Job
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

/**
 * Extract typed arrays from a WasmStackGraph and free the Rust-owned memory.
 * Each method call allocates a new JS typed array and copies data out of WASM
 * linear memory into it. These types arrays can then be zero-copy transferred to
 * the DOM thread.
 */
const wasmGraphToStackGraph = (wg: WasmStackGraph): StackGraph => {
    const childrenOffsets = wg.children_offsets()
    const childrenData = wg.children_data()
    const namesData = wg.names_data()
    const namesOffsets = wg.names_offsets()
    const displayNamesData = wg.display_names_data()
    const displayNamesOffsets = wg.display_names_offsets()
    const values = wg.values()
    wg.free()
    return {
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        displayNamesData,
        displayNamesOffsets,
        values,
    }
}

const processStream = async (
    stream: ReadableStream<Uint8Array>,
): Promise<WorkerResult> => {
    const parser = new StacksParser("root")
    const reader = stream.getReader()
    while (true) {
        const { done, value } = await reader.read()
        if (value) {
            parser.feed(value)
        }
        if (done) {
            break
        }
    }
    const graph = parser.finish()
    return { graph: wasmGraphToStackGraph(graph) }
}

const mergeChildren = (job: MergeChildrenJob): WorkerResult => {
    const { childrenOffsets, childrenData, namesData, namesOffsets, values } =
        job.graph
    const wasmGraph = wasm_merge_children(
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        values,
        job.nodeName,
    )
    return { graph: wasmGraphToStackGraph(wasmGraph) }
}

const icicleGraph = (job: IcicleGraphJob): WorkerResult => {
    const { childrenOffsets, childrenData, namesData, namesOffsets, values } =
        job.graph
    const wasmGraph = wasm_icicle_graph(
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        values,
        job.nodeId,
        nodeCount(job.graph),
    )
    return { graph: wasmGraphToStackGraph(wasmGraph) }
}

const parseEncodedData = async (
    job: ParseEncodedDataJob,
): Promise<WorkerResult> => {
    const CHUNK_SIZE_BYTES = 1024 * 1024
    const encoded = decodeBase64(job.encodedData)

    let position = 0
    const stream = new ReadableStream<Uint8Array>({
        pull(controller) {
            if (position >= encoded.length) {
                controller.close()
                return
            }
            const end = Math.min(position + CHUNK_SIZE_BYTES, encoded.length)
            controller.enqueue(encoded.subarray(position, end))
            position = end
        },
    }).pipeThrough(
        // DecompressionStream.writable is typed as WritableStream<BufferSource>; cast is safe
        // because we always feed Uint8Array chunks.
        new DecompressionStream("deflate-raw") as unknown as TransformStream<
            Uint8Array,
            Uint8Array
        >,
    )

    return await processStream(stream)
}

const simplifyGraph = (job: SimplifyGraphJob): WorkerResult => {
    const { childrenOffsets, childrenData, namesData, namesOffsets, values } =
        job.graph
    const wasmGraph = wasm_simplify_graph(
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        values,
        job.nodeId,
    )
    return { graph: wasmGraphToStackGraph(wasmGraph) }
}

const deleteNode = (job: DeleteNodeJob): WorkerResult => {
    const {
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        values,
        displayNamesData,
        displayNamesOffsets,
    } = job.graph
    const wasmGraph = wasm_delete_node(
        childrenOffsets,
        childrenData,
        namesData,
        namesOffsets,
        values,
        displayNamesData,
        displayNamesOffsets,
        job.nodeId,
    )
    return { graph: wasmGraphToStackGraph(wasmGraph) }
}

const process = async (job: Job): Promise<WorkerResult> => {
    if (job.type == "parseStream") {
        return await processStream(job.stream)
    } else if (job.type == "mergeChildren") {
        return mergeChildren(job)
    } else if (job.type == "icicleGraph") {
        return icicleGraph(job)
    } else if (job.type == "parseEncodedData") {
        return await parseEncodedData(job)
    } else if (job.type == "deleteNode") {
        return deleteNode(job)
    } else if (job.type == "simplifyGraph") {
        return simplifyGraph(job)
    }

    throw new Error("Unknown job type")
}

const tryProcess = async (job: Job): Promise<WorkerResponse> => {
    try {
        const result = await process(job)
        return { result }
    } catch (error) {
        const message =
            error instanceof Error
                ? error.message
                : typeof error === "string"
                  ? error
                  : "Unknown error"
        const stack = error instanceof Error ? error.stack : undefined
        return {
            error: { message, stack },
        }
    }
}

self.onmessage = async (event: MessageEvent<WorkerParams>) => {
    if (event.data.job.type == "initWasm") {
        try {
            await init({ module_or_path: event.data.job.module })
            resolveWasmReady()
        } catch (error) {
            console.error("Failed to initialize worker: " + error)
        }
        return
    }

    await wasmReady

    const response = await tryProcess(event.data.job)
    if ("result" in response) {
        const g = response.result.graph
        self.postMessage(response, [
            g.childrenOffsets.buffer,
            g.childrenData.buffer,
            g.namesData.buffer,
            g.namesOffsets.buffer,
            g.displayNamesData.buffer,
            g.displayNamesOffsets.buffer,
            g.values.buffer,
        ])
    } else {
        self.postMessage(response, [])
    }
}
