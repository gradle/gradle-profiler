import { forwardRef } from "react"
import type { StackGraph } from "./worker"
import { colorFor, type ColorSettings } from "./color"

// --- Constants ---

/** Nodes with pixel width below this threshold will not be rendered. */
export const CULLING_THRESHOLD_PX = 5

/** The number of consecutive nodes with the same width required to make a node collapsible. */
export const COLLAPSE_THRESHOLD = 2

/** The height of each node, in pixels. */
export const NODE_HEIGHT = 22

/** The horizontal width of the coordinate system. */
export const COORDINATE_WIDTH = 100_000

/** The space between the left edge of a node and its text label, in pixels. */
const NODE_TEXT_PADDING_LEFT = 5

/** The size of the collapse/expand button, in pixels. */
const COLLAPSE_BUTTON_SIZE = 12

const FONT_SIZE = 12
const FONT = `${FONT_SIZE}px sans-serif`
const COLLAPSE_FONT = `bold ${COLLAPSE_BUTTON_SIZE}px sans-serif`

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
                flexShrink: 0,
                maxWidth: "100%",
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

/** A rendered node rectangle, used for mouse hit testing. */
export interface RenderedNode {
    nodeId: number
    /** Canvas-pixel x of the left edge. */
    x: number
    /** Canvas-pixel y of the top edge. */
    y: number
    /** Canvas-pixel width. */
    width: number
    /** Canvas-pixel height. */
    height: number
    /** The node's full name (for hover highlight matching). */
    name: string
    /** True if this entry is the collapse/expand toggle button, not the node body. */
    isCollapseToggle: boolean
}

/**
 * Draws the entire flamegraph onto a canvas and returns the list of rendered
 * node rectangles so callers can do mouse hit testing without an extra pass.
 */
