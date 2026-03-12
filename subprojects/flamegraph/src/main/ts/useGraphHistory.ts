import { useCallback } from "react"
import { COORDINATE_WIDTH } from "./FlamegraphNode"
import type { GraphState } from "./useGraphTabs"

export function useGraphHistory(
    updateGraphState: (
        id: string,
        updater: (prev: GraphState) => GraphState,
    ) => void,
) {
    const setRootNode = useCallback(
        (tabId: string, nodeId: number) => {
            updateGraphState(tabId, (gs) => {
                const newHistory = gs.history.slice(0, gs.historyIndex + 1)
                newHistory.push({
                    rootNode: nodeId,
                    viewLeft: 0,
                    viewRight: COORDINATE_WIDTH,
                })
                return {
                    ...gs,
                    history: newHistory,
                    historyIndex: newHistory.length - 1,
                }
            })
        },
        [updateGraphState],
    )

    const updateZoom = useCallback(
        (tabId: string, left: number, right: number) => {
            updateGraphState(tabId, (gs) => {
                const newHistory = [...gs.history]
                newHistory[gs.historyIndex] = {
                    ...newHistory[gs.historyIndex]!,
                    viewLeft: left,
                    viewRight: right,
                }
                return { ...gs, history: newHistory }
            })
        },
        [updateGraphState],
    )

    const goBack = useCallback(
        (tabId: string) => {
            updateGraphState(tabId, (gs) => ({
                ...gs,
                historyIndex: Math.max(0, gs.historyIndex - 1),
            }))
        },
        [updateGraphState],
    )

    const goForward = useCallback(
        (tabId: string) => {
            updateGraphState(tabId, (gs) => ({
                ...gs,
                historyIndex: Math.min(
                    gs.history.length - 1,
                    gs.historyIndex + 1,
                ),
            }))
        },
        [updateGraphState],
    )

    return { setRootNode, updateZoom, goBack, goForward }
}
