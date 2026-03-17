import React, {
    forwardRef,
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
} from "react"
import { getGraph } from "./graphStore"
import type { Graph } from "./stackGraph"
import { Stack } from "./containers"
import type { ColorSettings } from "./color"
import {
    drawFlamegraph,
    getSameWidthChain,
    nodeDetails,
    type RenderedNode,
    COLLAPSE_THRESHOLD,
    COORDINATE_WIDTH,
    NODE_HEIGHT,
} from "./FlamegraphNode"

const NodeDetails = forwardRef<HTMLSpanElement, {}>((_, ref) => (
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
))

/**
 * Returns the pixel width of the vertical scrollbar inside the given scroll
 * container, or 0 when no scrollbar is visible. Updates automatically when
 * the container is resized or its content height changes.
 */
const useScrollbarWidth = (el: HTMLDivElement | null): number => {
    const [scrollbarWidth, setScrollbarWidth] = useState(0)
    useEffect(() => {
        if (!el) return
        const update = () => setScrollbarWidth(el.offsetWidth - el.clientWidth)
        const ro = new ResizeObserver(update)
        ro.observe(el)
        update()
        return () => ro.disconnect()
    }, [el])
    return scrollbarWidth
}

/**
 * Manages scroll position for the flamegraph container:
 * - When the graph changes (tab switch): restores the saved scroll position,
 *   or goes to the top if none is saved.
 * - When the root node changes within the same graph (drill-down): scrolls to
 *   the bottom so the new root is visible at the top of the viewport.
 * - When content height changes (expand/collapse): adjusts scroll
 *   proportionally to keep the same visual anchor.
 *
 * savedScrollTopRef should be set to the current scrollTop immediately before
 * triggering a height change (e.g. in toggleExpand).
 */
const useScrollAnchor = (
    scrollRef: React.RefObject<HTMLDivElement | null>,
    canvasHeight: number,
    graphId: string | null | undefined,
    rootNode: number,
    savedScrollTopRef: React.MutableRefObject<number | null>,
    initialScrollTop: number | null | undefined,
) => {
    const lastScrollHeightRef = useRef(0)
    const lastGraphIdRef = useRef(graphId)
    const lastRootNodeRef = useRef(rootNode)

    useLayoutEffect(() => {
        const container = scrollRef.current
        if (!container) return

        const scrollHeight = container.scrollHeight
        const delta = scrollHeight - lastScrollHeightRef.current
        const graphChanged = graphId !== lastGraphIdRef.current
        const rootChanged = rootNode !== lastRootNodeRef.current

        if (graphChanged || rootChanged) {
            // Restore saved position if available (tab switch, history navigation),
            // otherwise scroll to the bottom to reveal the new root (drill-down).
            container.scrollTop = initialScrollTop ?? container.scrollHeight
        } else if (delta !== 0) {
            const baseScrollTop =
                savedScrollTopRef.current ?? container.scrollTop
            container.scrollTop = baseScrollTop + delta
        }
        savedScrollTopRef.current = null

        lastScrollHeightRef.current = scrollHeight
        lastGraphIdRef.current = graphId
        lastRootNodeRef.current = rootNode
    }, [canvasHeight, graphId, rootNode])
}

/**
 * Pure canvas component: renders the flamegraph and handles mouse/keyboard
 * interaction. All overlay chrome (range slider, controls, node details)
 * is owned by the parent.
 */