export function drawFlamegraph(
    ctx: CanvasRenderingContext2D,
    graph: StackGraph,
    rootNode: number,
    viewLeft: number,
    viewRight: number,
    canvasWidth: number,
    canvasHeight: number,
    dpr: number,
    expandedNodes: Set<number>,
    colorSettings: ColorSettings,
    hoveredName: string | null,
): RenderedNode[] {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, canvasWidth, canvasHeight)

    const rootValue = graph.values[rootNode]
    if (!rootValue || rootValue === 0n) return []

    const zoomWidth = viewRight - viewLeft
    const renderedNodes: RenderedNode[] = []

    ctx.font = FONT
    ctx.textBaseline = "middle"
    ctx.textAlign = "left"

    type StackEntry = {
        nodeId: number
        xOffset: bigint
        depth: number
        parentValue: bigint | null
    }

    const stack: StackEntry[] = [
        { nodeId: rootNode, xOffset: 0n, depth: 0, parentValue: null },
    ]

    while (stack.length > 0) {
        const { nodeId, xOffset, depth, parentValue } = stack.pop()!

        const value = graph.values[nodeId]
        const name = graph.nodeNames[nodeId]
        if (value == null || name == null) continue

        // Map from coordinate space to canvas pixels.
        const rectXCoord =
            (Number(xOffset) / Number(rootValue)) * COORDINATE_WIDTH
        const rectWidthCoord =
            (Number(value) / Number(rootValue)) * COORDINATE_WIDTH
        const canvasX = ((rectXCoord - viewLeft) / zoomWidth) * canvasWidth
        const canvasW = (rectWidthCoord / zoomWidth) * canvasWidth

        if (canvasW < CULLING_THRESHOLD_PX) continue

        // Viewport culling: skip nodes entirely off-screen horizontally.
        // Children are contained within the parent, so the entire subtree
        // can be skipped.
        if (canvasX + canvasW <= 0 || canvasX >= canvasWidth) continue

        const canvasY = canvasHeight - (depth + 1) * NODE_HEIGHT

        // Node body.
        ctx.fillStyle = colorFor(name, colorSettings)
        ctx.fillRect(canvasX, canvasY, canvasW, NODE_HEIGHT)

        if (name === hoveredName) {
            ctx.fillStyle = "rgba(0,0,0,0.25)"
            ctx.fillRect(canvasX, canvasY, canvasW, NODE_HEIGHT)
        }

        // 1px border at the bottom of each row.
        ctx.fillStyle = "rgba(0,0,0,0.15)"
        ctx.fillRect(canvasX, canvasY + NODE_HEIGHT - 1, canvasW, 1)

        renderedNodes.push({
            nodeId,
            x: canvasX,
            y: canvasY,
            width: canvasW,
            height: NODE_HEIGHT,
            name,
            isCollapseToggle: false,
        })

        // Collapse/expand: only compute the chain when there is a chance the
        // node is collapsible (value must differ from parent).
        let sameWidthChain: number[] | undefined
        let isCollapsible = false
        let isCollapsed = false

        if (value !== parentValue) {
            sameWidthChain = getSameWidthChain(nodeId, graph)
            isCollapsible = sameWidthChain.length >= COLLAPSE_THRESHOLD
            isCollapsed = isCollapsible && !expandedNodes.has(nodeId)
        }

        const showCollapseButton =
            isCollapsible && canvasW >= COLLAPSE_BUTTON_SIZE * 2

        if (showCollapseButton) {
            const btnX = canvasX + COLLAPSE_BUTTON_SIZE / 2
            const btnY = canvasY + (NODE_HEIGHT - COLLAPSE_BUTTON_SIZE) / 2

            ctx.fillStyle = "rgba(255,255,255,0.7)"
            ctx.fillRect(btnX, btnY, COLLAPSE_BUTTON_SIZE, COLLAPSE_BUTTON_SIZE)
            ctx.strokeStyle = "rgba(0,0,0,0.6)"
            ctx.lineWidth = 1
            ctx.strokeRect(
                btnX,
                btnY,
                COLLAPSE_BUTTON_SIZE,
                COLLAPSE_BUTTON_SIZE,
            )
            ctx.fillStyle = "rgba(0,0,0,0.8)"
            ctx.font = COLLAPSE_FONT
            ctx.textAlign = "center"
            ctx.fillText(
                isCollapsed ? "+" : "-",
                btnX + COLLAPSE_BUTTON_SIZE / 2,
                btnY + COLLAPSE_BUTTON_SIZE / 2,
            )
            ctx.font = FONT
            ctx.textAlign = "left"

            renderedNodes.push({
                nodeId,
                x: btnX,
                y: btnY,
                width: COLLAPSE_BUTTON_SIZE,
                height: COLLAPSE_BUTTON_SIZE,
                name,
                isCollapseToggle: true,
            })
        }

        // Text label — only render when the available pixel width can fit at
        // least a couple of characters.
        const textOffsetX = showCollapseButton
            ? COLLAPSE_BUTTON_SIZE * 2
            : NODE_TEXT_PADDING_LEFT
        const availableTextWidth = canvasW - textOffsetX

        if (availableTextWidth > FONT_SIZE) {
            const displayName = graph.displayNames[nodeId] ?? name
            const textWidth = ctx.measureText(displayName).width
            const textX = canvasX + textOffsetX
            ctx.fillStyle = "black"
            if (textWidth <= availableTextWidth) {
                ctx.fillText(displayName, textX, canvasY + NODE_HEIGHT / 2)
            } else {
                ctx.save()
                ctx.beginPath()
                ctx.rect(textX, canvasY, availableTextWidth, NODE_HEIGHT)
                ctx.clip()
                ctx.fillText(displayName, textX, canvasY + NODE_HEIGHT / 2)
                ctx.restore()
            }
        }

        // Push children onto the stack.
        const children = isCollapsed
            ? graph.children[sameWidthChain![sameWidthChain!.length - 1]!]
            : graph.children[nodeId]

        if (children) {
            let childXOffset = xOffset
            for (const childId of children) {
                const childValue = graph.values[childId]
                if (childValue != null) {
                    const childLeftCoord =
                        (Number(childXOffset) / Number(rootValue)) *
                        COORDINATE_WIDTH
                    const childWidthCoord =
                        (Number(childValue) / Number(rootValue)) *
                        COORDINATE_WIDTH

                    // Children are laid out left-to-right, so once the
                    // left edge is past the viewport we can stop.
                    if (childLeftCoord >= viewRight) break

                    // Skip children entirely before the viewport.
                    if (
                        childLeftCoord + childWidthCoord > viewLeft &&
                        (childWidthCoord / zoomWidth) * canvasWidth >=
                            CULLING_THRESHOLD_PX
                    ) {
                        stack.push({
                            nodeId: childId,
                            xOffset: childXOffset,
                            depth: depth + 1,
                            parentValue: value,
                        })
                    }
                    childXOffset += childValue
                }
            }
        }
    }

    return renderedNodes
}
