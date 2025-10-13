import { useCallback, useEffect, useRef } from "react"

const POOL_SIZE = navigator.hardwareConcurrency || 2

interface Job<I, P, R> {
    id: I
    message: P
    transfer?: Transferable[]
    resolve: (value: R) => void
    reject: (reason?: any) => void
}

/**
 * A React hook that manages a pool of Web Workers to execute tasks concurrently.
 */
export default <I, P, R>(
    workerUrl: string,
): ((id: I, message: P, transfer: Transferable[]) => Promise<R>) => {
    const workerPool = useRef<Worker[]>([])
    const idleWorkers = useRef<Worker[]>([])
    const jobQueue = useRef<Job<I, P, R>[]>([])

    // Initialize and clean up worker instances and associated state.
    useEffect(() => {
        for (let i = 0; i < POOL_SIZE; i++) {
            const worker = new Worker(workerUrl, { type: "module" })
            workerPool.current.push(worker)
            idleWorkers.current.push(worker)
        }

        return () => {
            workerPool.current.forEach((worker) => worker.terminate())
            workerPool.current = []
            idleWorkers.current = []
            jobQueue.current = []
        }
    }, [workerUrl])

    // When called, attempts to schedule the next job in the queue on a worker.
    const dispatch = useCallback(() => {
        if (jobQueue.current.length > 0 && idleWorkers.current.length > 0) {
            const job = jobQueue.current.shift()!
            const worker = idleWorkers.current.shift()!

            worker.onmessage = (event) => {
                job.resolve(event.data)
                idleWorkers.current.push(worker)
                dispatch()
            }

            worker.onerror = (error) => {
                job.reject(error)
                idleWorkers.current.push(worker)
                dispatch()
            }

            worker.postMessage(job.message, job.transfer || [])
        }
    }, [])

    return useCallback(
        (id: I, message: P, transfer: Transferable[]): Promise<R> => {
            return new Promise((resolve, reject) => {
                const job = {
                    id,
                    message,
                    transfer,
                    resolve,
                    reject,
                }
                jobQueue.current.push(job)
                dispatch()
            })
        },
        [dispatch],
    )
}
