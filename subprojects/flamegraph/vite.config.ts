import type { UserConfig } from "vite"
import react from "@vitejs/plugin-react"
import { viteSingleFile } from "vite-plugin-singlefile"

export default {
    root: "src/main/ts",
    publicDir: "../public",
    build: {
        outDir: "../../../build/vite",
        emptyOutDir: true,
    },
    base: "./",
    plugins: [react(), viteSingleFile()],
} satisfies UserConfig
