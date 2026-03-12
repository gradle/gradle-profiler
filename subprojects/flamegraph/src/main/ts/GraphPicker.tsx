import React, { useRef } from "react"
import { Stack } from "./containers"

export const GraphPicker: React.FC<{
    tabIds: string[]
    selectedTab: string | null
    onSelectTab: (id: string) => void
    onDeleteTab: (id: string) => void
    onFileSelected: (file: File) => void
}> = ({ tabIds, selectedTab, onSelectTab, onDeleteTab, onFileSelected }) => {
    const fileInputRef = useRef<HTMLInputElement | null>(null)

    return (
        <Stack style={{ gap: 15 }}>
            <Stack style={{ gap: 5 }}>
                {tabIds.map((id) => (
                    <button
                        key={id}
                        style={{ textAlign: "left" }}
                        aria-pressed={selectedTab === id}
                        onMouseDown={(e) => {
                            if (e.button === 1) {
                                e.preventDefault()
                                onDeleteTab(id)
                            }
                        }}
                        onClick={() => onSelectTab(id)}
                    >
                        {id}
                    </button>
                ))}
            </Stack>
            <button onClick={() => fileInputRef.current?.click()}>
                Open file...
            </button>
            <input
                type="file"
                style={{ display: "none" }}
                ref={fileInputRef}
                onChange={(e) => {
                    const file = e.target.files?.[0]
                    if (file) {
                        onFileSelected(file)
                    }
                    e.target.value = ""
                }}
            />
        </Stack>
    )
}
