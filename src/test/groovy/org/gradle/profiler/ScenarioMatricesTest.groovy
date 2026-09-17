package org.gradle.profiler

import com.typesafe.config.Config
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ScenarioMatricesTest extends Specification {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    File scenarioFile

    def setup() {
        scenarioFile = tmpDir.newFile("scenarios.conf")
    }

    def "leaves config unchanged when no scenario-matrices block"() {
        when:
        def expanded = expand("""
            a { tasks = ["a"] }
            b { tasks = ["b"] }
        """)

        then:
        topLevelScenarioKeys(expanded) == ["a", "b"] as Set
        !expanded.hasPath("scenario-matrices")
    }

    def "expands a 2x2 cartesian product and emits fix-one auto-groups"() {
        given:
        def config = """
            a1 { tasks = ["a1"] }
            a2 { tasks = ["a2"] }
            b1 { tasks = ["b1"] }
            b2 { tasks = ["b2"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = left,  scenarios = [a1, a2] }
                        { name = right, scenarios = [b1, b2] }
                    ]
                }
            }
        """

        when:
        def expanded = expand(config)

        then:
        topLevelScenarioKeys(expanded) == [
            "a1", "a2", "b1", "b2",
            "matrix_a1_b1", "matrix_a1_b2", "matrix_a2_b1", "matrix_a2_b2",
        ] as Set

        and: "one fix-one auto-group per scenario, fixing that scenario"
        def groups = expanded.getConfig("scenario-groups")
        groups.getStringList("matrix_a1") == ["matrix_a1_b1", "matrix_a1_b2"]
        groups.getStringList("matrix_a2") == ["matrix_a2_b1", "matrix_a2_b2"]
        groups.getStringList("matrix_b1") == ["matrix_a1_b1", "matrix_a2_b1"]
        groups.getStringList("matrix_b2") == ["matrix_a1_b2", "matrix_a2_b2"]
    }

    def "expands 3 dimensions producing 2x2x2 = 8 scenarios"() {
        when:
        def expanded = expand("""
            a1 {}
            a2 {}
            b1 {}
            b2 {}
            c1 {}
            c2 {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = x, scenarios = [a1, a2] }
                        { name = y, scenarios = [b1, b2] }
                        { name = z, scenarios = [c1, c2] }
                    ]
                }
            }
        """)

        then:
        def synthesized = topLevelScenarioKeys(expanded).findAll { it.startsWith("matrix_") }
        synthesized as Set == [
            "matrix_a1_b1_c1", "matrix_a1_b1_c2",
            "matrix_a1_b2_c1", "matrix_a1_b2_c2",
            "matrix_a2_b1_c1", "matrix_a2_b1_c2",
            "matrix_a2_b2_c1", "matrix_a2_b2_c2",
        ] as Set
    }

    def "dimension order in the list controls dimension order in synthesized names"() {
        when:
        def expanded = expand("""
            a {}
            b {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = right, scenarios = [b] }
                        { name = left,  scenarios = [a] }
                    ]
                }
            }
        """)

        then:
        // The 'right' dimension is declared first, so its scenario (b) appears first in the synthesized name.
        topLevelScenarioKeys(expanded).contains("matrix_b_a")
        !topLevelScenarioKeys(expanded).contains("matrix_a_b")
    }

    def "later dimensions win on conflicts (fold-right withFallback)"() {
        when:
        def expanded = expand("""
            a { daemon = warm }
            b { daemon = cold }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = left,  scenarios = [a] }
                        { name = right, scenarios = [b] }
                    ]
                }
            }
        """)

        then:
        expanded.getConfig("matrix_a_b").getString("daemon") == "cold"
    }

    def "maps merge recursively across picked scenarios"() {
        when:
        def expanded = expand('''
            a {
                system-properties {
                    x = "1"
                    y = "2"
                }
            }
            b {
                system-properties {
                    y = "20"
                    z = "3"
                }
            }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = left,  scenarios = [a] }
                        { name = right, scenarios = [b] }
                    ]
                }
            }
        ''')

        then:
        def sp = expanded.getConfig("matrix_a_b").getConfig("system-properties")
        sp.getString("x") == "1"
        sp.getString("y") == "20"
        sp.getString("z") == "3"
    }

    def "arrays are replaced not merged (later wins)"() {
        when:
        def expanded = expand("""
            a { jvm-args = ["-Xmx1g", "-Da=1"] }
            b { jvm-args = ["-Xmx2g"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = left,  scenarios = [a] }
                        { name = right, scenarios = [b] }
                    ]
                }
            }
        """)

        then:
        expanded.getStringList("matrix_a_b.jvm-args") == ["-Xmx2g"]
    }

    def "mutator keys at distinct paths compose freely"() {
        when:
        def expanded = expand("""
            workflow { apply-abi-change-to = "Foo.java" }
            cache { clear-build-cache-before = SCENARIO }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = w, scenarios = [workflow] }
                        { name = c, scenarios = [cache] }
                    ]
                }
            }
        """)

        then:
        def merged = expanded.getConfig("matrix_workflow_cache")
        merged.getString("apply-abi-change-to") == "Foo.java"
        merged.getString("clear-build-cache-before") == "SCENARIO"
    }

    def "title composition: #description"() {
        when:
        def expanded = expand("""
            a $aBody
            b $bBody
            scenario-matrices {
                matrix {
                    $matrixTitle
                    dimensions = [
                        { name = left,  scenarios = [a] }
                        { name = right, scenarios = [b] }
                    ]
                }
            }
        """)

        then:
        def synthesized = expanded.getConfig("matrix_a_b")
        if (expectedTitle == null) {
            assert !synthesized.hasPath("title")
        } else {
            assert synthesized.getString("title") == expectedTitle
        }

        where:
        description                                  | aBody                 | bBody                | matrixTitle        | expectedTitle
        "explicit matrix and scenario titles compose"    | '{ title = "Alpha" }' | '{ title = "Beta" }' | 'title = "Matrix"' | "Matrix. Alpha. Beta"
        "missing scenario title falls back to scenario name" | '{}'                  | '{ title = "Beta" }' | ''                 | "matrix. a. Beta"
        "no titles anywhere leaves title unset"      | '{}'                  | '{}'                 | ''                 | null
        "empty matrix title omits the prefix segment"| '{ title = "Alpha" }' | '{ title = "Beta" }' | 'title = ""'       | "Alpha. Beta"
        "empty matrix title with one scenario lacking a title" | '{}'           | '{ title = "Beta" }' | 'title = ""'       | "a. Beta"
    }

    def "honors custom name and title separators"() {
        when:
        def expanded = expand('''
            a { title = "A" }
            b { title = "B" }
            scenario-matrices {
                matrix {
                    name-separator = "-"
                    title-separator = " | "
                    dimensions = [
                        { name = left,  scenarios = [a] }
                        { name = right, scenarios = [b] }
                    ]
                }
            }
        ''')

        then:
        topLevelScenarioKeys(expanded).contains("matrix-a-b")
        expanded.getString("\"matrix-a-b\".title") == "matrix | A | B"
    }

    def "auto-generates fix-all-but-one groups for N>=3 dimensions"() {
        when:
        def expanded = expand("""
            a1 {}
            a2 {}
            b1 {}
            b2 {}
            c1 {}
            c2 {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = workflow, scenarios = [a1, a2] }
                        { name = daemon,   scenarios = [b1, b2] }
                        { name = cc,       scenarios = [c1, c2] }
                    ]
                }
            }
        """)

        then:
        def groups = expanded.getConfig("scenario-groups")
        // Fix-all-but-one: pin workflow+daemon, vary cc.
        groups.getStringList("matrix_a1_b1") == ["matrix_a1_b1_c1", "matrix_a1_b1_c2"]
        groups.getStringList("matrix_a2_b2") == ["matrix_a2_b2_c1", "matrix_a2_b2_c2"]
        // Pin workflow+cc, vary daemon (declaration order preserved, daemon slot omitted).
        groups.getStringList("matrix_a1_c1") == ["matrix_a1_b1_c1", "matrix_a1_b2_c1"]
        groups.getStringList("matrix_a2_c2") == ["matrix_a2_b1_c2", "matrix_a2_b2_c2"]
        // Pin daemon+cc, vary workflow.
        groups.getStringList("matrix_b1_c1") == ["matrix_a1_b1_c1", "matrix_a2_b1_c1"]
        groups.getStringList("matrix_b2_c2") == ["matrix_a1_b2_c2", "matrix_a2_b2_c2"]
        // Fix-one groups still exist alongside.
        groups.getStringList("matrix_a1").size() == 4
        groups.getStringList("matrix_c2").size() == 4
    }

    def "for a single-dimension matrix emits one fix-one group per scenario"() {
        when:
        def expanded = expand("""
            a1 {}
            a2 {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = workflow, scenarios = [a1, a2] }
                    ]
                }
            }
        """)

        then:
        def groups = expanded.getConfig("scenario-groups")
        groups.root().keySet() as Set == ["matrix_a1", "matrix_a2"] as Set
        groups.getStringList("matrix_a1") == ["matrix_a1"]
        groups.getStringList("matrix_a2") == ["matrix_a2"]
    }

    def "for a 2-dimension matrix fix-one and fix-all-but-one auto-groups coincide (no duplicates)"() {
        when:
        def expanded = expand("""
            a1 {}
            a2 {}
            b1 {}
            b2 {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = workflow, scenarios = [a1, a2] }
                        { name = daemon,   scenarios = [b1, b2] }
                    ]
                }
            }
        """)

        then:
        def groups = expanded.getConfig("scenario-groups")
        groups.root().keySet() as Set == [
            "matrix_a1", "matrix_a2", "matrix_b1", "matrix_b2",
        ] as Set
    }

    def "two matrices produce disjoint synthesized scenarios"() {
        when:
        def expanded = expand("""
            a {}
            b {}
            c {}
            d {}
            scenario-matrices {
                first {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
                second {
                    dimensions = [
                        { name = l, scenarios = [c] }
                        { name = r, scenarios = [d] }
                    ]
                }
            }
        """)

        then:
        def keys = topLevelScenarioKeys(expanded)
        keys.contains("first_a_b")
        keys.contains("second_c_d")
    }

    def "preserves existing scenarios alongside synthesized ones"() {
        when:
        def expanded = expand("""
            a {}
            b {}
            handwritten { tasks = ["help"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        """)

        then:
        def keys = topLevelScenarioKeys(expanded)
        keys.contains("handwritten")
        keys.contains("matrix_a_b")
    }

    def "preserves existing scenario-groups and merges synthesized ones"() {
        when:
        def expanded = expand("""
            a {}
            b {}
            other {}
            scenario-groups {
                preexisting = [other]
            }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        """)

        then:
        def groups = expanded.getConfig("scenario-groups")
        groups.getStringList("preexisting") == ["other"]
        groups.getStringList("matrix_a") == ["matrix_a_b"]
        groups.getStringList("matrix_b") == ["matrix_a_b"]
    }

    def "rejects unknown scenario reference"() {
        expect:
        expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [missing] }
                    ]
                }
            }
        ''') == "Matrix 'matrix' references unknown scenario 'missing'"
    }

    def "rejects scenario reference to a reserved key"() {
        expect:
        expansionFailureFor('''
            a {}
            scenario-groups { g = [a] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = ["scenario-groups"] }
                    ]
                }
            }
        ''') == "Matrix 'matrix' references reserved key 'scenario-groups'"
    }

    def "rejects scenario reference to another matrix key"() {
        expect:
        expansionFailureFor('''
            a {}
            b {}
            scenario-matrices {
                first {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
                second {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [first] }
                    ]
                }
            }
        ''') == "Matrix 'second' references another matrix 'first'; matrices referencing matrices is not supported"
    }

    def "rejects synthesized scenario name colliding with existing top-level scenario"() {
        expect:
        expansionFailureFor('''
            a {}
            b {}
            "matrix_a_b" { tasks = ["already"] }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        ''') == "Synthesized scenario name 'matrix_a_b' collides with an existing top-level entry"
    }

    def "rejects synthesized scenario group name colliding with existing group"() {
        expect:
        expansionFailureFor('''
            a {}
            b {}
            scenario-groups {
                "matrix_a" = [a]
            }
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        ''') == "Synthesized scenario group 'matrix_a' collides with an existing scenario-groups entry"
    }

    def "rejects empty scenarios list in a dimension"() {
        expect:
        expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [] }
                    ]
                }
            }
        ''') == "Dimension 'matrix.r.scenarios' must list at least one scenario"
    }

    def "rejects missing dimensions list on a matrix entry"() {
        expect:
        expansionFailureFor('''
            scenario-matrices {
                matrix {
                    name-separator = "_"
                }
            }
        ''') == "Matrix 'matrix' must define a 'dimensions' list"
    }

    def "rejects dimensions that is not a list"() {
        expect:
        expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions { l = [a] }
                }
            }
        ''') == "Matrix 'matrix.dimensions' must be a list of dimensions"
    }

    def "rejects dimension missing a name"() {
        expect:
        expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { scenarios = [a] }
                    ]
                }
            }
        ''') == "Dimension 'matrix.dimensions[0]' must define a 'name'"
    }

    def "rejects dimension missing a scenarios list"() {
        expect:
        expansionFailureFor('''
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = lonely }
                    ]
                }
            }
        ''') == "Dimension 'matrix.lonely' must define a 'scenarios' list"
    }

    def "rejects two dimensions sharing a name"() {
        expect:
        expansionFailureFor('''
            a {}
            b {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = dup, scenarios = [a] }
                        { name = dup, scenarios = [b] }
                    ]
                }
            }
        ''') == "Matrix 'matrix' declares dimension 'dup' more than once"
    }

    def "rejects unknown key inside a dimension"() {
        when:
        def message = expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a], typo = true }
                    ]
                }
            }
        ''')

        then:
        // 'Allowed keys: …' lists the entries of an unordered Set, so only the prefix is stable.
        message.startsWith("Unrecognized key 'matrix.dimensions[0].typo'. Allowed keys:")
    }

    def "rejects scenario-matrices that is not an object"() {
        expect:
        expansionFailureFor('''
            scenario-matrices = "oops"
        ''') == "'scenario-matrices' must be an object mapping matrix names to matrix definitions"
    }

    def "rejects empty scenario-matrices block"() {
        expect:
        expansionFailureFor('''
            scenario-matrices {}
        ''') == "'scenario-matrices' is empty — remove it or declare at least one matrix"
    }

    def "rejects unknown key inside a matrix entry"() {
        when:
        def message = expansionFailureFor('''
            a {}
            scenario-matrices {
                matrix {
                    dimensions = [
                        { name = l, scenarios = [a] }
                    ]
                    dimensoins = "typo"
                }
            }
        ''')

        then:
        // 'Allowed keys: …' lists the entries of an unordered Set, so only the prefix is stable.
        message.startsWith("Unrecognized key 'matrix.dimensoins' in 'scenario-matrices'. Allowed keys:")
    }

    def "rejects empty name-separator"() {
        expect:
        expansionFailureFor('''
            a {}
            b {}
            scenario-matrices {
                matrix {
                    name-separator = ""
                    dimensions = [
                        { name = l, scenarios = [a] }
                        { name = r, scenarios = [b] }
                    ]
                }
            }
        ''') == "Matrix 'matrix.name-separator' must not be empty"
    }

    def "rejects empty dimensions list"() {
        expect:
        expansionFailureFor('''
            scenario-matrices {
                matrix {
                    dimensions = []
                }
            }
        ''') == "Matrix 'matrix.dimensions' must declare at least one dimension"
    }

    def "rejects matrix entry that is not an object"() {
        expect:
        expansionFailureFor('''
            scenario-matrices {
                matrix = "oops"
            }
        ''') == "Matrix 'matrix' must be an object, but got string"
    }

    private Config expand(String text) {
        scenarioFile.text = text
        ScenarioMatrices.expand(ScenarioLoader.parseScenarioFile(scenarioFile), scenarioFile)
    }

    private static Set<String> topLevelScenarioKeys(Config config) {
        config.root().keySet().findAll {
            !(it in ScenarioLoader.RESERVED_TOP_LEVEL_KEYS)
        } as Set<String>
    }

    private String expansionFailureFor(String text) {
        try {
            expand(text)
        } catch (IllegalArgumentException ex) {
            // The full message always ends with " in scenario file <path>"; strip the path so tests don't depend on it.
            return ex.message.replaceFirst(/ in scenario file .*$/, "")
        }
        throw new AssertionError("Expected expansion to throw IllegalArgumentException")
    }
}
