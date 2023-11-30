package org.gradle.profiler.flamegraph

import spock.lang.Specification

class FlameGraphSanitizerTest extends Specification {

    def "normalizes lambda stack frames"() {
        def normalizer = FlameGraphSanitizer.NORMALIZE_LAMBDA_NAMES
        expect:
        normalizer.map(['DefaultPlanExecutor$ExecutorWorker$$Lambda$887.1827771163.execute']) == ['DefaultPlanExecutor$ExecutorWorker$$Lambda$.execute']
        normalizer.map(['DefaultPlanExecutor$ExecutorWorker$$Lambda$887/1827771163.execute']) == ['DefaultPlanExecutor$ExecutorWorker$$Lambda$.execute']
        normalizer.map(['DefaultPlanExecutor$ExecutorWorker$$Lambda$x00007fd5410e0c68.execute']) == ['DefaultPlanExecutor$ExecutorWorker$$Lambda$.execute']
    }
}
