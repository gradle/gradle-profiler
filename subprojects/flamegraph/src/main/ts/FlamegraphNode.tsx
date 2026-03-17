import type { Graph } from "./stackGraph"
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

const FONT_SIZE = 12
const FONT = `${FONT_SIZE}px sans-serif`

export const getSameWidthChain = (nodeId: number, graph: Graph): number[] => {
    const chain = []
    let currentId = nodeId
    while (true) {
        const children = graph.getChildren(currentId)
        if (children.length === 1) {
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

export const nodeDetails = (nodeId: number, graph: Graph): string => {
    const value = graph.values[nodeId]
    if (value == undefined) {
        return "Malformed graph. Missing node data."
    }
    return `${graph.getNodeName(nodeId)} ${BigInt.asIntN(64, value).toLocaleString()} samples`
}

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
    graph: Graph,
    rootNode: number,
    viewLeft: number,
    viewRight: number,
    canvasWidth: number,
    canvasHeight: number,
    dpr: number,
    expandedNodes: Set<number>,
    colorSettings: ColorSettings,
    hoveredName: string | null,
    hoveredCollapseNodeId: number | null,
    searchQuery: string | undefined,
): RenderedNode[] {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
    ctx.clearRect(0, 0, canvasWidth, canvasHeight)

    const rootValue = graph.values[rootNode]
    if (!rootValue || rootValue === 0n) return []

    const searchQueryLower = searchQuery?.toLowerCase()
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
        if (value == null) continue
        const name = graph.getNodeName(nodeId)

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

        const showCollapseButton = isCollapsible && canvasW >= NODE_HEIGHT * 2

        // The body area excludes the collapse button so overlays never dim it.
        const bodyX = showCollapseButton ? canvasX + NODE_HEIGHT : canvasX
        const bodyW = showCollapseButton ? canvasW - NODE_HEIGHT : canvasW

        const nodeMatchesSearch = !searchQueryLower || name.toLowerCase().includes(searchQueryLower)
        // True when the chain contains a search match — the +/− button stays undimmed
        // regardless of whether the chain is currently expanded or collapsed.
        const chainMatchesSearch =
            !!searchQueryLower &&
            sameWidthChain != null &&
            sameWidthChain.some((id) =>
                graph.getNodeName(id).toLowerCase().includes(searchQueryLower),
            )
        // Dim the node body if nothing relevant (node or hidden chain) matches.
        const isDimmed = !!searchQueryLower && !nodeMatchesSearch && !chainMatchesSearch
        // Dim the button when there are no hidden matches to reveal.
        const btnDimmed =
            showCollapseButton &&
            !!searchQueryLower &&
            !chainMatchesSearch

        if (isDimmed) {
            ctx.fillStyle = "rgba(0,0,0,0.5)"
            if (btnDimmed) {
                // Entire row dimmed: one rect avoids a sub-pixel gap at the
                // button/body boundary.
                ctx.fillRect(canvasX, canvasY, canvasW, NODE_HEIGHT)
            } else {
                ctx.fillRect(bodyX, canvasY, bodyW, NODE_HEIGHT)
            }
        } else if (btnDimmed) {
            // Node matches but the button still needs dimming.
            ctx.fillStyle = "rgba(0,0,0,0.5)"
            ctx.fillRect(canvasX, canvasY, NODE_HEIGHT, NODE_HEIGHT)
        }

        // Hover overlay for the node body, excluding the button area.
        if (name === hoveredName) {
            ctx.fillStyle = "rgba(0,0,0,0.25)"
            ctx.fillRect(bodyX, canvasY, bodyW, NODE_HEIGHT)
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

        if (showCollapseButton) {
            const btnX = canvasX
            const btnY = canvasY

            if (hoveredCollapseNodeId === nodeId) {
                ctx.fillStyle = "rgba(0,0,0,0.25)"
                ctx.fillRect(btnX, btnY, NODE_HEIGHT, NODE_HEIGHT)
            }

            ctx.fillStyle = "black"
            ctx.textAlign = "center"
            ctx.fillText(
                isCollapsed ? "+" : "\u2212",
                btnX + NODE_HEIGHT / 2,
                btnY + NODE_HEIGHT / 2,
            )
            ctx.textAlign = "left"

            renderedNodes.push({
                nodeId,
                x: btnX,
                y: btnY,
                width: NODE_HEIGHT,
                height: NODE_HEIGHT,
                name,
                isCollapseToggle: true,
            })
        }

        // Text label — only render when the available pixel width can fit at
        // least a couple of characters.
        const textOffsetX = showCollapseButton
            ? NODE_HEIGHT
            : NODE_TEXT_PADDING_LEFT
        const availableTextWidth = canvasW - textOffsetX

        if (availableTextWidth > FONT_SIZE) {
            const displayName = graph.getDisplayName(nodeId)
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
            ? graph.getChildren(sameWidthChain![sameWidthChain!.length - 1]!)
            : graph.getChildren(nodeId)

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

    return renderedNodes
}
