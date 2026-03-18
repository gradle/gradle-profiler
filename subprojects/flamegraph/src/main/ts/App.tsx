import React, { useCallback, useEffect, useState } from "react"
import { Flamegraph } from "./Flamegraph"
import { COORDINATE_WIDTH } from "./FlamegraphNode"
import { RangeSlider } from "./Sliders"
import { Row, Stack } from "./containers.tsx"
import type { ColorSettings } from "./color"
import { useGraphTabs } from "./useGraphTabs"
import { useGraphHistory } from "./useGraphHistory"
import { useGraphMutation } from "./useGraphMutation"
import { GraphPicker } from "./GraphPicker"
import { ColorPicker } from "./ColorPicker"
import { GraphActions } from "./GraphActions"
import { SearchPanel } from "./SearchPanel"
import { ResizablePanel } from "./ResizablePanel"

type OpenPanel = "graphs" | "colors" | null

const PANEL_STYLE = {
    background: "rgba(0, 0, 0, 0.6)",
    padding: 10,
    paddingTop: 0,
    gap: 15,
    pointerEvents: "auto" as const,
}

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
        showSimplifiedGraph,
    } = useGraphTabs()

    useEffect(() => {
        const namesEl = document.getElementById("embedded-stacks-names")

        if (namesEl) {
            const stackNames =
                namesEl.innerHTML.trim().split(",").map(atob) || []
            stackNames.forEach((name, i) => {
                const template = document.getElementById(
                    `embedded-stacks-${i}`,
                ) as HTMLTemplateElement
                if (template) {
                    const rawBase64 = template.content.textContent
                    submitJob(
                        name,
                        {
                            type: "parseEncodedData",
                            encodedData: rawBase64,
                        },
                        [],
                    )
                    template.remove()
                }
            })
            namesEl.remove()
        }
    }, [submitJob])

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

    const [openPanel, setOpenPanel] = useState<OpenPanel>("graphs")
    const togglePanel = (panel: Exclude<OpenPanel, null>) =>
        setOpenPanel((current) => (current === panel ? null : panel))

    const [searchOpen, setSearchOpen] = useState(false)
    const [searchQuery, setSearchQuery] = useState("")

    const handleScrollChange = useCallback(
        (scrollTop: number) => {
            if (selectedTab) {
                updateGraphState(selectedTab, (prev) => {
                    const newHistory = [...prev.history]
                    newHistory[prev.historyIndex] = {
                        ...newHistory[prev.historyIndex]!,
                        scrollTop,
                    }
                    return { ...prev, history: newHistory }
                })
            }
        },
        [selectedTab, updateGraphState],
    )

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
            initialScrollTop={
                graphState?.history[graphState.historyIndex]?.scrollTop
            }
            onScrollChange={handleScrollChange}
            searchQuery={searchQuery || undefined}
        >
            {/* Wrapper provides the positioning context for the center overlay,
                spanning the full flamegraph area including the range slider. */}
            <Stack wide style={{ position: "relative", flex: 1, minHeight: 0 }}>
                {/* Center: empty state / progress / errors — absolutely covers the
                    entire flamegraph overlay. Rendered first so panels in DOM
                    order paint on top without needing explicit z-index. */}
                <Stack
                    style={{
                        position: "absolute",
                        inset: 0,
                        justifyContent: "center",
                        alignItems: "center",
                        pointerEvents: "none",
                    }}
                >
                    {!selectedTab && (
                        <div style={{ opacity: 0.5 }}>
                            Load a -stacks.txt file to begin
                        </div>
                    )}
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
                </Stack>

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
                        disabled={!graphState}
                    />
                </Row>

                <Row
                    wide
                    style={{ flex: 1, minHeight: 0, alignItems: "stretch" }}
                >
                    {/* Left panel: graphs / colors. */}
                    <ResizablePanel
                        edge="right"
                        open={openPanel !== null}
                        initialWidth={450}
                        minWidth={200}
                        style={{ alignSelf: "stretch", maxWidth: "50%" }}
                        panelStyle={PANEL_STYLE}
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
                            <Stack
                                style={{
                                    flex: 1,
                                    minHeight: 0,
                                    overflowY: "auto",
                                }}
                            >
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
                    </ResizablePanel>

                    {/* Right side: GraphActions above the search panel. The column
                        stretches to full row height when search is open so the
                        ResizablePanel can fill down to NodeDetails. */}
                    <Stack
                        style={{
                            position: "relative",
                            alignItems: "flex-end",
                            alignSelf: searchOpen ? "stretch" : "flex-start",
                            marginLeft: "auto",
                            maxWidth: "50%",
                        }}
                    >
                        {/* GraphActions — own natural width, not resizable */}
                        <Stack style={{ ...PANEL_STYLE, flexShrink: 0 }}>
                            <GraphActions
                                tabId={selectedTab}
                                graphState={graphState}
                                goBack={goBack}
                                goForward={goForward}
                                setRootNode={setRootNode}
                                showMergedSubgraph={showMergedSubgraph}
                                showIcicleGraph={showIcicleGraph}
                                showSimplifiedGraph={showSimplifiedGraph}
                                setMutable={setMutable}
                            />
                        </Stack>

                        {/* Search panel — resizable width, content-driven height
                            capped at the remaining column height when open.
                            maxWidth: 100% prevents the stored width from visually
                            overflowing the column when CSS clamps the column. */}
                        <ResizablePanel
                            edge="left"
                            open={searchOpen}
                            initialWidth={350}
                            minWidth={200}
                            style={{
                                flex: searchOpen ? 1 : undefined,
                                minHeight: searchOpen ? 0 : undefined,
                                maxWidth: "100%",
                            }}
                            panelStyle={PANEL_STYLE}
                        >
                            <Row
                                style={{
                                    gap: 5,
                                    justifyContent: "flex-end",
                                    flexShrink: 0,
                                }}
                            >
                                <button
                                    aria-pressed={searchOpen}
                                    onClick={() => setSearchOpen((s) => !s)}
                                    disabled={!selectedTab}
                                >
                                    Search
                                </button>
                            </Row>
                            {searchOpen && (
                                <SearchPanel
                                    graphId={graphState?.graphId}
                                    rootNode={rootNode}
                                    searchQuery={searchQuery}
                                    onSearchQueryChange={setSearchQuery}
                                    onSelectNode={(nodeId) =>
                                        selectedTab &&
                                        setRootNode(selectedTab, nodeId)
                                    }
                                />
                            )}
                        </ResizablePanel>
                    </Stack>
                </Row>
            </Stack>
        </Flamegraph>
    )
}

export default App
