import { useCallback } from "react"
import type { GraphState, RunJob } from "./useGraphTabs"
import { getGraph, removeGraph, storeGraph } from "./graphStore"

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
        async (tabId: string, graphId: string, nodeId: number) => {
            const graph = getGraph(graphId)
            if (!graph) return
            const result = await runJob(
                "deleteNode",
                {
                    job: {
                        type: "deleteNode",
                        nodeId,
                        graph: graph.toRaw(),
                    },
                },
                [],
            )
            if ("result" in result) {
                const newGraphId = storeGraph(result.result.graph)
                removeGraph(graphId)
                updateGraphState(tabId, (gs) => ({
                    ...gs,
                    graphId: newGraphId,
                }))
            }
        },
        [updateGraphState, runJob],
    )

    return { setMutable, deleteNode }
}
