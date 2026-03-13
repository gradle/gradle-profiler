import React, { useEffect, useState } from "react"
import { Flamegraph } from "./Flamegraph"
import { COORDINATE_WIDTH } from "./FlamegraphNode"
import { RangeSlider } from "./RangeSlider"
import { Row, Stack } from "./containers.tsx"
import type { ColorSettings } from "./color"
import { useGraphTabs } from "./useGraphTabs"
import { useGraphHistory } from "./useGraphHistory"
import { useGraphMutation } from "./useGraphMutation"
import { GraphPicker } from "./GraphPicker"
import { ColorPicker } from "./ColorPicker"
import { GraphActions } from "./GraphActions"
import { ENCODED_DEMO_STACKS } from "./demo.ts"

type OpenPanel = "graphs" | "colors" | null

const App = (): React.JSX.Element => {
    const {
        runJob,
        allTabData,
        selectedTab,
        setSelectedTab,
        updateGraphState,
        deleteTab,
        submitJob,
        showMergedSubgraph,
        showIcicleGraph,
    } = useGraphTabs()

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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const selectedTabData = selectedTab ? allTabData.get(selectedTab) : null
    const graphState = selectedTabData?.graph ?? null

    const { rootNode, viewLeft, viewRight } = graphState
        ? graphState.history[graphState.historyIndex]!
        : { rootNode: 0, viewLeft: 0, viewRight: COORDINATE_WIDTH }

    const { setRootNode, updateZoom, goBack, goForward } =
        useGraphHistory(updateGraphState)

    const { setMutable, deleteNode } = useGraphMutation(
        updateGraphState,
        runJob,
    )

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (!e.metaKey || !selectedTab) return
            if (e.key === "[") {
                e.preventDefault()
                goBack(selectedTab)
            } else if (e.key === "]") {
                e.preventDefault()
                goForward(selectedTab)
            }
        }
        window.addEventListener("keydown", handleKeyDown)
        return () => window.removeEventListener("keydown", handleKeyDown)
    }, [goBack, goForward, selectedTab])

    const [colorSettings, setColorSettings] = useState<ColorSettings>({
        center: 98,
        width: 100,
        amount: 1.67,
        distribution: 1199,
    })

    const [openPanel, setOpenPanel] = useState<OpenPanel>(null)
    const togglePanel = (panel: Exclude<OpenPanel, null>) =>
        setOpenPanel((current) => (current === panel ? null : panel))

    return (
        <Flamegraph
            graphId={graphState?.graphId}
            rootNode={rootNode}
            setRootNode={(nodeId) =>
                selectedTab && setRootNode(selectedTab, nodeId)
            }
            viewLeft={viewLeft}
            viewRight={viewRight}
            onUpdateZoom={(left, right) =>
                selectedTab && updateZoom(selectedTab, left, right)
            }
            isMutable={graphState?.mutable ?? false}
            onDeleteNode={(nodeId) =>
                selectedTab &&
                graphState &&
                deleteNode(selectedTab, graphState.graphId, nodeId)
            }
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
                    onChange={(left, right) =>
                        selectedTab && updateZoom(selectedTab, left, right)
                    }
                />
            </Row>
            <Row style={{ justifyContent: "space-between" }}>
                <div>
                    <Stack
                        style={{
                            maxWidth: 500,
                            background: "rgba(0, 0, 0, 0.6)",
                            padding: 10,
                            paddingTop: 0,
                            gap: 15,
                            pointerEvents: "auto",
                        }}
                    >
                        <Row style={{ gap: 5 }}>
                            <button
                                aria-pressed={openPanel === "graphs"}
                                onClick={() => togglePanel("graphs")}
                            >
                                Graphs
                            </button>
                            <button
                                aria-pressed={openPanel === "colors"}
                                onClick={() => togglePanel("colors")}
                            >
                                Colors
                            </button>
                        </Row>
                        {openPanel !== null && (
                            <Stack>
                                {openPanel === "graphs" && (
                                    <GraphPicker
                                        tabIds={[...allTabData.keys()]}
                                        selectedTab={selectedTab}
                                        onSelectTab={setSelectedTab}
                                        onDeleteTab={deleteTab}
                                        onFileSelected={(file) => {
                                            const stream = file.stream()
                                            submitJob(
                                                file.name,
                                                { type: "parseStream", stream },
                                                [stream],
                                            )
                                            setSelectedTab(file.name)
                                        }}
                                    />
                                )}
                                {openPanel === "colors" && (
                                    <ColorPicker
                                        colorSettings={colorSettings}
                                        onColorChange={setColorSettings}
                                    />
                                )}
                            </Stack>
                        )}
                    </Stack>
                </div>
                <div>
                    <Stack
                        style={{
                            background: "rgba(0, 0, 0, 0.6)",
                            padding: 10,
                            paddingTop: 0,
                            pointerEvents: "auto",
                        }}
                    >
                        <Row>
                            <GraphActions
                                tabId={selectedTab}
                                graphState={graphState}
                                goBack={goBack}
                                goForward={goForward}
                                setRootNode={setRootNode}
                                showMergedSubgraph={showMergedSubgraph}
                                showIcicleGraph={showIcicleGraph}
                                setMutable={setMutable}
                            />
                        </Row>
                    </Stack>
                </div>
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
