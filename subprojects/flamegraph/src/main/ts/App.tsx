import React, { useCallback, useEffect, useRef, useState } from "react"
import {
    type Job,
    type StackGraph,
    type WorkerParams,
    type WorkerResponse,
} from "./worker"
import DataWorker from "./worker?worker&inline"
import useWorkerPool from "./useWorkerPool.ts"
import { Row, Stack } from "./containers.tsx"
import { ENCODED_DEMO_STACKS } from "./demo.ts"
import { Flamegraph } from "./Flamegraph"
import { COORDINATE_WIDTH } from "./FlamegraphNode"
import { RangeSlider } from "./RangeSlider"
import type { ColorSettings } from "./color"

const Slider: React.FC<{
    min: number
    max: number
    value: number
    onChange: (newValue: number) => void
}> = ({ min, max, value, onChange }) => {
    const resolution = 10000
    const percent = (value - min) / (max - min)
    const scaledValue = percent * resolution

    return (
        <input
            style={{ flexGrow: 1 }}
            type="range"
            min={0}
            max={resolution}
            value={scaledValue}
            onChange={(e) => {
                const v = parseInt(e.target.value, 10)
                onChange(min + (v / resolution) * (max - min))
            }}
        />
    )
}

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
    onFreeze: () => void
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
    onFreeze,
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
        <Stack
            style={{
                width: 500,
                background: "rgba(0, 0, 0, 0.6)",
                padding: 10,
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
            <button onClick={isMutable ? onFreeze : onMutate}>
                {isMutable ? "Freeze" : "Mutate"}
            </button>
        </Stack>
    )
}

interface HistoryEntry {
    rootNode: number
    viewLeft: number
    viewRight: number
}

interface GraphState {
    graph: StackGraph
    history: HistoryEntry[]
    historyIndex: number
    mutable?: boolean
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

    const [selectedTab, setSelectedTab] = useState<string | null>(null)
    const [allTabData, setAllTabData] = useState<Map<string, WorkerState>>(
        new Map(),
    )

    const setTabData = (id: string, newData: WorkerState) => {
        setAllTabData((oldData) => {
            const updated = new Map(oldData)
            updated.set(id, newData)
            return updated
        })
        if (selectedTab == null) {
            setSelectedTab(id)
        }
    }

    const deleteTab = (id: string) => {
        setAllTabData((oldData) => {
            const updated = new Map(oldData)
            updated.delete(id)
            return updated
        })
        if (selectedTab === id) {
            setSelectedTab(null)
        }
    }

    const selectedTabData = selectedTab ? allTabData.get(selectedTab) : null
    const graphState = selectedTabData?.graph ?? null

    const { rootNode, viewLeft, viewRight } = graphState
        ? graphState.history[graphState.historyIndex]!
        : { rootNode: 0, viewLeft: 0, viewRight: COORDINATE_WIDTH }

    const canGoBack = graphState ? graphState.historyIndex > 0 : false
    const canGoForward = graphState
        ? graphState.historyIndex < graphState.history.length - 1
        : false

    const setRootNode = (nodeId: number) => {
        if (!graphState || !selectedTab) return
        const newHistory = graphState.history.slice(
            0,
            graphState.historyIndex + 1,
        )
        newHistory.push({
            rootNode: nodeId,
            viewLeft: 0,
            viewRight: COORDINATE_WIDTH,
        })
        setTabData(selectedTab, {
            ...selectedTabData,
            graph: {
                ...graphState,
                history: newHistory,
                historyIndex: newHistory.length - 1,
            },
        })
    }

    const updateZoom = (left: number, right: number) => {
        if (!graphState || !selectedTab) return
        const newHistory = [...graphState.history]
        newHistory[graphState.historyIndex] = {
            ...newHistory[graphState.historyIndex]!,
            viewLeft: left,
            viewRight: right,
        }
        setTabData(selectedTab, {
            ...selectedTabData,
            graph: { ...graphState, history: newHistory },
        })
    }

