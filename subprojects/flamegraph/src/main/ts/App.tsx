import React, {
    createContext,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react"
import { type StackGraph, type WorkerResponse } from "./worker"
import DataWorker from "./worker?worker&url"

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
    const childElements = Array.from(children.values()).map((childId) => {
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

const Flamegraph: React.FC<{ graph: StackGraph }> = ({ graph }) => {
    const [hoveredNode, setHoveredNode] = useState<number | null>(null)
    const [rootNode, setRootNode] = useState(0)

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
            for (const childId of children.values()) {
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

    const rootValue = graph.values[rootNode]
    const svgHeight = maxDepth * NODE_HEIGHT

    // Scroll to the bottom when the graph changes
    useEffect(() => {
        const container = scrollRef.current
        if (container) {
            container.scrollTop = container.scrollHeight
        }
    }, [svgHeight])

    const [colorCenter, setColorCenter] = useState(80)
    const [colorWidth, setColorWidth] = useState(0.4)

    console.log(colorCenter, colorWidth)

    return (
        <>
            <div
                style={{
                    position: "absolute",
                    display: "flex",
                    justifyContent: "flex-end",
                    width: "100%",
                }}
            >
                <div
                    style={{
                        display: "flex",
                        flexDirection: "column",
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
                    <div style={{ display: "flex", alignItems: "center" }}>
                        Center ({Math.round(colorCenter)})
                        <Slider
                            min={0}
                            max={360}
                            value={colorCenter}
                            onChange={setColorCenter}
                        />
                    </div>
                    <div style={{ display: "flex", alignItems: "center" }}>
                        Temperature ({colorWidth.toFixed(2)})
                        <Slider
                            min={0}
                            max={4}
                            value={colorWidth}
                            onChange={setColorWidth}
                        />
                    </div>
                </div>
            </div>

            <ColorContext.Provider value={{ colorCenter, colorWidth }}>
                <div
                    style={{
                        display: "flex",
                        flexDirection: "column",
                        height: "100%",
                    }}
                >
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
                </div>
            </ColorContext.Provider>
        </>
    )
}

interface WorkerState {
    error?: ErrorEvent
    result?: WorkerResponse
}

const App = (): React.JSX.Element => {
    const workerRef = React.useRef<Worker>(null)
    const [data, setData] = useState<WorkerState>({})

    // Cleanup worker on component unmount
    React.useEffect(() => {
        return () => {
            if (workerRef.current) {
                workerRef.current.terminate()
            }
        }
    }, [])

    const loadStacks = React.useCallback(async () => {
        // Clear previous state
        setData({})

        // Terminate any existing worker before starting a new one
        if (workerRef.current) {
            workerRef.current.terminate()
            workerRef.current = null
        }

        const response = await fetch("/test/more-complex-stacks.txt")
        if (!response.ok) {
            throw new Error(
                `HTTP error! status: ${response.status} - Could not find stacks file`,
            )
        }
        const stream = response.body
        if (!stream) {
            throw new Error("No response body stream found")
        }

        const worker = new Worker(DataWorker, { type: "module" })
        workerRef.current = worker
        worker.onmessage = (event) => {
            setData({ result: event.data as WorkerResponse })
            if (workerRef.current) {
                workerRef.current.terminate()
                workerRef.current = null
            }
        }
        worker.onerror = (err) => {
            setData({ error: err })
            if (workerRef.current) {
                workerRef.current.terminate()
                workerRef.current = null
            }
        }

        worker.postMessage({ stream }, [stream])
    }, [])

    if (data.result && data.result.result) {
        return <Flamegraph graph={data.result.result.graph} />
    }

    return (
        <>
            {workerRef.current && <div>Processing...</div>}
            <button onClick={loadStacks}>Render Flamegraph</button>
            {data.error && <div>Error: {data.error.message}</div>}
            {data.result?.error && (
                <>
                    <div>Error: {data.result.error.message}</div>
                    <div>
                        {data.result.error.stack
                            ?.split("\n")
                            .map((line, index) => (
                                <div key={index}>{line}</div>
                            ))}
                    </div>
                </>
            )}
        </>
    )
}

export default App
