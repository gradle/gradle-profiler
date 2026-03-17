import type { PluginOption, UserConfig } from "vite"
import react from "@vitejs/plugin-react"
import { viteSingleFile } from "vite-plugin-singlefile"
import { run } from "vite-plugin-run"
import path from "node:path"
import fs from "node:fs"

// Inject the wasm into the header as early as possible,
// so the browser can initialize the worker as ASAP.
const wasmPreloadPlugin = (isDev: boolean): PluginOption => ({
    name: "wasm-preloader",
    transformIndexHtml(_) {
        const wasmPath = path.resolve(
            __dirname,
            "build/wasm/flamegraph_wasm_bg.wasm",
        )

        let wasmSource: string
        if (isDev) {
            // In dev, we just point to the file served by Vite via the filesystem
            wasmSource = `/@fs${wasmPath}`
        } else {
            // In production, we inline the Base64 binary
            const buffer = fs.readFileSync(wasmPath)
            wasmSource = `data:application/wasm;base64,${buffer.toString("base64")}`
        }

        return [
            {
                tag: "script",
                attrs: { type: "text/javascript" },
                children: `
                    (function() {
                        const wasmSource = "${wasmSource}";
                        const isDataUri = wasmSource.startsWith("data:");

                        // Use streaming for dev/files, fallback to ArrayBuffer for prod/data-uris
                        window.WASM_MODULE_PROMISE = isDataUri
                            ? fetch(wasmSource).then(r => r.arrayBuffer()).then(bytes => WebAssembly.compile(bytes))
                            : WebAssembly.compileStreaming(fetch(wasmSource));

                        window.WASM_MODULE_PROMISE.catch(e => console.error("WASM Preload Failed:", e));
                    })();
                `,
                injectTo: "head-prepend",
            },
        ]
    },
})

export default ({ command }: { command: string }): UserConfig => {
    const isDev = command === "serve"
    return {
        root: "src/main/ts",
        publicDir: "../public",
        build: {
            outDir: "../../../build/vite",
            emptyOutDir: true,
            sourcemap: "hidden",
            assetsInlineLimit: 100000000, // Ensure wasm is always inlined
        },
        base: "./",
        resolve: {
            alias: {
                "@flamegraph-wasm": path.resolve(__dirname, "build/wasm"),
            },
        },
        assetsInclude: ["**/*.wasm"],
        plugins: [
            react(),
            viteSingleFile(),
            wasmPreloadPlugin(isDev),
            {
                name: "watch-rust-dir",
                configureServer(server) {
                    // Configure the dev server to watch the Rust directory for changes
                    server.watcher.add(path.resolve(__dirname, "src/main/rust"))
                    server.watcher.add(path.resolve(__dirname, "build/wasm"))
                },
            } satisfies PluginOption,
            isDev &&
                run({
                    silent: false,
                    input: [
                        {
                            // Trigger compilation of rust sources whenever a change is detected
                            name: "Compile Rust",
                            startup: false,
                            run: [
                                path.resolve(__dirname, "../../gradlew"),
                                ":flamegraph:compileRust",
                            ],
                            condition: (file) =>
                                file.endsWith(".rs") ||
                                file.endsWith("Cargo.toml"),
                        },
                    ],
                }),
        ].filter(Boolean) as PluginOption[],
    }
}
