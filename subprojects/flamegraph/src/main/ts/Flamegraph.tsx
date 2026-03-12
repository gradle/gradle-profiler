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
import { ColorContext } from "./color"
import {
    CollapseContext,
    FlamegraphNode,
    getSameWidthChain,
    nodeDetails,
    NodeDetails,
    GraphContext,
    COLLAPSE_THRESHOLD,
    COORDINATE_WIDTH,
    CULLING_THRESHOLD_PX,
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

const useSvgWidth = (
    svgRef: React.RefObject<SVGSVGElement | null>,
): number | null => {
    const [svgWidth, setSvgWidth] = useState<number | null>(null)

    useEffect(() => {
        const svg = svgRef.current
        if (!svg) return

        const resizeObserver = new ResizeObserver(() => {
            setSvgWidth(svg.getBoundingClientRect().width)
        })

        resizeObserver.observe(svg)
        setSvgWidth(svg.getBoundingClientRect().width)

        return () => resizeObserver.disconnect()
    }, [svgRef.current])

    return svgWidth
}

/**
 * Keeps the scroll container anchored to the bottom when the graph or zoom
 * level changes, and adjusts scroll position to compensate when content
 * height changes due to expand/collapse.
 *
 * savedScrollTopRef should be set to the current scrollTop immediately before
 * triggering a height change (e.g. in toggleExpand), so the layout effect can
 * apply the delta against the pre-change position.
 */
const useScrollAnchor = (
    scrollRef: React.MutableRefObject<HTMLDivElement | null>,
    svgHeight: number,
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
        const graphOrZoomChanged =
            graph !== lastGraphRef.current ||
            rootNode !== lastRootNodeRef.current

        if (graphOrZoomChanged) {
            container.scrollTop = container.scrollHeight
        } else if (delta !== 0) {
            // Add delta to the scrollTop captured before the DOM update. We cannot use container.scrollTop
            // here because the browser may have already clamped it when the content shrank (e.g. on collapse
            // while scrolled to the bottom), which would cause the correction to be applied twice.
            const baseScrollTop =
                savedScrollTopRef.current ?? container.scrollTop
            container.scrollTop = baseScrollTop + delta
        }
        savedScrollTopRef.current = null

        lastScrollHeightRef.current = scrollHeight
        lastGraphRef.current = graph
        lastRootNodeRef.current = rootNode
    }, [svgHeight, graph, rootNode])
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
    const svgRef = useRef<SVGSVGElement | null>(null)
    const scrollRef = useRef<HTMLDivElement | null>(null)
    const detailsRef = useRef<HTMLSpanElement | null>(null)
    const hoverStyleRef = useRef<HTMLStyleElement | null>(null)
    const savedScrollTopRef = useRef<number | null>(null)

    const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set())

    const svgWidth = useSvgWidth(svgRef)
    const zoomWidth = viewRight - viewLeft

    const maxDepth = useMemo(() => {
        if (!graph || graph.values.length === 0 || !svgWidth) {
            return 0
        }

        const rootValue = graph.values[rootNode]
        if (!rootValue || rootValue === 0n) {
            return 0
        }

        let max = 0
        const stack: Array<[number, number, bigint | null]> = [
            [rootNode, 1, null],
        ]
        const minValueThresholdRatio =
            (CULLING_THRESHOLD_PX / svgWidth) * (zoomWidth / COORDINATE_WIDTH)

        while (stack.length > 0) {
            const [nodeId, depth, parentValue] = stack.pop()!

            if (depth > max) {
                max = depth
            }

            const value = graph.values[nodeId]!
            const chain = getSameWidthChain(nodeId, graph)
            const isCollapsible =
                chain.length >= COLLAPSE_THRESHOLD && value !== parentValue
            const isCollapsed = isCollapsible && !expandedNodes.has(nodeId)

            const children = isCollapsed
                ? graph.children[chain[chain.length - 1]!]
                : graph.children[nodeId]

            if (children) {
                for (let i = 0; i < children.length; i++) {
                    const childId = children[i]!
                    const childValue = graph.values[childId]
                    if (childValue !== undefined) {
                        const childValueRatio =
                            Number(childValue) / Number(rootValue)
                        if (childValueRatio >= minValueThresholdRatio) {
                            stack.push([childId, depth + 1, value])
                        }
                    }
                }
            }
        }

        return max
    }, [graph, rootNode, svgWidth, expandedNodes, zoomWidth])

    const [colorCenter, setColorCenter] = useState(98)
    const [colorWidth, setColorWidth] = useState(100)
    const [colorAmount, setColorAmount] = useState(1.67)
    const [colorDistribution, setColorDistribution] = useState(1199)

    const svgHeight = maxDepth * NODE_HEIGHT

    useScrollAnchor(scrollRef, svgHeight, graph, rootNode, savedScrollTopRef)

    const handleMouseLeave = () => {
        if (detailsRef.current) {
            detailsRef.current.textContent = "Hover for details, click to zoom"
        }
        if (hoverStyleRef.current) {
            hoverStyleRef.current.textContent = ""
        }
    }

    const handleMouseDown: React.MouseEventHandler<SVGSVGElement> = (event) => {
        if (event.button !== 1) return
        event.preventDefault()
        if (!isMutable) return

        const target = event.target
        if (target instanceof SVGElement) {
            const nodeId = target.getAttribute("data-node-id")
            if (nodeId) {
                const id = parseInt(nodeId, 10)
                if (id !== rootNode) {
                    onDeleteNode(id)
                    handleMouseLeave()
                }
            }
        }
    }

    const handleMouseMove: React.MouseEventHandler<SVGSVGElement> = (event) => {
        const target = event.target
        if (target instanceof SVGElement && target.tagName === "rect") {
            const name = target.getAttribute("data-name")
            const nodeId = target.getAttribute("data-node-id")

            if (name && nodeId) {
                if (detailsRef.current) {
                    detailsRef.current.textContent = nodeDetails(
                        parseInt(nodeId, 10),
                        graph,
                    )
                }
                if (hoverStyleRef.current) {
                    hoverStyleRef.current.textContent = `
                        .flamegraph-svg rect[data-name="${CSS.escape(name)}"] {
                            filter: brightness(0.75);
                        }
                    `
                }
            } else {
                handleMouseLeave()
            }
        } else {
            handleMouseLeave()
        }
    }

    useEffect(() => {
        handleMouseLeave()
    }, [graph])

    const toggleExpand = (nodeId: number) => {
        savedScrollTopRef.current = scrollRef.current?.scrollTop ?? null
        setExpandedNodes((prev) => {
            const next = new Set(prev)
            if (next.has(nodeId)) {
                next.delete(nodeId)
            } else {
                next.add(nodeId)
            }
            return next
        })
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
        let nodeName = graph.nodeNames[nodeId]!
        submitJob(`merge${nodeName}`, {
            type: "mergeChildren",
            nodeName,
            graph,
        })
    }

    const showIcicleGraph = (nodeId: number) => {
        let nodeName = graph.nodeNames[nodeId]!
        submitJob(`icicle${nodeName}${nodeId}`, {
            type: "icicleGraph",
            nodeId,
            graph,
        })
    }

    const rootValue = graph.values[rootNode]

    const handleNodeClick = useCallback(
        (nodeId: number) => {
            setRootNode(nodeId)
            handleMouseLeave()
        },
        [setRootNode],
    )

    return (
        <>
            <style ref={hoverStyleRef} />
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
                <ColorContext.Provider
                    value={{
                        center: colorCenter,
                        width: colorWidth,
                        amount: colorAmount,
                        distribution: colorDistribution,
                    }}
                >
                    <GraphContext.Provider value={{ graph }}>
                        <CollapseContext.Provider
                            value={{ expandedNodes, toggleExpand }}
                        >
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
                                {rootValue !== undefined && (
                                    <svg
                                        className={"flamegraph-svg"}
                                        style={{
                                            width: "100%",
                                            marginTop: "auto",
                                            flexShrink: 0,
                                        }}
                                        height={svgHeight}
                                        viewBox={`${viewLeft} 0 ${zoomWidth} ${svgHeight}`}
                                        preserveAspectRatio="none"
                                        onMouseLeave={handleMouseLeave}
                                        onMouseMove={handleMouseMove}
                                        onMouseDown={handleMouseDown}
                                        ref={svgRef}
                                    >
                                        {svgWidth && (
                                            <FlamegraphNode
                                                nodeId={rootNode}
                                                xOffset={0n}
                                                depth={0}
                                                svgWidth={svgWidth}
                                                svgHeight={svgHeight}
                                                totalValue={rootValue}
                                                onClick={handleNodeClick}
                                                parentValue={null}
                                                zoomWidth={zoomWidth}
                                            />
                                        )}
                                    </svg>
                                )}
                            </div>
                            <NodeDetails ref={detailsRef} />
                        </CollapseContext.Provider>
                    </GraphContext.Provider>
                </ColorContext.Provider>
            </Stack>
        </>
    )
}
