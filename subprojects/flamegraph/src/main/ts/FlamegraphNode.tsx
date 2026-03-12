import React, { createContext, forwardRef, useMemo } from "react"
import type { StackGraph } from "./worker"
import { colorFor, ColorContext } from "./color"

// --- Constants ---

/** Nodes with pixel width below this threshold will not be rendered. */
export const CULLING_THRESHOLD_PX = 5

/** The number of consecutive nodes with the same width required to make a node collapsible. */
export const COLLAPSE_THRESHOLD = 2

/** The height of each node, in pixels. */
export const NODE_HEIGHT = 22

/** The horizontal width of the SVG coordinate system. */
export const COORDINATE_WIDTH = 100_000

/** The space between the left edge of a node and its text, in pixels. */
const NODE_TEXT_PADDING_LEFT = 5

/** The size of the collapse/expand button icon, in pixels. */
const COLLAPSE_BUTTON_SIZE = 12

export interface CollapseContextType {
    expandedNodes: Set<number>
    toggleExpand: (nodeId: number) => void
}

export const CollapseContext = createContext<CollapseContextType>({
    expandedNodes: new Set(),
    toggleExpand: () => {},
})

export const getSameWidthChain = (
    nodeId: number,
    graph: StackGraph,
): number[] => {
    const chain = []
    let currentId = nodeId
    while (true) {
        const children = graph.children[currentId]
        if (children && children.length === 1) {
            const childId = children[0]!
            if (graph.values[childId] === graph.values[nodeId]) {
                chain.push(childId)
                currentId = childId
                continue
            }
        }
        break
    }
    return chain
}

export const nodeDetails = (nodeId: number, graph: StackGraph): string => {
    const name = graph.nodeNames[nodeId]
    const value = graph.values[nodeId]
    if (value == undefined || name == undefined) {
        return "Malformed graph. Missing node data."
    }
    return `${name} ${BigInt.asIntN(64, value).toLocaleString()} samples`
}

export const NodeDetails = forwardRef<HTMLSpanElement, {}>((_, ref) => {
    return (
        <span
            ref={ref}
            style={{
                position: "absolute",
                bottom: 0,
                left: 0,
                maxWidth: "calc(100% - 20px)",
                width: "fit-content",
                background: "rgba(0, 0, 0, 0.8)",
                padding: "0 8px",
                pointerEvents: "none",
                zIndex: 2,
                minHeight: NODE_HEIGHT,
                lineHeight: NODE_HEIGHT + "px",
                display: "flex",
                alignItems: "center",
                wordBreak: "break-all",
                overflowWrap: "anywhere",
            }}
        >
            Hover for details, click to zoom
        </span>
    )
})

export interface GraphContextType {
    graph: StackGraph
}

export const GraphContext = React.createContext<GraphContextType | null>(null)

