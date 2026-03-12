import React from "react"
import type { GraphState } from "./useGraphTabs"
import { Row } from "./containers.tsx"

export const GraphActions: React.FC<{
    tabId: string | null
    graphState: GraphState | null
    goBack: (tabId: string) => void
    goForward: (tabId: string) => void
    setRootNode: (tabId: string, nodeId: number) => void
    showMergedSubgraph: (gs: GraphState, tabId: string, nodeId: number) => void
    showIcicleGraph: (gs: GraphState, tabId: string, nodeId: number) => void
    setMutable: (tabId: string, mutable: boolean) => void
}> = ({
    tabId,
    graphState,
    goBack,
    goForward,
    setRootNode,
    showMergedSubgraph,
    showIcicleGraph,
    setMutable,
}) => {
    const historyEntry = graphState?.history[graphState.historyIndex]
    const rootNode = historyEntry?.rootNode ?? 0
    const canGoBack = graphState ? graphState.historyIndex > 0 : false
    const canGoForward = graphState
        ? graphState.historyIndex < graphState.history.length - 1
        : false
    const isMutable = graphState?.mutable ?? false

    return (
        <Row style={{ gap: 5 }}>
            <button
                onClick={() => tabId && goBack(tabId)}
                disabled={!tabId || !canGoBack}
            >
                &larr; Back
            </button>
            <button
                onClick={() => tabId && goForward(tabId)}
                disabled={!tabId || !canGoForward}
            >
                Forward &rarr;
            </button>
            <button
                onClick={() => tabId && setRootNode(tabId, 0)}
                disabled={!tabId || rootNode === 0}
            >
                Reset
            </button>
            <button
                onClick={() =>
                    tabId &&
                    graphState &&
                    showMergedSubgraph(graphState, tabId, rootNode)
                }
                disabled={!tabId || !graphState || rootNode === 0}
            >
                Merge
            </button>
            <button
                onClick={() =>
                    tabId &&
                    graphState &&
                    showIcicleGraph(graphState, tabId, rootNode)
                }
                disabled={!tabId || !graphState}
            >
                Icicle
            </button>
            <button
                onClick={() =>
                    tabId && setMutable(tabId, !isMutable)
                }
                disabled={!tabId}
            >
                {isMutable ? "Freeze" : "Mutate"}
            </button>
        </Row>
    )
}