    const goBack = useCallback(() => {
        if (!graphState || !canGoBack || !selectedTab) return
        setAllTabData((oldData) => {
            const updated = new Map(oldData)
            const tabState = updated.get(selectedTab)!
            updated.set(selectedTab, {
                ...tabState,
                graph: {
                    ...tabState.graph!,
                    historyIndex: tabState.graph!.historyIndex - 1,
                },
            })
            return updated
        })
    }, [graphState, canGoBack, selectedTab])

    const goForward = useCallback(() => {
        if (!graphState || !canGoForward || !selectedTab) return
        setAllTabData((oldData) => {
            const updated = new Map(oldData)
            const tabState = updated.get(selectedTab)!
            updated.set(selectedTab, {
                ...tabState,
                graph: {
                    ...tabState.graph!,
                    historyIndex: tabState.graph!.historyIndex + 1,
                },
            })
            return updated
        })
    }, [graphState, canGoForward, selectedTab])

    const isMutable = graphState?.mutable ?? false

    const onMutate = useCallback(() => {
        if (!graphState || !selectedTab) return
        setTabData(selectedTab, {
            ...selectedTabData,
            graph: { ...graphState, mutable: true },
        })
    }, [graphState, selectedTab, selectedTabData])

    const onFreeze = useCallback(() => {
        if (!graphState || !selectedTab) return
        setTabData(selectedTab, {
            ...selectedTabData,
            graph: { ...graphState, mutable: false },
        })
    }, [graphState, selectedTab, selectedTabData])

