import React, {
    createContext,
    useEffect,
    useMemo,
    useRef,
    useState,
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
 * The height of each node, in pixels.
 */
const NODE_HEIGHT = 22

/**
 * The space between the left edge of a node and its text, in pixels.
 */
const NODE_TEXT_PADDING_LEFT = 5

const generateColorFor = (hue: number): string => {
    const lightness = 50
    return `hsl(${hue}, 100%, ${lightness}%)`
}

/**
 * Given a string, deterministically generate a color.
 */
const colorFor = (str: string, colorContext: ColorContextType): string => {
    const lastSlash = str.lastIndexOf("/")
    if (lastSlash !== -1) {
        return generateColorFor(colorContext.colorCenter)
    }
    return generateColorFor(colorContext.colorCenter)
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

const FlamegraphNode = ({
    nodeId,
    graph,
    xOffset,
    depth,
    svgWidth,
    svgHeight,
    totalValue,
    onClick,
}: {
    nodeId: number
    graph: StackGraph
    xOffset: number
    depth: number
    svgWidth: number
    svgHeight: number
    totalValue: number
    onClick: (nodeId: number) => void
}) => {
    const value = graph.values[nodeId]
    if (value == undefined) {
        throw new Error("Malformed graph: missing node value")
    }

    const name = graph.nodeNames[nodeId]
    if (name == undefined) {
        throw new Error("Malformed graph: missing node name")
    }

    const children = graph.children[nodeId]
    if (children == undefined) {
        throw new Error("Malformed graph: missing children map")
    }

    // Do not render nodes that are too small.
    const widthInPixels = (value / totalValue) * svgWidth
    if (widthInPixels < CULLING_THRESHOLD_PX) {
        return null
    }

    // Recursively render children
    let childXOffset = xOffset
    const childElements = children.map((childId) => {
        const childValue = graph.values[childId]
        if (childValue == null) {
            throw new Error("Malformed graph: missing child value")
        }
        const element = (
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
            />
        )
        childXOffset += childValue
        return element
    })

    const y = svgHeight - (depth + 1) * NODE_HEIGHT

    // To counteract the text stretching caused by preserveAspectRatio="none", we apply an inverse
    // horizontal scale transform.
    const horizontalScale = totalValue > 0 ? totalValue / svgWidth : 0

    const colorContext = React.useContext(ColorContext)

    return (
        <>
            <g
                transform={`translate(${xOffset}, ${y})`}
                onClick={(e) => {
                    e.stopPropagation() // Prevent click from bubbling to parent nodes
                    onClick(nodeId)
                }}
                style={{ cursor: "pointer" }}
            >
                <rect
                    data-node-id={nodeId}
                    data-name={name}
                    width={value}
                    height={NODE_HEIGHT}
                    fill={colorFor(name, colorContext)}
                >
                    <title>{nodeDetails(nodeId, graph)}</title>
                </rect>

                {widthInPixels > NODE_TEXT_PADDING_LEFT && (
                    <text
                        x={NODE_TEXT_PADDING_LEFT}
                        y={NODE_HEIGHT / 2}
                        dy=".35em"
                        transform={`scale(${horizontalScale}, 1)`}
                        style={{ pointerEvents: "none" }}
                    >
                        {simpleName(name)}
                    </text>
                )}
            </g>
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

    return `${name} ${value.toLocaleString()} samples`
}

const NodeDetails: React.FC<{ nodeId: number | null; graph: StackGraph }> = ({
    nodeId,
    graph,
}) => {
    if (nodeId == null) {
        return <span>Hover for details, click to zoom</span>
    }

    const renderedDetails = nodeDetails(nodeId, graph)
    return <span>{renderedDetails}</span>
}

interface ColorContextType {
    colorCenter: number
}

const ColorContext = createContext<ColorContextType>({
    colorCenter: 50,
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
            min={min}
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
    const [hoveredNode, setHoveredNode] = useState<number | null>(null)

    const svgRef = useRef<SVGSVGElement | null>(null)
    const [svgWidth, setSvgWidth] = useState<number | null>(null)

    const scrollRef = useRef<HTMLDivElement | null>(null)

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
        if (!graph || graph.values.length === 0) {
            return 0
        }

        let max = 0
        const queue: Array<[number, number]> = [[rootNode, 1]]

        let element: [number, number] | undefined
        while ((element = queue.shift()) !== undefined) {
            const [nodeId, depth] = element
            max = Math.max(max, depth)
            const children = graph.children[nodeId]
            if (!children) {
                throw new Error("Malformed graph: missing children map")
            }
            for (const childId of children) {
                queue.push([childId, depth + 1])
            }
        }

        return max
    }, [graph, rootNode])

    const handleMouseMove: React.MouseEventHandler<SVGSVGElement> = (event) => {
        const svg = svgRef.current
        if (!svg) return

        svg.querySelectorAll("rect.similar-hover").forEach((el) => {
            el.classList.remove("similar-hover")
        })

        const target = event.target
        if (target instanceof SVGElement && target.tagName === "rect") {
            const name = target.getAttribute("data-name")
            const nodeId = target.getAttribute("data-node-id")
            setHoveredNode(nodeId ? parseInt(nodeId, 10) : null)

            if (name) {
                const similarNodes = svg.querySelectorAll(
                    `rect[data-name="${name}"]`,
                )
                similarNodes.forEach((node) => {
                    node.classList.add("similar-hover")
                })
            }
        } else {
            setHoveredNode(null)
        }
    }

    if (!graph || graph.values.length === 0 || !graph.values[0]) {
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

    // Scroll to the bottom when the graph changes
    useEffect(() => {
        const container = scrollRef.current
        if (container) {
            container.scrollTop = container.scrollHeight
        }
    }, [svgHeight])

    useEffect(() => {
        setHoveredNode(null)
    }, [graph])

    const [colorCenter, setColorCenter] = useState(80)

    return (
        <>
            <Row
                wide
                style={{
                    position: "absolute",
                    justifyContent: "flex-end",
                }}
            >
                <Stack
                    style={{
                        width: 500,
                        background: "rgba(0, 0, 0, 0.6)",
                        marginRight: "40px",
                        marginTop: "20px",
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
                    <button
                        onClick={() => showMergedSubgraph(rootNode)}
                        disabled={rootNode === 0}
                    >
                        Merge
                    </button>
                    <button
                        onClick={() => showIcicleGraph(rootNode)}
                    >
                        Icicle
                    </button>
                </Stack>
            </Row>

            <ColorContext.Provider value={{ colorCenter }}>
                <Stack tall>
                    <div
                        style={{
                            flexGrow: 1,
                            overflowY: "auto",
                            display: "flex",
                        }}
                        ref={scrollRef}
                    >
                        {rootValue && (
                            <svg
                                className={"flamegraph-svg"}
                                style={{
                                    width: "100%",
                                    marginTop: "auto",
                                }}
                                height={svgHeight}
                                viewBox={`0 0 ${rootValue} ${svgHeight}`}
                                preserveAspectRatio="none"
                                onMouseLeave={() => setHoveredNode(null)}
                                onMouseMove={handleMouseMove}
                                ref={svgRef}
                            >
                                {svgWidth && (
                                    <FlamegraphNode
                                        nodeId={rootNode}
                                        graph={graph}
                                        xOffset={0}
                                        depth={0}
                                        svgWidth={svgWidth}
                                        svgHeight={svgHeight}
                                        totalValue={rootValue}
                                        onClick={(nodeId) => {
                                            setRootNode(nodeId)
                                            setHoveredNode(null)
                                        }}
                                    />
                                )}
                            </svg>
                        )}
                    </div>

                    <NodeDetails nodeId={hoveredNode} graph={graph} />
                </Stack>
            </ColorContext.Provider>
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
                    <button key={id} onClick={() => setSelectedTab(id)}>
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
