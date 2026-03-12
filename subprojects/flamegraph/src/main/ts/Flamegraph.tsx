import React, {
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
} from "react"
import type { Job, StackGraph } from "./worker"
import { Row, Stack } from "./containers"
import type { ColorContextType } from "./color"
import {
    drawFlamegraph,
    getSameWidthChain,
    nodeDetails,
    NodeDetails,
    type RenderedNode,
    COLLAPSE_THRESHOLD,
    COORDINATE_WIDTH,
    NODE_HEIGHT,
} from "./FlamegraphNode"
import { RangeSlider } from "./RangeSlider"

const Slider: React.FC<{
    min: number
    max: number
    value: number
    onChange: (newValue: number) => void
}> = ({ min, max, value, onChange }) => {
    const resolution = 10000

    let percent = (value - min) / (max - min)
    const scaledValue = percent * resolution

    return (
        <input
            style={{ flexGrow: 1 }}
            type="range"
            min={0}
            max={resolution}
            value={scaledValue}
            onChange={(e) => {
                const value = parseInt(e.target.value, 10)
                let percent = value / resolution
                onChange(min + percent * (max - min))
            }}
        />
    )
}

/**
 * Keeps the scroll container anchored to the bottom when the graph or root
 * node changes, and adjusts scroll position when content height changes due
 * to expand/collapse.
 *
 * savedScrollTopRef should be set to the current scrollTop immediately before
 * triggering a height change (e.g. in toggleExpand).
 */
const useScrollAnchor = (
    scrollRef: React.MutableRefObject<HTMLDivElement | null>,
    canvasHeight: number,
    graph: StackGraph,
    rootNode: number,
    savedScrollTopRef: React.MutableRefObject<number | null>,
) => {
    const lastScrollHeightRef = useRef(0)
    const lastGraphRef = useRef(graph)
    const lastRootNodeRef = useRef(rootNode)

    useLayoutEffect(() => {
        const container = scrollRef.current
        if (!container) return

        const scrollHeight = container.scrollHeight
        const delta = scrollHeight - lastScrollHeightRef.current
        const graphOrRootChanged =
            graph !== lastGraphRef.current ||
            rootNode !== lastRootNodeRef.current

        if (graphOrRootChanged) {
            container.scrollTop = container.scrollHeight
        } else if (delta !== 0) {
            const baseScrollTop =
                savedScrollTopRef.current ?? container.scrollTop
            container.scrollTop = baseScrollTop + delta
        }
        savedScrollTopRef.current = null

        lastScrollHeightRef.current = scrollHeight
        lastGraphRef.current = graph
        lastRootNodeRef.current = rootNode
    }, [canvasHeight, graph, rootNode])
}

/** The floating overlay panel with color sliders and action buttons. */
const ColorControls: React.FC<{
    rootNode: number
    canGoBack: boolean
    canGoForward: boolean
    onBack: () => void
    onForward: () => void
    onReset: () => void
    onMerge: () => void
    onIcicle: () => void
    isMutable: boolean
    onMutate: () => void
    colorCenter: number
    colorWidth: number
    colorAmount: number
    colorDistribution: number
    setColorCenter: (v: number) => void
    setColorWidth: (v: number) => void
    setColorAmount: (v: number) => void
    setColorDistribution: (v: number) => void
}> = ({
    rootNode,
    canGoBack,
    canGoForward,
    onBack,
    onForward,
    onReset,
    onMerge,
    onIcicle,
    isMutable,
    onMutate,
    colorCenter,
    colorWidth,
    colorAmount,
    colorDistribution,
    setColorCenter,
    setColorWidth,
    setColorAmount,
    setColorDistribution,
}) => {
    return (
        <Row
            wide
            style={{
                position: "absolute",
                top: "40px",
                justifyContent: "flex-end",
                pointerEvents: "none",
                zIndex: 1,
            }}
        >
            <Stack
                style={{
                    width: 500,
                    background: "rgba(0, 0, 0, 0.6)",
                    marginRight: "40px",
                    marginTop: "20px",
                    pointerEvents: "auto",
                }}
            >
                <Row>
                    <button onClick={onBack} disabled={!canGoBack}>
                        &larr; Back
                    </button>
                    <button onClick={onForward} disabled={!canGoForward}>
                        Forward &rarr;
                    </button>
                    <button onClick={onReset} disabled={rootNode === 0}>
                        Reset
                    </button>
                </Row>
                <Row>
                    Center ({Math.round(colorCenter)})
                    <Slider
                        min={0}
                        max={360}
                        value={colorCenter}
                        onChange={setColorCenter}
                    />
                </Row>
                <Row>
                    Width ({Math.round(colorWidth)})
                    <Slider
                        min={0}
                        max={360}
                        value={colorWidth}
                        onChange={setColorWidth}
                    />
                </Row>
                <Row>
                    Decay ({colorAmount.toFixed(2)})
                    <Slider
                        min={1}
                        max={3}
                        value={colorAmount}
                        onChange={setColorAmount}
                    />
                </Row>
                <Row>
                    Spread ({Math.round(colorDistribution)})
                    <Slider
                        min={1}
                        max={5000}
                        value={colorDistribution}
                        onChange={setColorDistribution}
                    />
                </Row>
                <button onClick={onMerge} disabled={rootNode === 0}>
                    Merge
                </button>
                <button onClick={onIcicle}>Icicle</button>
                <button onClick={onMutate} disabled={isMutable}>
                    {isMutable ? "Mutable" : "Mutate"}
                </button>
            </Stack>
        </Row>
    )
}

