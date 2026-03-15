declare global {
    interface Window {
        WASM_MODULE_PROMISE: Promise<WebAssembly.Module>
    }
}

export {}
