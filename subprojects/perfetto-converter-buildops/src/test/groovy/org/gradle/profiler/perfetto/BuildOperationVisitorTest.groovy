package org.gradle.profiler.perfetto

import spock.lang.Specification

class BuildOperationVisitorTest extends Specification {

    def testTrace = [
        new BuildOperationStart(1, "root1", 0, null, null, null),
        new BuildOperationStart(2, "root1-child1", 10, null, null, 1),
        new BuildOperationFinish(2, 20),
        new BuildOperationStart(3, "root1-child2", 30, null, null, 1),
        new BuildOperationFinish(3, 40),
        new BuildOperationFinish(1, 100),
        new BuildOperationStart(4, "root2", 150, null, null, null),
        new BuildOperationStart(5, "root2-child1", 151, null, null, 4),
        new BuildOperationStart(6, "root2-child1-child1", 151, null, null, 5),
        new BuildOperationFinish(6, 153),
        new BuildOperationStart(7, "root2-child1-child2", 155, null, null, 5),
        new BuildOperationFinish(7, 156),
        new BuildOperationFinish(5, 156),
        new BuildOperationStart(8, "root2-child2", 157, null, null, 4),
        new BuildOperationFinish(8, 158),
        new BuildOperationFinish(4, 160),
    ]

    def "build operation trace is traversed"() {
        when:
        def actualTraversedIds = collectVisitedIds(testTrace)

        then:
        actualTraversedIds == [1, 2, 3, 4, 5, 6, 7, 8]
    }

    private static List<Integer> collectVisitedIds(List<BuildOperationRecord> traversal) {
        def ids = []
        BuildOperationVisitor.visitLogs(traversal.stream(), new BuildOperationVisitor() {
            @Override
            BuildOperationFinishVisitor visit(BuildOperationStart start) {
                ids.add(start.id as int)
                return { s, f -> }
            }

            @Override
            void visit(BuildOperationProgress progress) {}
        })
        return ids
    }
}