export const Flamegraph: React.FC<{
    graph: StackGraph
    rootNode: number
    setRootNode: (nodeId: number) => void
    viewLeft: number
    viewRight: number
    onUpdateZoom: (left: number, right: number) => void
    canGoBack: boolean
    canGoForward: boolean
    onBack: () => void
    onForward: () => void
    isMutable: boolean
    onMutate: () => void
    onDeleteNode: (nodeId: number) => void
    submitJob: (id: string, job: Job) => void
}> = ({
    graph,
    rootNode,
    setRootNode,
    viewLeft,
    viewRight,
    onUpdateZoom,
    canGoBack,
    canGoForward,
    onBack,
    onForward,
    isMutable,
    onMutate,
    onDeleteNode,
    submitJob,
}) => {
    const canvasRef = useRef<HTMLCanvasElement | null>(null)
    const scrollRef = useRef<HTMLDivElement | null>(null)
    const detailsRef = useRef<HTMLSpanElement | null>(null)
    const savedScrollTopRef = useRef<number | null>(null)

    const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set())

    const [colorCenter, setColorCenter] = useState(98)
    const [colorWidth, setColorWidth] = useState(100)
    const [colorAmount, setColorAmount] = useState(1.67)
    const [colorDistribution, setColorDistribution] = useState(1199)

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
                ? graph.children[chain[chain.length - 1]!]
                : graph.children[nodeId]

            if (children) {
                for (const childId of children) {
                    stack.push([childId, depth + 1, value])
                }
            }
        }

        return max
    }, [graph, rootNode, expandedNodes])

    const canvasHeight = Math.max(NODE_HEIGHT, maxDepth * NODE_HEIGHT)

    useScrollAnchor(scrollRef, canvasHeight, graph, rootNode, savedScrollTopRef)

    // --- Ref-based draw state ---
    //
    // These refs hold the latest draw parameters so that zoom and hover can
    // trigger immediate canvas redraws without going through React state.
    const drawParamsRef = useRef<{
        graph: StackGraph
        rootNode: number
        viewLeft: number
        viewRight: number
        expandedNodes: Set<number>
        colorSettings: ColorContextType
        hoveredName: string | null
    }>({
        graph,
        rootNode,
        viewLeft,
        viewRight,
        expandedNodes,
        colorSettings: {
            center: colorCenter,
            width: colorWidth,
            amount: colorAmount,
            distribution: colorDistribution,
        },
        hoveredName: null,
    })

    const hitListRef = useRef<RenderedNode[]>([])

    const redraw = useCallback(() => {
        const canvas = canvasRef.current
        if (!canvas || canvas.width === 0 || canvas.height === 0) return
        const ctx = canvas.getContext("2d")
        if (!ctx) return
        const dpr = window.devicePixelRatio || 1
        const p = drawParamsRef.current
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
        drawParamsRef.current.colorSettings = {
            center: colorCenter,
            width: colorWidth,
            amount: colorAmount,
            distribution: colorDistribution,
        }
        redraw()
    }, [
        graph,
        rootNode,
        viewLeft,
        viewRight,
        expandedNodes,
        colorCenter,
        colorWidth,
        colorAmount,
        colorDistribution,
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
        if (drawParamsRef.current.hoveredName !== null) {
            drawParamsRef.current.hoveredName = null
            redraw()
        }
        if (detailsRef.current) {
            detailsRef.current.textContent = "Hover for details, click to zoom"
        }
    }, [redraw])

    useEffect(() => {
        clearHover()
    }, [graph])

    const handleMouseMove: React.MouseEventHandler<HTMLCanvasElement> = (e) => {
        const hit = hitTest(e.clientX, e.clientY)
        const newName =
            hit && !hit.isCollapseToggle ? hit.name : null

        const canvas = canvasRef.current
        if (canvas) {
            canvas.style.cursor = hit ? "pointer" : "default"
        }

        if (detailsRef.current) {
            detailsRef.current.textContent =
                hit && !hit.isCollapseToggle
                    ? nodeDetails(hit.nodeId, drawParamsRef.current.graph)
                    : "Hover for details, click to zoom"
        }

        if (newName !== drawParamsRef.current.hoveredName) {
            drawParamsRef.current.hoveredName = newName
            redraw()
        }
    }

    const handleMouseLeave = () => clearHover()

    const handleClick: React.MouseEventHandler<HTMLCanvasElement> = (e) => {
        const hit = hitTest(e.clientX, e.clientY)
        if (!hit) return

        if (hit.isCollapseToggle) {
            savedScrollTopRef.current = scrollRef.current?.scrollTop ?? null
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
            if (hit.nodeId !== currentRoot) {
                onDeleteNode(hit.nodeId)
                clearHover()
            }
        }
    }

    if (
        !graph ||
        graph.values.length === 0 ||
        graph.values[0] === undefined ||
        graph.values[0] === 0n
    ) {
        return <div>No data to display.</div>
    }

    const showMergedSubgraph = (nodeId: number) => {
        const nodeName = graph.nodeNames[nodeId]!
        submitJob(`merge${nodeName}`, { type: "mergeChildren", nodeName, graph })
    }

    const showIcicleGraph = (nodeId: number) => {
        const nodeName = graph.nodeNames[nodeId]!
        submitJob(`icicle${nodeName}${nodeId}`, {
            type: "icicleGraph",
            nodeId,
            graph,
        })
    }

    return (
        <Stack tall style={{ position: "relative" }}>
            <Row
                wide
                style={{
                    position: "absolute",
                    top: "0px",
                    left: "0px",
                    zIndex: 1,
                    pointerEvents: "none",
                }}
            >
                <div
                    style={{
                        flexGrow: 1,
                        background: "rgba(0, 0, 0, 0.6)",
                        padding: "10px",
                        height: "40px",
                        pointerEvents: "auto",
                        display: "flex",
                        alignItems: "center",
                        gap: "10px",
                        boxSizing: "border-box",
                    }}
                >
                    <span>Range</span>
                    <RangeSlider
                        min={0}
                        max={COORDINATE_WIDTH}
                        valueLeft={viewLeft}
                        valueRight={viewRight}
                        onChange={onUpdateZoom}
                    />
                </div>
            </Row>
            <ColorControls
                rootNode={rootNode}
                canGoBack={canGoBack}
                canGoForward={canGoForward}
                onBack={onBack}
                onForward={onForward}
                onReset={() => setRootNode(0)}
                onMerge={() => showMergedSubgraph(rootNode)}
                isMutable={isMutable}
                onMutate={onMutate}
                onIcicle={() => showIcicleGraph(rootNode)}
                colorCenter={colorCenter}
                colorWidth={colorWidth}
                colorAmount={colorAmount}
                colorDistribution={colorDistribution}
                setColorCenter={setColorCenter}
                setColorWidth={setColorWidth}
                setColorAmount={setColorAmount}
                setColorDistribution={setColorDistribution}
            />
            <div
                ref={scrollRef}
                style={{
                    flexGrow: 1,
                    overflowY: "auto",
                    display: "flex",
                    paddingBottom: NODE_HEIGHT,
                    overflowAnchor: "none",
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
            <NodeDetails ref={detailsRef} />
        </Stack>
    )
}
