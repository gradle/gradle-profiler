import React, { useEffect, useMemo, useRef } from "react"
import { Row, Stack } from "./containers"
import { getGraph } from "./graphStore"
import { TopKHeap } from "./topKHeap"

export const SearchPanel: React.FC<{
    graphId: string | null | undefined
    /** Only return nodes that are descendants of this node (inclusive). */
    rootNode: number
    searchQuery: string
    onSearchQueryChange: (query: string) => void
    onSelectNode: (nodeId: number) => void
}> = ({
    graphId,
    rootNode,
    searchQuery,
    onSearchQueryChange,
    onSelectNode,
}) => {
    const inputRef = useRef<HTMLInputElement | null>(null)

    useEffect(() => {
        inputRef.current?.focus()
    }, [])

    const graph = graphId != null ? (getGraph(graphId) ?? null) : null

    // When the user has drilled into a subtree, restrict search to that subtree.
    // At the true root (node 0) we skip the traversal and search everything.
    const subtreeIds = useMemo(() => {
        if (!graph || rootNode === 0) return null
        const ids = new Set<number>()
        const stack = [rootNode]
        while (stack.length > 0) {
            const id = stack.pop()!
            ids.add(id)
            for (const c of graph.getChildren(id)) {
                stack.push(c)
            }
        }
        return ids
    }, [graph, rootNode])

    const results = useMemo(() => {
        if (!graph || !searchQuery) return []

        const queryLower = searchQuery.toLowerCase()
        const heap = new TopKHeap<{
            nodeId: number
            name: string
            value: bigint
        }>(250)

        for (let i = 0; i < graph.nodeCount; i++) {
            if (subtreeIds !== null && !subtreeIds.has(i)) {
                continue
            }
            const value = graph.values[i]
            if (value == null) {
                continue
            }
            if (!graph.getNodeNameLower(i).includes(queryLower)) {
                continue
            }
            heap.push(value, { nodeId: i, name: graph.getNodeName(i), value })
        }

        return heap.toSortedArray()
    }, [graph, subtreeIds, searchQuery])

    const maxValue = (graph && (graph.values[rootNode] ?? graph.values[0])) || 1n

    return (
        <Stack style={{ gap: 5, flex: 1, minHeight: 0 }}>
            <Row style={{ gap: 5, flexShrink: 0 }}>
                <input
                    ref={inputRef}
                    type="text"
                    value={searchQuery}
                    onChange={(e) => onSearchQueryChange(e.target.value)}
                    placeholder="Search nodes..."
                    style={{ flex: 1 }}
                />
                <button
                    onClick={() => onSearchQueryChange("")}
                    disabled={!searchQuery}
                >
                    &#x2715;
                </button>
            </Row>
            <Stack style={{ gap: 5, flex: 1, minHeight: 0, overflowY: "auto" }}>
                {results.map(({ nodeId, name, value }) => {
                    const percent = Number((value * 10000n) / maxValue) / 100
                    return (
                        <button
                            key={nodeId}
                            style={{
                                textAlign: "left",
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                                flexShrink: 0,
                                background: `linear-gradient(to right, rgba(30, 120, 255, 0.4) ${percent}%, rgba(0, 0, 0, 0.45) ${percent}%)`,
                            }}
                            title={name}
                            onClick={() => onSelectNode(nodeId)}
                        >
                            {name}
                        </button>
                    )
                })}
            </Stack>
        </Stack>
    )
}
