import { useCallback, useState } from "react"
import type { Job, WorkerParams, WorkerResponse } from "./worker"
import DataWorker from "./worker?worker&inline"
import useWorkerPool from "./useWorkerPool.ts"
import { COORDINATE_WIDTH } from "./FlamegraphNode"
import { getGraph, removeGraph, storeGraph } from "./graphStore"

export interface HistoryEntry {
    rootNode: number
    viewLeft: number
    viewRight: number
}

export interface GraphState {
    graphId: string
    history: HistoryEntry[]
    historyIndex: number
    mutable?: boolean
}

export interface WorkerState {
    error?: {
        message: string
        stack?: string
    }
    progress?: string
    graph?: GraphState
}

export type RunJob = (
    id: string,
    params: WorkerParams,
    transfer?: Transferable[],
) => Promise<WorkerResponse>

export function useGraphTabs() {
    const runJob: RunJob = useWorkerPool<string, WorkerParams, WorkerResponse>(
        DataWorker,
    )

    const [selectedTab, setSelectedTab] = useState<string | null>(null)
    const [allTabData, setAllTabData] = useState<Map<string, WorkerState>>(
        new Map(),
    )

    const setTabData = useCallback((id: string, newData: WorkerState) => {
        setAllTabData((oldData) => {
            const updated = new Map(oldData)
            updated.set(id, newData)
            return updated
        })
        setSelectedTab((current) => current ?? id)
    }, [])

    const updateGraphState = useCallback(
        (id: string, updater: (prev: GraphState) => GraphState) => {
            setAllTabData((oldData) => {
                const tabState = oldData.get(id)
                if (!tabState?.graph) {
                    return oldData
                }
                const updated = new Map(oldData)
                updated.set(id, { ...tabState, graph: updater(tabState.graph) })
                return updated
            })
        },
        [],
    )

    const deleteTab = useCallback((id: string) => {
        setAllTabData((oldData) => {
            const tabState = oldData.get(id)
            if (tabState?.graph) {
                removeGraph(tabState.graph.graphId)
            }
            const updated = new Map(oldData)
            updated.delete(id)
            return updated
        })
        setSelectedTab((current) => (current === id ? null : current))
    }, [])

    const submitJob = useCallback(
        async (id: string, job: Job, transfer: Transferable[]) => {
            setTabData(id, { progress: "Crunching the numbers..." })
            const params: WorkerParams = { job }
            try {
                const result = await runJob(id, params, transfer)
                if ("error" in result) {
                    setTabData(id, { error: result.error })
                } else if ("result" in result) {
                    setTabData(id, {
                        graph: {
                            graphId: storeGraph(result.result.graph),
                            history: [
                                {
                                    rootNode: 0,
                                    viewLeft: 0,
                                    viewRight: COORDINATE_WIDTH,
                                },
                            ],
                            historyIndex: 0,
                        },
                    })
                }
            } catch (err) {
                setTabData(id, {
                    error: {
                        message:
                            err instanceof Error ? err.message : String(err),
                    },
                })
            }
        },
        [runJob, setTabData],
    )

    const showMergedSubgraph = useCallback(
        (graphId: string, tabId: string, nodeId: number) => {
            const graph = getGraph(graphId)
            if (!graph) return
            const nodeName = graph.nodeNames[nodeId]!
            const newTabId = `${tabId}:merge:${nodeName}`
            submitJob(
                newTabId,
                { type: "mergeChildren", nodeName, graph },
                [],
            )
            setSelectedTab(newTabId)
        },
        [submitJob],
    )

    const showIcicleGraph = useCallback(
        (graphId: string, tabId: string, nodeId: number) => {
            const graph = getGraph(graphId)
            if (!graph) return
            const nodeName = graph.nodeNames[nodeId]!
            const newTabId = `${tabId}:icicle:${nodeName}`
            submitJob(
                newTabId,
                { type: "icicleGraph", nodeId, graph },
                [],
            )
            setSelectedTab(newTabId)
        },
        [submitJob],
    )

    return {
        runJob,
        allTabData,
        selectedTab,
        setSelectedTab,
        updateGraphState,
        deleteTab,
        submitJob,
        showMergedSubgraph,
        showIcicleGraph,
    }
}
