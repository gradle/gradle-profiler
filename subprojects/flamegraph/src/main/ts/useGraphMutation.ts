import { useCallback } from "react"
import type { GraphState, RunJob } from "./useGraphTabs"

export function useGraphMutation(
    updateGraphState: (
        id: string,
        updater: (prev: GraphState) => GraphState,
    ) => void,
    runJob: RunJob,
) {
    const setMutable = useCallback(
        (tabId: string, mutable: boolean) => {
            updateGraphState(tabId, (gs) => ({ ...gs, mutable }))
        },
        [updateGraphState],
    )

    const deleteNode = useCallback(
        async (tabId: string, graphState: GraphState, nodeId: number) => {
            // graph.values is a BigInt64Array backed by a shared ArrayBuffer.
            // Transferring an ArrayBuffer to a Worker via postMessage detaches
            // it, making the original inaccessible. We clone the buffer first
            // so the worker gets its own copy to transfer while the UI keeps
            // the live buffer untouched.
            const valuesBuffer = graphState.graph.values.buffer.slice(0)
            const result = await runJob(
                "deleteNode",
                {
                    job: {
                        type: "deleteNode",
                        nodeId,
                        graph: graphState.graph,
                    },
                },
                [valuesBuffer],
            )
            if ("result" in result) {
                updateGraphState(tabId, (gs) => ({
                    ...gs,
                    graph: result.result.graph,
                }))
            }
        },
        [updateGraphState, runJob],
    )

    return { setMutable, deleteNode }
}