export const FlamegraphNode = React.memo(({
    nodeId,
    xOffset,
    depth,
    svgWidth,
    svgHeight,
    totalValue,
    onClick,
    parentValue,
    zoomWidth = COORDINATE_WIDTH,
}: {
    nodeId: number
    xOffset: bigint
    depth: number
    svgWidth: number
    svgHeight: number
    totalValue: bigint
    onClick: (nodeId: number) => void
    parentValue: bigint | null
    zoomWidth?: number
}) => {
    const context = React.useContext(GraphContext)
    if (!context) {
        throw new Error("FlamegraphNode must be used within a GraphContext")
    }
    const { graph } = context

    const value = graph.values[nodeId]
    if (value == undefined) {
        throw new Error("Malformed graph: missing node value")
    }

    const name = graph.nodeNames[nodeId]
    if (name == undefined) {
        throw new Error("Malformed graph: missing node name")
    }

    const { expandedNodes, toggleExpand } = React.useContext(CollapseContext)
    const sameWidthChain = useMemo(
        () => getSameWidthChain(nodeId, graph),
        [nodeId, graph],
    )
    const isCollapsible =
        sameWidthChain.length >= COLLAPSE_THRESHOLD && value !== parentValue
    const isCollapsed = isCollapsible && !expandedNodes.has(nodeId)

    const children = isCollapsed
        ? graph.children[sameWidthChain[sameWidthChain.length - 1]!]
        : graph.children[nodeId]
    if (children == undefined) {
        throw new Error("Malformed graph: missing children map")
    }

    // Recursively render children
    let childXOffset = xOffset
    const childElements = children.map((childId) => {
        const childValue = graph.values[childId]
        if (childValue == null) {
            throw new Error("Malformed graph: missing child value")
        }

        const childWidthPx =
            (Number(childValue) / Number(totalValue)) *
            COORDINATE_WIDTH *
            (svgWidth / zoomWidth)
        let element
        if (childWidthPx >= CULLING_THRESHOLD_PX) {
            element = (
                <FlamegraphNode
                    key={childId}
                    nodeId={childId}
                    xOffset={childXOffset}
                    depth={depth + 1}
                    totalValue={totalValue}
                    svgWidth={svgWidth}
                    svgHeight={svgHeight}
                    onClick={onClick}
                    parentValue={value}
                    zoomWidth={zoomWidth}
                />
            )
        } else {
            // Do not render nodes that are too small.
            element = null
        }

        childXOffset += childValue
        return element
    })

    const y = svgHeight - (depth + 1) * NODE_HEIGHT

    const rectX = (Number(xOffset) / Number(totalValue)) * COORDINATE_WIDTH
    const rectWidth = (Number(value) / Number(totalValue)) * COORDINATE_WIDTH

    // To counteract the text stretching caused by preserveAspectRatio="none", we apply an inverse
    // horizontal scale transform.
    const horizontalScale = zoomWidth / svgWidth

    const colorContext = React.useContext(ColorContext)
    const showCollapseButton =
        isCollapsible && rectWidth >= COLLAPSE_BUTTON_SIZE * 2 * horizontalScale

    return (
        <>
            <svg
                x={rectX}
                y={y}
                width={rectWidth}
                height={NODE_HEIGHT}
                onClick={(e) => {
                    e.stopPropagation() // Prevent click from bubbling to parent nodes
                    onClick(nodeId)
                }}
                style={{ cursor: "pointer" }}
            >
                <rect
                    data-node-id={nodeId}
                    data-name={name}
                    width={rectWidth}
                    height={NODE_HEIGHT}
                    fill={colorFor(name, colorContext)}
                >
                    <title>{nodeDetails(nodeId, graph)}</title>
                </rect>

                {showCollapseButton && (
                    <g
                        onClick={(e) => {
                            e.stopPropagation()
                            toggleExpand(nodeId)
                        }}
                    >
                        <rect
                            x={(COLLAPSE_BUTTON_SIZE / 2) * horizontalScale}
                            y={(NODE_HEIGHT - COLLAPSE_BUTTON_SIZE) / 2}
                            width={COLLAPSE_BUTTON_SIZE * horizontalScale}
                            height={COLLAPSE_BUTTON_SIZE}
                            fill="rgba(0,0,0,0)"
                            stroke="black"
                            strokeWidth={1}
                            vectorEffect="non-scaling-stroke"
                        />
                        <g
                            transform={`translate(${COLLAPSE_BUTTON_SIZE * horizontalScale}, ${NODE_HEIGHT / 2})`}
                        >
                            <text
                                dy=".35em"
                                textAnchor="middle"
                                fill="black"
                                transform={`scale(${horizontalScale}, 1)`}
                                style={{
                                    pointerEvents: "none",
                                    fontSize: `${COLLAPSE_BUTTON_SIZE}px`,
                                    fontWeight: "bold",
                                }}
                            >
                                {!isCollapsed ? "-" : "+"}
                            </text>
                        </g>
                    </g>
                )}

                {(rectWidth / zoomWidth) * svgWidth >
                    (showCollapseButton
                        ? COLLAPSE_BUTTON_SIZE * 2
                        : NODE_TEXT_PADDING_LEFT) && (
                    <text
                        x={
                            showCollapseButton
                                ? COLLAPSE_BUTTON_SIZE * 2
                                : NODE_TEXT_PADDING_LEFT
                        }
                        y={NODE_HEIGHT / 2}
                        dy=".35em"
                        transform={`scale(${horizontalScale}, 1)`}
                        style={{ pointerEvents: "none" }}
                    >
                        {graph.displayNames[nodeId] ?? name}
                    </text>
                )}
            </svg>
            {childElements}
        </>
    )
})