export const Flamegraph: React.FC<{
    graphId: string | null | undefined
    rootNode: number
    setRootNode: (nodeId: number) => void
    viewLeft: number
    viewRight: number
    onUpdateZoom: (left: number, right: number) => void
    isMutable: boolean
    onDeleteNode: (nodeId: number) => void
    colorSettings: ColorSettings
    /** Scroll position to restore when this graph is displayed. */
    initialScrollTop?: number | null
    /** Called (debounced) when the user scrolls, with the new scrollTop. */
    onScrollChange?: (scrollTop: number) => void
    /** Highlights nodes whose raw name contains this string. */
    searchQuery?: string
    children?: React.ReactNode
}> = ({
    graphId,
    rootNode,
    setRootNode,
    viewLeft,
    viewRight,
    onUpdateZoom,
    isMutable,
    onDeleteNode,
    colorSettings,
    initialScrollTop,
    onScrollChange,
    searchQuery,
    children,
}) => {
    const graph = graphId != null ? (getGraph(graphId) ?? null) : null
    const scrollContainerRef = useRef<HTMLDivElement | null>(null)
    const nodeDetailsRef = useRef<HTMLSpanElement | null>(null)
    const [scrollEl, setScrollEl] = useState<HTMLDivElement | null>(null)
    const scrollbarWidth = useScrollbarWidth(scrollEl)
    const canvasRef = useRef<HTMLCanvasElement | null>(null)
    const savedScrollTopRef = useRef<number | null>(null)

    const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set())

    // The maximum depth of the visible tree, used to size the canvas.
    // Zoom level is intentionally excluded from deps — removing the culling
    // threshold dependency means canvasHeight doesn't change during zoom,
    // so the scroll container stays stable while panning/zooming.
    const maxDepth = useMemo(() => {
        if (!graph || graph.values.length === 0) return 0
        const rootValue = graph.values[rootNode]
        if (!rootValue || rootValue === 0n) return 0

        let max = 0
        const stack: Array<[number, number, bigint | null]> = [
            [rootNode, 1, null],
        ]

        while (stack.length > 0) {
            const [nodeId, depth, parentValue] = stack.pop()!
            if (depth > max) max = depth

            const value = graph.values[nodeId]!
            const chain = getSameWidthChain(nodeId, graph)
            const isCollapsible =
                chain.length >= COLLAPSE_THRESHOLD && value !== parentValue
            const isCollapsed = isCollapsible && !expandedNodes.has(nodeId)
            const children = isCollapsed
                ? graph.getChildren(chain[chain.length - 1]!)
                : graph.getChildren(nodeId)

            for (const childId of children) {
                stack.push([childId, depth + 1, value])
            }
        }

        return max
    }, [graph, rootNode, expandedNodes])

    const canvasHeight = Math.max(NODE_HEIGHT, maxDepth * NODE_HEIGHT)

    useScrollAnchor(
        scrollContainerRef,
        canvasHeight,
        graphId,
        rootNode,
        savedScrollTopRef,
        initialScrollTop,
    )

    useEffect(() => {
        const el = scrollEl
        if (!el || !onScrollChange) return
        let timer: number
        const handleScroll = () => {
            clearTimeout(timer)
            timer = window.setTimeout(() => onScrollChange(el.scrollTop), 100)
        }
        el.addEventListener("scroll", handleScroll, { passive: true })
        return () => {
            el.removeEventListener("scroll", handleScroll)
            clearTimeout(timer)
        }
    }, [scrollEl, onScrollChange])

    // --- Ref-based draw state ---
    //
    // These refs hold the latest draw parameters so that zoom and hover can
    // trigger immediate canvas redraws without going through React state.
    const drawParamsRef = useRef<{
        graph: Graph | null | undefined
        rootNode: number
        viewLeft: number
        viewRight: number
        expandedNodes: Set<number>
        colorSettings: ColorSettings
        hoveredName: string | null
        hoveredCollapseNodeId: number | null
        searchQuery: string | undefined
    }>({
        graph,
        rootNode,
        viewLeft,
        viewRight,
        expandedNodes,
        colorSettings,
        hoveredName: null,
        hoveredCollapseNodeId: null,
        searchQuery,
    })

    const hitListRef = useRef<RenderedNode[]>([])

    const redraw = useCallback(() => {
        const canvas = canvasRef.current
        if (!canvas || canvas.width === 0 || canvas.height === 0) return
        const ctx = canvas.getContext("2d")
        if (!ctx) return
        const dpr = window.devicePixelRatio || 1
        const p = drawParamsRef.current
        if (!p.graph) {
            ctx.clearRect(0, 0, canvas.width, canvas.height)
            hitListRef.current = []
            return
        }
        hitListRef.current = drawFlamegraph(
            ctx,
            p.graph,
            p.rootNode,
            p.viewLeft,
            p.viewRight,
            canvas.width / dpr,
            canvas.height / dpr,
            dpr,
            p.expandedNodes,
            p.colorSettings,
            p.hoveredName,
            p.hoveredCollapseNodeId,
            p.searchQuery,
        )
    }, [])

    // Observe canvas element size changes and keep the drawing buffer
    // resolution in sync (scaled by devicePixelRatio for sharp rendering
    // on HiDPI displays). Setting canvas.width/height clears the buffer,
    // so we call redraw() immediately after.
    useEffect(() => {
        const canvas = canvasRef.current
        if (!canvas) return

        const syncSize = () => {
            const dpr = window.devicePixelRatio || 1
            const rect = canvas.getBoundingClientRect()
            const bufW = Math.round(rect.width * dpr)
            const bufH = Math.round(rect.height * dpr)
            if (canvas.width !== bufW || canvas.height !== bufH) {
                canvas.width = bufW
                canvas.height = bufH
                redraw()
            }
        }

        const ro = new ResizeObserver(syncSize)
        ro.observe(canvas)
        syncSize()

        return () => ro.disconnect()
    }, [redraw])

    // --- Zoom and pan via wheel ---
    //
    // Zoom/pan is handled entirely outside React: the wheel handler updates
    // viewRef and drawParamsRef, then redraws immediately for responsive
    // feedback. onUpdateZoom is called synchronously so the range slider
    // stays in sync, but the sync effect guards against overwriting
    // viewRef with stale React state while the wheel is still active.
    const viewRef = useRef({ left: viewLeft, right: viewRight })
    const onUpdateZoomRef = useRef(onUpdateZoom)
    const wheelActiveRef = useRef(false)
    const wheelTimerRef = useRef<number>(0)

    onUpdateZoomRef.current = onUpdateZoom

    // Sync React state into drawParamsRef and redraw. This covers graph
    // changes, navigation, expand/collapse, and color setting changes.
    // For view values: adopt the incoming React state unless the wheel
    // handler is actively driving the view (to avoid overwriting newer
    // wheel-driven values with stale React state mid-scroll).
    useEffect(() => {
        if (!wheelActiveRef.current) {
            viewRef.current = { left: viewLeft, right: viewRight }
        }
        drawParamsRef.current.graph = graph
        drawParamsRef.current.rootNode = rootNode
        drawParamsRef.current.viewLeft = viewRef.current.left
        drawParamsRef.current.viewRight = viewRef.current.right
        drawParamsRef.current.expandedNodes = expandedNodes
        drawParamsRef.current.colorSettings = colorSettings
        drawParamsRef.current.searchQuery = searchQuery
        redraw()
    }, [
        graph,
        rootNode,
        viewLeft,
        viewRight,
        expandedNodes,
        colorSettings,
        searchQuery,
        redraw,
    ])

    useEffect(() => {
        const MIN_ZOOM_WIDTH = 10

        const clampView = (left: number, right: number): [number, number] => {
            left = Math.max(0, left)
            right = Math.min(COORDINATE_WIDTH, right)
            if (right - left < MIN_ZOOM_WIDTH) {
                if (left === 0) right = MIN_ZOOM_WIDTH
                else if (right === COORDINATE_WIDTH)
                    left = COORDINATE_WIDTH - MIN_ZOOM_WIDTH
            }
            return [left, right]
        }

        const applyView = (newLeft: number, newRight: number) => {
            ;[newLeft, newRight] = clampView(newLeft, newRight)

            if (
                newLeft === viewRef.current.left &&
                newRight === viewRef.current.right
            )
                return

            viewRef.current = { left: newLeft, right: newRight }
            drawParamsRef.current.viewLeft = newLeft
            drawParamsRef.current.viewRight = newRight
            redraw()

            // Mark wheel as active so the sync effect doesn't overwrite
            // viewRef with stale React state while events are in flight.
            // Reset after a short idle period so range-slider changes can
            // update viewRef again.
            wheelActiveRef.current = true
            clearTimeout(wheelTimerRef.current)
            wheelTimerRef.current = window.setTimeout(() => {
                wheelActiveRef.current = false
            }, 32)

            // Call synchronously so the range slider updates every frame.
            onUpdateZoomRef.current(newLeft, newRight)
        }

        const handleWheel = (e: WheelEvent) => {
            const canvas = canvasRef.current
            if (!canvas) return

            const hasZoom = e.metaKey && e.deltaY !== 0
            const hasPan = e.deltaX !== 0

            if (!hasZoom && !hasPan) return
            e.preventDefault()

            const { left, right } = viewRef.current
            const windowSize = right - left
            let newLeft = left
            let newRight = right

            // Horizontal scroll → pan
            if (hasPan) {
                const panDelta = windowSize * -0.0004 * e.deltaX
                newLeft = Math.round(newLeft + panDelta)
                newRight = Math.round(newRight + panDelta)
                // Keep the window size constant when hitting edges
                if (newLeft < 0) {
                    newRight -= newLeft
                    newLeft = 0
                }
                if (newRight > COORDINATE_WIDTH) {
                    newLeft -= newRight - COORDINATE_WIDTH
                    newRight = COORDINATE_WIDTH
                }
            }

            // Cmd+scroll → zoom around cursor
            if (hasZoom) {
                const rect = canvas.getBoundingClientRect()
                const mousePosPercent = (e.clientX - rect.left) / rect.width
                const currentSize = newRight - newLeft
                const totalDelta = currentSize * 0.001 * e.deltaY

                newLeft = Math.round(newLeft - totalDelta * mousePosPercent)
                newRight = Math.round(
                    newRight + totalDelta * (1 - mousePosPercent),
                )

                const newWidth = newRight - newLeft
                if (newWidth < MIN_ZOOM_WIDTH) {
                    const centerShift =
                        (MIN_ZOOM_WIDTH - newWidth) * mousePosPercent
                    newLeft = Math.round(newLeft - centerShift)
                    newRight = newLeft + MIN_ZOOM_WIDTH
                }
            }

            applyView(newLeft, newRight)
        }

        window.addEventListener("wheel", handleWheel, { passive: false })
        return () => {
            window.removeEventListener("wheel", handleWheel)
            clearTimeout(wheelTimerRef.current)
        }
    }, [redraw])

    // --- Mouse hit testing ---

    const hitTest = useCallback(
        (clientX: number, clientY: number): RenderedNode | null => {
            const canvas = canvasRef.current
            if (!canvas) return null
            const rect = canvas.getBoundingClientRect()
            const x = clientX - rect.left
            const y = clientY - rect.top
            // Iterate in reverse so collapse toggle entries (pushed after the
            // node body) take precedence when the cursor is over the button.
            const hits = hitListRef.current
            for (let i = hits.length - 1; i >= 0; i--) {
                const h = hits[i]!
                if (
                    x >= h.x &&
                    x <= h.x + h.width &&
                    y >= h.y &&
                    y <= h.y + h.height
                ) {
                    return h
                }
            }
            return null
        },
        [],
    )

    const clearHover = useCallback(() => {
        const changed =
            drawParamsRef.current.hoveredName !== null ||
            drawParamsRef.current.hoveredCollapseNodeId !== null
        drawParamsRef.current.hoveredName = null
        drawParamsRef.current.hoveredCollapseNodeId = null
        if (changed) redraw()
        if (nodeDetailsRef.current) {
            nodeDetailsRef.current.textContent =
                "Hover for details, click to zoom"
        }
    }, [redraw])

    useEffect(() => {
        clearHover()
    }, [graphId])

    const handleMouseMove: React.MouseEventHandler<HTMLCanvasElement> = (e) => {
        const hit = hitTest(e.clientX, e.clientY)
        const newName = hit && !hit.isCollapseToggle ? hit.name : null
        const newCollapseNodeId =
            hit?.isCollapseToggle ? hit.nodeId : null

        const canvas = canvasRef.current
        if (canvas) {
            canvas.style.cursor = hit ? "pointer" : "default"
        }

        if (nodeDetailsRef.current) {
            nodeDetailsRef.current.textContent =
                hit && !hit.isCollapseToggle
                    ? drawParamsRef.current.graph
                        ? nodeDetails(hit.nodeId, drawParamsRef.current.graph)
                        : ""
                    : "Hover for details, click to zoom"
        }

        const nameChanged = newName !== drawParamsRef.current.hoveredName
        const collapseChanged =
            newCollapseNodeId !== drawParamsRef.current.hoveredCollapseNodeId
        if (nameChanged || collapseChanged) {
            drawParamsRef.current.hoveredName = newName
            drawParamsRef.current.hoveredCollapseNodeId = newCollapseNodeId
            redraw()
        }
    }

    const handleMouseLeave = () => clearHover()

    const handleClick: React.MouseEventHandler<HTMLCanvasElement> = (e) => {
        const hit = hitTest(e.clientX, e.clientY)
        if (!hit) return

        if (hit.isCollapseToggle) {
            savedScrollTopRef.current =
                scrollContainerRef.current?.scrollTop ?? null
            setExpandedNodes((prev) => {
                const next = new Set(prev)
                if (next.has(hit.nodeId)) {
                    next.delete(hit.nodeId)
                } else {
                    next.add(hit.nodeId)
                }
                return next
            })
        } else {
            setRootNode(hit.nodeId)
            clearHover()
        }
    }

    const handleMouseDown: React.MouseEventHandler<HTMLCanvasElement> = (e) => {
        if (e.button !== 1) return
        e.preventDefault()
        if (!isMutable) return

        const hit = hitTest(e.clientX, e.clientY)
        if (hit && !hit.isCollapseToggle) {
            const { rootNode: currentRoot } = drawParamsRef.current
            if (hit.nodeId !== currentRoot && hit.nodeId !== 0) {
                onDeleteNode(hit.nodeId)
                clearHover()
            }
        }
    }

    return (
        <Stack tall wide>
            <div
                ref={(el) => {
                    scrollContainerRef.current = el
                    setScrollEl(el)
                }}
                style={{
                    flexGrow: 1,
                    overflowY: "auto",
                    display: "flex",
                    paddingBottom: NODE_HEIGHT,
                }}
            >
                <canvas
                    ref={canvasRef}
                    style={{
                        width: "100%",
                        height: canvasHeight,
                        display: "block",
                        marginTop: "auto",
                        flexShrink: 0,
                    }}
                    onMouseMove={handleMouseMove}
                    onMouseLeave={handleMouseLeave}
                    onClick={handleClick}
                    onMouseDown={handleMouseDown}
                />
            </div>
            <Stack
                tall
                style={{
                    justifyContent: "space-between",
                    position: "absolute",
                    top: 0,
                    left: 0,
                    width: `calc(100% - ${scrollbarWidth}px)`,
                    pointerEvents: "none",
                    overflow: "hidden",
                }}
            >
                <Stack wide style={{ flex: 1, minHeight: 0 }}>
                    {children}
                </Stack>
                <NodeDetails ref={nodeDetailsRef} />
            </Stack>
        </Stack>
    )
}
