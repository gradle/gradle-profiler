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
import { decodeAndDecompressData, toStream } from "./encoding.ts"

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

/**
 * Compute a hash from a portion of a string.
 */
const hash = (str: string, start: number, end: number): number => {
    let hash = 0
    for (let i = start; i < end; i++) {
        hash = 31 * hash + str.charCodeAt(i)
    }
    return hash
}

const hueFor = (str: string, start: number, end: number): number => {
    return hash(str, start, end) % 360
}

const partialHueFor = (
    packageName: string,
    offset: number,
    colorContext: ColorContextType,
): number => {
    const nextPart = packageName.indexOf("/", offset)
    if (nextPart === -1) {
        return hueFor(packageName, offset, packageName.length)
    } else {
        const part = hueFor(packageName, offset, nextPart)
        let rest = partialHueFor(packageName, nextPart + 1, colorContext)
        return (
            (part + rest * colorContext.colorWidth) /
            (colorContext.colorWidth + 1)
        )
    }
}

const totalHueFor = (
    packageName: string,
    colorContext: ColorContextType,
): number => {
    return (
        (colorContext.colorCenter +
            colorContext.colorWidth *
                partialHueFor(packageName, 0, colorContext)) %
        360
    )
}

const generateColorFor = (
    str: string,
    darken: boolean,
    colorContext: ColorContextType,
): string => {
    const lightness = darken ? 41 : 50
    const hue = totalHueFor(str, colorContext)
    return `hsl(${hue}, 100%, ${lightness}%)`
}

/**
 * Given a string, deterministically generate a color.
 */
const colorFor = (
    str: string,
    shouldHighlight: boolean,
    colorContext: ColorContextType,
): string => {
    const lastSlash = str.lastIndexOf("/")
    if (lastSlash !== -1) {
        const javaPackage = str.substring(0, lastSlash)
        return generateColorFor(javaPackage, shouldHighlight, colorContext)
    }
    return generateColorFor(str, shouldHighlight, colorContext)
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
    hoveredNode,
}: {
    nodeId: number
    graph: StackGraph
    xOffset: number
    depth: number
    svgWidth: number
    svgHeight: number
    totalValue: number
    onClick: (nodeId: number) => void
    hoveredNode: number | null
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

    // Determine if this node should be highlighted
    const isActiveHover = hoveredNode !== null && hoveredNode === nodeId
    const isSimilarHover =
        hoveredNode !== null && graph.nodeNames[hoveredNode] === name
    const shouldHighlight = isActiveHover || isSimilarHover

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
                hoveredNode={hoveredNode}
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
                    width={value}
                    height={NODE_HEIGHT}
                    fill={colorFor(name, shouldHighlight, colorContext)}
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
    colorWidth: number
}

const ColorContext = createContext<ColorContextType>({
    colorCenter: 50,
    colorWidth: 100,
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
        const findHoveredNode = () => {
            const target = event.target
            if (target instanceof Element) {
                const nodeIdAttr = target.getAttribute("data-node-id")
                if (nodeIdAttr) {
                    return parseInt(nodeIdAttr, 10)
                }
            }
            return null
        }

        const newHoveredNode = findHoveredNode()
        if (newHoveredNode != hoveredNode) {
            setHoveredNode(newHoveredNode)
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
    const [colorWidth, setColorWidth] = useState(0.4)

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
                    <Row>
                        Temperature ({colorWidth.toFixed(2)})
                        <Slider
                            min={0}
                            max={4}
                            value={colorWidth}
                            onChange={setColorWidth}
                        />
                    </Row>

                    <button
                        onClick={() => showMergedSubgraph(rootNode)}
                        disabled={rootNode === 0}
                    >
                        Merge
                    </button>
                </Stack>
            </Row>

            <ColorContext.Provider value={{ colorCenter, colorWidth }}>
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
                                        hoveredNode={hoveredNode}
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
            {state.graph ? (
                <Flamegraph
                    graph={state.graph.graph}
                    rootNode={state.graph.rootNode}
                    setRootNode={(nodeId) => {
                        setState({
                            ...state,
                            graph: { ...state.graph, rootNode: nodeId },
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
                    error: err instanceof Error ? err.message : String(err),
                })
            }
        },
        [runJob],
    )

    const loadStacks = React.useCallback(
        async (stackName: string) => {
            setCustomStackName("")
            setTabData(stackName, { progress: "Downloading stack file..." })

            const response = await fetch(`/test/${stackName}.txt`)
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

    // Load the demo stacks
    useEffect(() => {
        const stream = toStream(decodeAndDecompressData(ENCODED_DEMO_STACKS))
        submitJob(
            "demo",
            {
                type: "parseStream",
                stream,
            },
            [stream],
        )
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
            {selectedTabData && (
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
