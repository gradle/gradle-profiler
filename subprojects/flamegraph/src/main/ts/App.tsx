import React, {
    createContext,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
    forwardRef,
} from "react"
import {
    type Job,
    type StackGraph,
    type WorkerParams,
    type WorkerResponse,
} from "./worker"
import DataWorker from "./worker?worker&inline"
import useWorkerPool from "./useWorkerPool.ts"
import { Grow, Row, Stack } from "./containers.tsx"
import { ENCODED_DEMO_STACKS } from "./demo.ts"

/**
 * Nodes with pixel width below this threshold will not be rendered.
 */
const CULLING_THRESHOLD_PX = 5

/**
 * The number of consecutive nodes with the same width required to make a node collapsible.
 */
const COLLAPSE_THRESHOLD = 2

/**
 * The height of each node, in pixels.
 */
const NODE_HEIGHT = 22

/**
 * The space between the left edge of a node and its text, in pixels.
 */
const NODE_TEXT_PADDING_LEFT = 5

/**
 * The size of the collapse/expand button icon, in pixels.
 */
const COLLAPSE_BUTTON_SIZE = 12

/**
 * The horizontal width of the SVG coordinate system.
 */
const COORDINATE_WIDTH = 100_000

const generateColorFor = (hue: number): string => {
    const lightness = 50
    return `hsl(${hue}, 100%, ${lightness}%)`
}

const hashString = (str: string, distribution: number): number => {
    let hash = 0
    for (let i = 0; i < str.length; i++) {
        hash = (hash << 5) - hash + str.charCodeAt(i)
        hash |= 0
    }
    return (Math.abs(hash) % distribution) / distribution
}

const getParts = (name: string): string[] => {
    // Strip method call and line number if present
    let cleanName = name
    const parenIdx = name.indexOf("(")
    if (parenIdx !== -1) {
        cleanName = name.substring(0, parenIdx)
    } else if (name.endsWith("_[j]")) {
        cleanName = name.substring(0, name.length - 4)
    }

    return cleanName
        .split(/[./:]/)
        .map((x) =>
            x.indexOf("$") !== -1 ? x.substring(0, x.indexOf("$")) : x,
        )
        .filter((p) => p.length > 0)
}

/**
 * Given a string, deterministically generate a color.
 */
const colorFor = (str: string, colorContext: ColorContextType): string => {
    const { center, width, amount, distribution } = colorContext
    const parts = getParts(str)

    let hueAccumulator = 0
    let currentDivisor = 1
    for (let i = 0; i < parts.length; i++) {
        // Shift hash from [0, 1] to [-0.5, 0.5] to center it around the 'center' hue.
        hueAccumulator +=
            (hashString(parts[i]!, distribution) - 0.5) *
            (width / currentDivisor)
        currentDivisor *= amount
    }

    // Ensure the hue is always positive before modulo.
    const hue = (center + hueAccumulator + 3600) % 360
    return generateColorFor(hue)
}

const simpleName = (fullName: string): string => {
    if (fullName.endsWith("_[j]")) {
        const packageIdx = fullName.lastIndexOf("/")
        if (packageIdx !== -1) {
            return fullName.substring(packageIdx + 1, fullName.length - 4)
        }
    }
    return fullName
}

