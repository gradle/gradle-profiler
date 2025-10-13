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
    WorkerFactory: new () => Worker,
): ((id: I, message: P, transfer?: Transferable[]) => Promise<R>) => {
    const workerPool = useRef<Worker[]>([])
    const idleWorkers = useRef<Worker[]>([])
    const jobQueue = useRef<Job<I, P, R>[]>([])
    const activeJobs = useRef<Map<Worker, Job<I, P, R>>>(new Map())

    const dispatch = useCallback(() => {
        if (jobQueue.current.length > 0 && idleWorkers.current.length > 0) {
            const job = jobQueue.current.shift()!
            const worker = idleWorkers.current.shift()!

            activeJobs.current.set(worker, job)
            worker.postMessage(job.message, job.transfer || [])
        }
    }, [])

    const onWorkerMessage = useCallback(
        (event: MessageEvent, worker: Worker) => {
            const job = activeJobs.current.get(worker)
            if (job) {
                job.resolve(event.data)
                activeJobs.current.delete(worker)
                idleWorkers.current.push(worker)
                dispatch()
            }
        },
        [dispatch],
    )

    const onWorkerError = useCallback(
        (error: ErrorEvent, worker: Worker) => {
            const job = activeJobs.current.get(worker)
            if (job) {
                job.reject(error)
                activeJobs.current.delete(worker)
                idleWorkers.current.push(worker)
                dispatch()
            }
        },
        [dispatch],
    )

    // Initialize worker pool
    useEffect(() => {
        for (let i = 0; i < POOL_SIZE; i++) {
            const worker = new WorkerFactory()
            worker.onmessage = (event) => onWorkerMessage(event, worker)
            worker.onerror = (error) => onWorkerError(error, worker)
            workerPool.current.push(worker)
            idleWorkers.current.push(worker)
        }

        return () => {
            workerPool.current.forEach((worker) => worker.terminate())
            workerPool.current = []
            idleWorkers.current = []
            jobQueue.current = []
            activeJobs.current.clear()
        }
    }, [WorkerFactory, onWorkerMessage, onWorkerError])

    return useCallback(
        (id: I, message: P, transfer: Transferable[] = []): Promise<R> => {
            return new Promise((resolve, reject) => {
                jobQueue.current.push({
                    id,
                    message,
                    transfer,
                    resolve,
                    reject,
                })
                dispatch()
            })
        },
        [dispatch],
    )
}
