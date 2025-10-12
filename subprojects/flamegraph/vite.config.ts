import type { UserConfig } from "vite"
import react from "@vitejs/plugin-react"

export default {
    root: "src/main/ts",
    publicDir: "../public",
    build: {
        outDir: "../../../build/vite",
        emptyOutDir: true,
    },
    plugins: [react()],
} satisfies UserConfig