const getSameWidthChain = (nodeId: number, graph: StackGraph): number[] => {
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

interface CollapseContextType {
    expandedNodes: Set<number>
    toggleExpand: (nodeId: number) => void
}

const CollapseContext = createContext<CollapseContextType>({
    expandedNodes: new Set(),
    toggleExpand: () => {},
})

const FlamegraphNode = ({
    nodeId,
    graph,
    xOffset,
    depth,
    svgWidth,
    svgHeight,
    totalValue,
    onClick,
    parentValue,
}: {
    nodeId: number
    graph: StackGraph
    xOffset: bigint
    depth: number
    svgWidth: number
    svgHeight: number
    totalValue: bigint
    onClick: (nodeId: number) => void
    parentValue: bigint | null
}) => {
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
            (Number(childValue) / Number(totalValue)) * svgWidth
        let element
        if (childWidthPx >= CULLING_THRESHOLD_PX) {
            element = (
                <FlamegraphNode
                    key={childId}
                    nodeId={childId}
                    graph={graph}
                    xOffset={childXOffset}
                    depth={depth + 1}
                    totalValue={totalValue}
                    svgWidth={svgWidth}
                    svgHeight={svgHeight}
                    onClick={onClick}
                    parentValue={value}
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
    const horizontalScale = COORDINATE_WIDTH / svgWidth

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

                {rectWidth * (svgWidth / COORDINATE_WIDTH) >
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
                        {simpleName(name)}
                    </text>
                )}
            </svg>
            {childElements}
        </>
    )
}

const nodeDetails = (nodeId: number, graph: StackGraph): string => {
    const name = graph.nodeNames[nodeId]
    const value = graph.values[nodeId]
    if (value == undefined || name == undefined) {
        return "Malformed graph. Missing node data."
    }

    return `${name} ${BigInt.asIntN(64, value).toLocaleString()} samples`
}

const NodeDetails = forwardRef<HTMLSpanElement, {}>((_, ref) => {
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

interface ColorContextType {
    center: number
    width: number
    amount: number
    distribution: number
}

const ColorContext = createContext<ColorContextType>({
    center: 0,
    width: 60,
    amount: 1.5,
    distribution: 1000,
})

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

const Flamegraph: React.FC<{
    graph: StackGraph
    rootNode: number
    setRootNode: (nodeId: number) => void
    submitJob: (id: string, job: Job) => void
}> = ({ graph, rootNode, setRootNode, submitJob }) => {
    const svgRef = useRef<SVGSVGElement | null>(null)
    const [svgWidth, setSvgWidth] = useState<number | null>(null)
    const scrollRef = useRef<HTMLDivElement | null>(null)
    const detailsRef = useRef<HTMLSpanElement | null>(null)
    const hoverStyleRef = useRef<HTMLStyleElement | null>(null)

    const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set())
    const savedScrollTopRef = useRef<number | null>(null)
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

    // Measure the size of the SVG
    useEffect(() => {
        const svg = svgRef.current
        if (!svg) {
            return
        }

        const resizeObserver = new ResizeObserver(() => {
            setSvgWidth(svg.getBoundingClientRect().width)
        })

        resizeObserver.observe(svg)
        setSvgWidth(svg.getBoundingClientRect().width)

        return () => {
            resizeObserver.disconnect()
        }
    }, [svgRef.current])

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
        const minValueThresholdRatio = CULLING_THRESHOLD_PX / svgWidth

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
    }, [graph, rootNode, svgWidth, expandedNodes])

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

    const handleMouseLeave = () => {
        if (detailsRef.current) {
            detailsRef.current.textContent = "Hover for details, click to zoom"
        }
        if (hoverStyleRef.current) {
            hoverStyleRef.current.textContent = ""
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
    const svgHeight = maxDepth * NODE_HEIGHT

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

    useEffect(() => {
        handleMouseLeave()
    }, [graph])

    const [colorCenter, setColorCenter] = useState(98)
    const [colorWidth, setColorWidth] = useState(100)
    const [colorAmount, setColorAmount] = useState(1.67)
    const [colorDistribution, setColorDistribution] = useState(1199)

    return (
        <>
            <style ref={hoverStyleRef} />
            <Row
                wide
                style={{
                    position: "absolute",
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
                    <button
                        onClick={() => setRootNode(0)}
                        disabled={rootNode === 0}
                    >
                        Reset
                    </button>
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
                        Amount ({colorAmount.toFixed(2)})
                        <Slider
                            min={1}
                            max={3}
                            value={colorAmount}
                            onChange={setColorAmount}
                        />
                    </Row>
                    <Row>
                        Distribution ({Math.round(colorDistribution)})
                        <Slider
                            min={1}
                            max={5000}
                            value={colorDistribution}
                            onChange={setColorDistribution}
                        />
                    </Row>
                    <button
                        onClick={() => showMergedSubgraph(rootNode)}
                        disabled={rootNode === 0}
                    >
                        Merge
                    </button>
                    <button onClick={() => showIcicleGraph(rootNode)}>
                        Icicle
                    </button>
                </Stack>
            </Row>

            <CollapseContext.Provider value={{ expandedNodes, toggleExpand }}>
                <ColorContext.Provider
                    value={{
                        center: colorCenter,
                        width: colorWidth,
                        amount: colorAmount,
                        distribution: colorDistribution,
                    }}
                >
                    <Stack tall style={{ position: "relative" }}>
                        <div
                            style={{
                                flexGrow: 1,
                                overflowY: "auto",
                                display: "flex",
                                paddingBottom: NODE_HEIGHT,
                                overflowAnchor: "none",
                            }}
                            ref={scrollRef}
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
                                    viewBox={`0 0 ${COORDINATE_WIDTH} ${svgHeight}`}
                                    preserveAspectRatio="none"
                                    onMouseLeave={handleMouseLeave}
                                    onMouseMove={handleMouseMove}
                                    ref={svgRef}
                                >
                                    {svgWidth && (
                                        <FlamegraphNode
                                            nodeId={rootNode}
                                            graph={graph}
                                            xOffset={0n}
                                            depth={0}
                                            svgWidth={svgWidth}
                                            svgHeight={svgHeight}
                                            totalValue={rootValue}
                                            onClick={(nodeId) => {
                                                setRootNode(nodeId)
                                                handleMouseLeave()
                                            }}
                                            parentValue={null}
                                        />
                                    )}
                                </svg>
                            )}
                        </div>
                        <NodeDetails ref={detailsRef} />
                    </Stack>
                </ColorContext.Provider>
            </CollapseContext.Provider>
        </>
    )
}

const FlamegraphTab: React.FC<{
    state: WorkerState
    setState: (state: WorkerState) => void
    submitJob: (id: string, job: Job, transfer: Transferable[]) => void
}> = ({ state, setState, submitJob }) => {
    const graphState = state.graph
    return (
        <Grow>
            {state.progress && <div>{state.progress}</div>}
            {state.error && (
                <>
                    <div>Error: {state.error.message}</div>
                    <div>
                        {state.error.stack?.split("\n").map((line, index) => (
                            <div key={index}>{line}</div>
                        ))}
                    </div>
                </>
            )}
            {graphState ? (
                <Flamegraph
                    graph={graphState.graph}
                    rootNode={graphState.rootNode}
                    setRootNode={(nodeId) => {
                        setState({
                            ...state,
                            graph: { ...graphState, rootNode: nodeId },
                        })
                    }}
                    submitJob={(id, job) => submitJob(id, job, [])}
                />
            ) : null}
        </Grow>
    )
}

interface GraphState {
    graph: StackGraph
    rootNode: number
}

interface WorkerState {
    error?: {
        message: string
        stack?: string
    }
    progress?: string
    graph?: GraphState
}

const App = (): React.JSX.Element => {
    const runJob = useWorkerPool<string, WorkerParams, WorkerResponse>(
        DataWorker,
    )

    const fileInputRef = useRef<HTMLInputElement | null>(null)
    const [customStackName, setCustomStackName] = useState("")

    const [selectedTab, setSelectedTab] = useState<string | null>(null)
    const [allTabData, setAllAllTabData] = useState<Map<string, WorkerState>>(
        new Map(),
    )
    const setTabData = (id: string, newData: WorkerState) => {
        setAllAllTabData((oldData) => {
            const updated = new Map(oldData)
            updated.set(id, newData)
            return updated
        })
        if (selectedTab == null) {
            setSelectedTab(id)
        }
    }

    const deleteTab = (id: string) => {
        setAllAllTabData((oldData) => {
            const updated = new Map(oldData)
            updated.delete(id)
            return updated
        })
        if (selectedTab === id) {
            setSelectedTab(null)
        }
    }

    const submitJob = React.useCallback(
        async (id: string, job: Job, transfer: Transferable[]) => {
            setTabData(id, { progress: "Crunching the numbers..." })

            const params: WorkerParams = { job: job }
            try {
                const result = await runJob(id, params, transfer)
                if ("error" in result) {
                    setTabData(id, { error: result.error })
                    return
                } else if ("result" in result) {
                    setTabData(id, {
                        graph: { graph: result.result.graph, rootNode: 0 },
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
        [runJob],
    )

    const loadStacks = React.useCallback(
        async (stackName: string) => {
            setCustomStackName("")
            setTabData(stackName, { progress: "Downloading stack file..." })

            const response = await fetch(stackName)
            if (!response.ok) {
                throw new Error(
                    `HTTP error! status: ${response.status} - Could not find stacks file`,
                )
            }
            const stream = response.body
            if (!stream) {
                throw new Error("No response body stream found")
            }
            await submitJob(
                stackName,
                {
                    type: "parseStream",
                    stream,
                },
                [stream],
            )
        },
        [submitJob],
    )

    useEffect(() => {
        let stacksObj = window.__ENCODED_EMBEDDED_STACKS__
        if (stacksObj) {
            for (const [stackName, encodedStack] of Object.entries(stacksObj)) {
                submitJob(
                    stackName,
                    { type: "parseEncodedData", encodedData: encodedStack },
                    [],
                )
            }
            delete window.__ENCODED_EMBEDDED_STACKS__
        } else {
            // No embedded stacks. Load the demo stacks.
            submitJob(
                "demo",
                { type: "parseEncodedData", encodedData: ENCODED_DEMO_STACKS },
                [],
            )
        }
    }, [])

    const selectedTabData = selectedTab ? allTabData.get(selectedTab) : null
    return (
        <Stack tall>
            <div>
                {[...allTabData.keys()].map((id) => (
                    <button
                        key={id}
                        onMouseDown={(e) => {
                            if (e.button === 1) {
                                deleteTab(id)
                            }
                        }}
                        onClick={() => setSelectedTab(id)}
                    >
                        {id}
                    </button>
                ))}
                <input
                    value={customStackName}
                    placeholder={"Stack name"}
                    onChange={(e) => setCustomStackName(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") {
                            loadStacks(customStackName)
                        }
                    }}
                />
                <button
                    onClick={() => loadStacks(customStackName)}
                    disabled={customStackName === ""}
                >
                    Load
                </button>
                <input
                    type="file"
                    style={{ display: "none" }}
                    ref={fileInputRef}
                    onChange={(e) => {
                        const file = e.target.files?.[0]
                        if (file) {
                            const stream = file.stream()
                            submitJob(
                                file.name,
                                { type: "parseStream", stream },
                                [stream],
                            )
                        }
                        e.target.value = ""
                    }}
                />
                <button onClick={() => fileInputRef.current?.click()}>
                    Open file...
                </button>
            </div>
            {selectedTab && selectedTabData && (
                <FlamegraphTab
                    state={selectedTabData}
                    setState={(newState) => setTabData(selectedTab, newState)}
                    submitJob={submitJob}
                />
            )}
        </Stack>
    )
}

export default App
