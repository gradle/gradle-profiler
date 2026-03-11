import React, { useEffect, useRef, useState } from "react"
import {
    type Job,
    type StackGraph,
    type WorkerParams,
    type WorkerResponse,
} from "./worker"
import DataWorker from "./worker?worker&inline"
import useWorkerPool from "./useWorkerPool.ts"
import { Grow, Stack } from "./containers.tsx"
import { ENCODED_DEMO_STACKS } from "./demo.ts"
import { Flamegraph } from "./Flamegraph"

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