    const deleteNode = useCallback(
        async (nodeId: number) => {
            if (!graphState || !graphState.mutable || !selectedTab) return

            const capturedGraphState = graphState
            const capturedTabData = selectedTabData
            const capturedTab = selectedTab

            // Clone the buffer so we can transfer it without detaching the one
            // currently in use by the UI.
            const valuesBuffer = capturedGraphState.graph.values.buffer.slice(0)

            const result = await runJob(
                "deleteNode",
                {
                    job: {
                        type: "deleteNode",
                        nodeId,
                        graph: capturedGraphState.graph,
                    },
                },
                [valuesBuffer],
            )

            if ("result" in result) {
                setTabData(capturedTab, {
                    ...capturedTabData,
                    graph: {
                        ...capturedGraphState,
                        graph: result.result.graph,
                    },
                })
            }
        },
        [graphState, selectedTabData, selectedTab, runJob],
    )

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (!e.metaKey) return
            if (e.key === "[") {
                e.preventDefault()
                goBack()
            } else if (e.key === "]") {
                e.preventDefault()
                goForward()
            }
        }
        window.addEventListener("keydown", handleKeyDown)
        return () => window.removeEventListener("keydown", handleKeyDown)
    }, [goBack, goForward])

    const [colorCenter, setColorCenter] = useState(98)
    const [colorWidth, setColorWidth] = useState(100)
    const [colorAmount, setColorAmount] = useState(1.67)
    const [colorDistribution, setColorDistribution] = useState(1199)
    const colorSettings: ColorSettings = {
        center: colorCenter,
        width: colorWidth,
        amount: colorAmount,
        distribution: colorDistribution,
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
                        graph: {
                            graph: result.result.graph,
                            history: [
                                {
                                    rootNode: 0,
                                    viewLeft: 0,
                                    viewRight: COORDINATE_WIDTH,
                                },
                            ],
                            historyIndex: 0,
                        },
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

    const showMergedSubgraph = (
        gs: GraphState,
        tabId: string,
        nodeId: number,
    ) => {
        const nodeName = gs.graph.nodeNames[nodeId]!
        submitJob(
            `${tabId}:merge:${nodeName}`,
            { type: "mergeChildren", nodeName, graph: gs.graph },
            [],
        )
    }

    const showIcicleGraph = (gs: GraphState, tabId: string, nodeId: number) => {
        const nodeName = gs.graph.nodeNames[nodeId]!
        submitJob(
            `${tabId}:icicle:${nodeName}`,
            { type: "icicleGraph", nodeId, graph: gs.graph },
            [],
        )
    }

    useEffect(() => {
        const stacksObj = window.__ENCODED_EMBEDDED_STACKS__
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
            submitJob(
                "demo",
                { type: "parseEncodedData", encodedData: ENCODED_DEMO_STACKS },
                [],
            )
        }
    }, [])

    const [pickerOpen, setPickerOpen] = useState(false)
    const fileInputRef = useRef<HTMLInputElement | null>(null)

    return (
        <Flamegraph
            graph={graphState?.graph}
            rootNode={rootNode}
            setRootNode={setRootNode}
            viewLeft={viewLeft}
            viewRight={viewRight}
            onUpdateZoom={updateZoom}
            isMutable={isMutable}
            onDeleteNode={deleteNode}
            colorSettings={colorSettings}
        >
            <Row
                style={{
                    background: "rgba(0, 0, 0, 0.6)",
                    padding: "10px",
                    height: "40px",
                    pointerEvents: "auto",
                    alignItems: "center",
                }}
            >
                <RangeSlider
                    min={0}
                    max={COORDINATE_WIDTH}
                    valueLeft={viewLeft}
                    valueRight={viewRight}
                    onChange={updateZoom}
                />
            </Row>
            <Row style={{ justifyContent: "space-between" }}>
                <Stack
                    style={{
                        pointerEvents: "auto",
                    }}
                >
                    <button onClick={() => setPickerOpen((o) => !o)}>
                        {pickerOpen ? "−" : "Graphs"}
                    </button>
                    {pickerOpen && (
                        <Stack
                            style={{
                                maxWidth: 300,
                                background: "rgba(0, 0, 0, 0.6)",
                                padding: "10px",
                            }}
                        >
                            <Stack style={{ marginBottom: "10px" }}>
                                {[...allTabData.keys()].map((id) => (
                                    <button
                                        key={id}
                                        style={{
                                            textAlign: "left",
                                            fontWeight:
                                                selectedTab === id
                                                    ? "bold"
                                                    : "normal",
                                        }}
                                        onMouseDown={(e) => {
                                            if (e.button === 1) {
                                                e.preventDefault()
                                                deleteTab(id)
                                            }
                                        }}
                                        onClick={() => setSelectedTab(id)}
                                    >
                                        {id}
                                    </button>
                                ))}
                            </Stack>
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
                                            {
                                                type: "parseStream",
                                                stream,
                                            },
                                            [stream],
                                        )
                                        setSelectedTab(file.name)
                                    }
                                    e.target.value = ""
                                }}
                            />
                            <button
                                onClick={() => fileInputRef.current?.click()}
                            >
                                Open file...
                            </button>
                        </Stack>
                    )}
                </Stack>
                <ColorControls
                    rootNode={rootNode}
                    canGoBack={canGoBack}
                    canGoForward={canGoForward}
                    onBack={goBack}
                    onForward={goForward}
                    onReset={() => setRootNode(0)}
                    onMerge={() =>
                        graphState &&
                        selectedTab &&
                        showMergedSubgraph(graphState, selectedTab, rootNode)
                    }
                    onIcicle={() =>
                        graphState &&
                        selectedTab &&
                        showIcicleGraph(graphState, selectedTab, rootNode)
                    }
                    isMutable={isMutable}
                    onMutate={onMutate}
                    onFreeze={onFreeze}
                    colorCenter={colorCenter}
                    colorWidth={colorWidth}
                    colorAmount={colorAmount}
                    colorDistribution={colorDistribution}
                    setColorCenter={setColorCenter}
                    setColorWidth={setColorWidth}
                    setColorAmount={setColorAmount}
                    setColorDistribution={setColorDistribution}
                />
            </Row>
            <Row
                wide
                style={{
                    justifyContent: "center",
                    alignItems: "center",
                    flexGrow: 1,
                }}
            >
                {selectedTabData?.progress && (
                    <div>{selectedTabData.progress}</div>
                )}
                {selectedTabData?.error && (
                    <>
                        <div>Error: {selectedTabData.error.message}</div>
                        <div>
                            {selectedTabData.error.stack
                                ?.split("\n")
                                .map((line, index) => (
                                    <div key={index}>{line}</div>
                                ))}
                        </div>
                    </>
                )}
            </Row>
        </Flamegraph>
    )
}

export default App
