package org.gradle.profiler;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Expands top-level {@code scenario-matrices { ... }} blocks into synthesized scenarios and
 * scenario groups. The output is a normal HOCON {@link Config} that downstream code consumes
 * without further knowledge of matrices.
 *
 * <p>Example input:
 * <pre>
 * cold { daemon = cold }
 * warm { daemon = warm }
 * assemble { tasks = ["assemble"] }
 * check    { tasks = ["check"] }
 *
 * scenario-matrices {
 *     matrix {
 *         dimensions = [
 *             { name = task,   scenarios = [assemble, check] }
 *             { name = daemon, scenarios = [cold, warm] }
 *         ]
 *     }
 * }
 * </pre>
 *
 * <p>Expansion produces four synthesized scenarios — {@code matrix_assemble_cold},
 * {@code matrix_assemble_warm}, {@code matrix_check_cold}, {@code matrix_check_warm} — each formed
 * by HOCON-merging the picked scenarios in dimension order (rightmost dimension wins on conflicts),
 * plus auto-generated {@code scenario-groups} for selecting subsets.
 */
final class ScenarioMatrices {

    static final String SCENARIO_MATRICES = "scenario-matrices";
    private static final String SCENARIO_GROUPS = "scenario-groups";
    private static final String DIMENSIONS = "dimensions";
    private static final String NAME = "name";
    private static final String SCENARIOS = "scenarios";
    private static final String NAME_SEPARATOR = "name-separator";
    private static final String TITLE_SEPARATOR = "title-separator";
    private static final String TITLE = "title";
    private static final String DEFAULT_NAME_SEPARATOR = "_";
    private static final String DEFAULT_TITLE_SEPARATOR = ". ";
    private static final Set<String> MATRIX_ENTRY_KEYS = Set.of(
        DIMENSIONS, NAME_SEPARATOR, TITLE_SEPARATOR, TITLE
    );
    private static final Set<String> DIMENSION_KEYS = Set.of(NAME, SCENARIOS);

    private ScenarioMatrices() {
    }

    /**
     * Returns {@code rootConfig} with every {@code scenario-matrices { ... }} entry replaced by its
     * synthesized scenarios and auto-generated groups. The {@code scenario-matrices} block itself is
     * removed. If no such block is present the input is returned unchanged.
     *
     * <p>Throws {@link IllegalArgumentException} citing {@code scenarioFile} and the offending key
     * for any malformed matrix definition or cross-batch collision.
     */
    static Config expand(Config rootConfig, File scenarioFile) {
        if (!rootConfig.hasPath(SCENARIO_MATRICES)) {
            return rootConfig;
        }

        ConfigValue scenarioMatricesValue = rootConfig.getValue(SCENARIO_MATRICES);
        if (scenarioMatricesValue.valueType() != ConfigValueType.OBJECT) {
            throw fail(scenarioFile, "'" + SCENARIO_MATRICES + "' must be an object mapping matrix names to matrix definitions");
        }
        ConfigObject matricesObject = (ConfigObject) scenarioMatricesValue;
        if (matricesObject.isEmpty()) {
            throw fail(scenarioFile, "'" + SCENARIO_MATRICES + "' is empty — remove it or declare at least one matrix");
        }
        Set<String> matrixKeys = new LinkedHashSet<>(matricesObject.keySet());

        // Phase 1 — collect additions from every matrix without touching the input.
        Map<String, ConfigValue> additionalScenarios = new LinkedHashMap<>();
        Map<String, List<String>> additionalGroups = new LinkedHashMap<>();

        Config matricesConfig = matricesObject.toConfig();
        for (String prefix : matrixKeys) {
            ConfigValue entryValue = matricesObject.get(prefix);
            if (entryValue.valueType() != ConfigValueType.OBJECT) {
                throw fail(scenarioFile, "Matrix '" + prefix + "' must be an object, but got " + entryValue.valueType().name().toLowerCase(Locale.ROOT));
            }
            Config entry = matricesConfig.getConfig(quote(prefix));
            expandOne(rootConfig, prefix, entry, matrixKeys, additionalScenarios, additionalGroups, scenarioFile);
        }

        // Phase 2 — validate the batch of additions against the input in one pass, then merge.
        Set<String> existingTopLevel = rootConfig.root().keySet();
        for (String name : additionalScenarios.keySet()) {
            if (existingTopLevel.contains(name)) {
                throw fail(scenarioFile, "Synthesized scenario name '" + name + "' collides with an existing top-level entry");
            }
        }
        Set<String> existingGroups = rootConfig.hasPath(SCENARIO_GROUPS)
            ? rootConfig.getObject(SCENARIO_GROUPS).keySet()
            : Set.of();
        for (String groupName : additionalGroups.keySet()) {
            if (existingGroups.contains(groupName)) {
                throw fail(scenarioFile, "Synthesized scenario group '" + groupName + "' collides with an existing scenario-groups entry");
            }
        }

        return buildResult(rootConfig, additionalScenarios, additionalGroups);
    }

    private static Config buildResult(
        Config rootConfig,
        Map<String, ConfigValue> additionalScenarios,
        Map<String, List<String>> additionalGroups
    ) {
        Map<String, Object> additions = new LinkedHashMap<>();
        additions.putAll(additionalScenarios);
        if (!additionalGroups.isEmpty()) {
            // Merge synthesized groups under the same scenario-groups key.
            additions.put(SCENARIO_GROUPS, ConfigValueFactory.fromMap(new LinkedHashMap<>(additionalGroups)));
        }
        Config additionsConfig = ConfigValueFactory.fromMap(additions).toConfig();
        // additionsConfig wins (no collisions — checked above), so we use it as the receiver.
        return additionsConfig.withFallback(rootConfig.withoutPath(SCENARIO_MATRICES));
    }

    /**
     * Expands one matrix entry into synthesized scenarios + auto-groups, appending them to the
     * shared {@code additionalScenarios} and {@code additionalGroups} maps.
     *
     * <p>The {@code entry} corresponds to the body of a single matrix, for example:
     * <pre>
     * {
     *     title = "Matrix"            // optional; "" omits the prefix segment; default = matrix key
     *     name-separator = "_"        // optional; default "_"
     *     title-separator = ". "      // optional; default ". "
     *     dimensions = [ ... ]        // required
     * }
     * </pre>
     *
     * <p>Each tuple of scenarios produces one synthesized scenario named
     * {@code <prefix><nameSep><scenario1><nameSep><scenario2>...}, formed by fold-right
     * {@link Config#withFallback}.
     */
    private static void expandOne(
        Config rootConfig,
        String prefix,
        Config entry,
        Set<String> matrixKeys,
        Map<String, ConfigValue> additionalScenarios,
        Map<String, List<String>> additionalGroups,
        File scenarioFile
    ) {
        for (String key : entry.root().keySet()) {
            if (!MATRIX_ENTRY_KEYS.contains(key)) {
                throw fail(scenarioFile, "Unrecognized key '" + prefix + "." + key + "' in '" + SCENARIO_MATRICES + "'. Allowed keys: " + String.join(", ", MATRIX_ENTRY_KEYS));
            }
        }
        if (!entry.hasPath(DIMENSIONS)) {
            throw fail(scenarioFile, "Matrix '" + prefix + "' must define a 'dimensions' list");
        }
        String nameSep = entry.hasPath(NAME_SEPARATOR) ? entry.getString(NAME_SEPARATOR) : DEFAULT_NAME_SEPARATOR;
        if (nameSep.isEmpty()) {
            throw fail(scenarioFile, "Matrix '" + prefix + "." + NAME_SEPARATOR + "' must not be empty");
        }
        String titleSep = entry.hasPath(TITLE_SEPARATOR) ? entry.getString(TITLE_SEPARATOR) : DEFAULT_TITLE_SEPARATOR;
        String matrixTitle = entry.hasPath(TITLE) ? entry.getString(TITLE) : null;

        List<Dimension> dimensions = parseDimensions(entry, prefix, rootConfig, matrixKeys, scenarioFile);

        // Cartesian product, preserving the order of dimensions and scenarios as declared.
        List<List<String>> tuples = cartesianProduct(dimensions.stream().map(Dimension::scenarios).toList());

        // Synthesize one scenario per tuple.
        List<String> tupleNames = new ArrayList<>(tuples.size());
        for (List<String> tuple : tuples) {
            String name = prefix + nameSep + String.join(nameSep, tuple);
            if (additionalScenarios.containsKey(name)) {
                throw fail(scenarioFile, "Synthesized scenario name '" + name + "' produced twice (likely two matrices collide)");
            }

            // Fold-right: last dimension's scenario wins on conflicts.
            Config merged = ConfigFactory.empty();
            for (String scenario : tuple) {
                merged = rootConfig.getConfig(quote(scenario)).withFallback(merged);
            }

            String synthesizedTitle = composeTitle(rootConfig, matrixTitle, prefix, tuple, titleSep);
            if (synthesizedTitle != null) {
                merged = merged.withValue(TITLE, ConfigValueFactory.fromAnyRef(synthesizedTitle));
            }
            // Otherwise leave any scenario-inherited title in place — composeTitle returns null only when no scenario has one,
            // so there's nothing to strip.

            additionalScenarios.put(name, merged.root());
            tupleNames.add(name);
        }

        generateAutoGroups(prefix, nameSep, dimensions, tuples, tupleNames, additionalGroups, scenarioFile);
    }

    /**
     * Generates two families of auto-groups, both following the same naming rule: the group name is the synthesized
     * scenario name with the varying dim(s) omitted.
     *
     * <ul>
     *   <li>Fix-one (one pinned scenario, rest free): {@code <prefix>_<scenario>}. For "give me all variants where daemon=cold".</li>
     *   <li>Fix-all-but-one (N−1 pinned scenarios, one dim free): {@code <prefix>_<scenario1>_<scenario2>...} in dimension order
     *       with the varying dim's slot omitted. For "vary just this knob between two otherwise-identical configurations".</li>
     * </ul>
     *
     * For N≤2 the two families coincide; we deduplicate. Collisions across families or scenarios fail loudly.
     */
    private static void generateAutoGroups(
        String prefix,
        String nameSep,
        List<Dimension> dimensions,
        List<List<String>> tuples,
        List<String> tupleNames,
        Map<String, List<String>> additionalGroups,
        File scenarioFile
    ) {
        generateFixOneGroups(prefix, nameSep, dimensions, tuples, tupleNames, additionalGroups, scenarioFile);
        // N≤2 is already covered by fix-one: with N=2 the names coincide; with N=1 there's no second dim to vary over.
        if (dimensions.size() >= 3) {
            generateFixAllButOneGroups(prefix, nameSep, dimensions, tuples, tupleNames, additionalGroups, scenarioFile);
        }
    }

    /**
     * Emits one group per scenario. The group fixes that single scenario and lets every other dimension vary.
     *
     * <p>Example: with scenarios {@code [a1, a2] × [b1, b2]} and prefix {@code matrix}, this emits
     * {@code matrix_a1 = [matrix_a1_b1, matrix_a1_b2]}, {@code matrix_a2 = [matrix_a2_b1, matrix_a2_b2]},
     * {@code matrix_b1 = [matrix_a1_b1, matrix_a2_b1]}, {@code matrix_b2 = [matrix_a1_b2, matrix_a2_b2]}.
     */
    private static void generateFixOneGroups(
        String prefix,
        String nameSep,
        List<Dimension> dimensions,
        List<List<String>> tuples,
        List<String> tupleNames,
        Map<String, List<String>> additionalGroups,
        File scenarioFile
    ) {
        int dimensionCount = dimensions.size();
        for (int d = 0; d < dimensionCount; d++) {
            for (String scenario : dimensions.get(d).scenarios()) {
                String groupName = prefix + nameSep + scenario;
                List<String> members = new ArrayList<>();
                for (int t = 0; t < tuples.size(); t++) {
                    if (tuples.get(t).get(d).equals(scenario)) {
                        members.add(tupleNames.get(t));
                    }
                }
                putGroup(additionalGroups, groupName, members, scenarioFile);
            }
        }
    }

    /**
     * Emits one group per choice of (which dimension varies, scenarios fixed in the others). The group
     * name lists the pinned scenarios in dimension order, omitting the varying dimension's slot.
     *
     * <p>Example: with scenarios {@code [a1, a2] × [b1, b2] × [c1, c2]} and prefix {@code matrix},
     * pinning {@code a1, b1} (cc varies) emits {@code matrix_a1_b1 = [matrix_a1_b1_c1, matrix_a1_b1_c2]};
     * pinning {@code a1, c1} (daemon varies) emits {@code matrix_a1_c1 = [matrix_a1_b1_c1, matrix_a1_b2_c1]}.
     */
    private static void generateFixAllButOneGroups(
        String prefix,
        String nameSep,
        List<Dimension> dimensions,
        List<List<String>> tuples,
        List<String> tupleNames,
        Map<String, List<String>> additionalGroups,
        File scenarioFile
    ) {
        int dimensionCount = dimensions.size();
        for (int varying = 0; varying < dimensionCount; varying++) {
            List<List<String>> fixedScenariosByDim = new ArrayList<>(dimensionCount);
            for (int d = 0; d < dimensionCount; d++) {
                fixedScenariosByDim.add(d == varying ? Collections.singletonList(null) : dimensions.get(d).scenarios());
            }
            for (List<String> assignment : cartesianProduct(fixedScenariosByDim)) {
                StringBuilder groupName = new StringBuilder(prefix);
                for (String scenario : assignment) {
                    if (scenario != null) {
                        groupName.append(nameSep).append(scenario);
                    }
                }
                List<String> members = new ArrayList<>();
                for (int t = 0; t < tuples.size(); t++) {
                    boolean match = true;
                    for (int d = 0; d < dimensionCount; d++) {
                        String pinned = assignment.get(d);
                        if (pinned != null && !pinned.equals(tuples.get(t).get(d))) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        members.add(tupleNames.get(t));
                    }
                }
                putGroup(additionalGroups, groupName.toString(), members, scenarioFile);
            }
        }
    }

    /** Same name + same members is a no-op; same name + different members fails. */
    private static void putGroup(Map<String, List<String>> groups, String groupName, List<String> members, File scenarioFile) {
        List<String> previous = groups.putIfAbsent(groupName, members);
        if (previous != null && !previous.equals(members)) {
            throw fail(scenarioFile, "Synthesized scenario group '" + groupName + "' produced twice with different members (likely two dimensions share a scenario name)");
        }
    }

    /**
     * Builds the title of a synthesized scenario by joining the matrix title prefix with each scenario's title
     * (or the scenario's name if it has no {@code title}) using {@code titleSep}. Returns {@code null} when
     * nothing carries a title — the caller then leaves {@code title} unset so {@code ScenarioDefinition.getTitle()}
     * falls back to the synthesized scenario name.
     *
     * <p>{@code matrixTitle} controls the prefix segment in three states:
     * <ul>
     *   <li>{@code null} — {@code title} wasn't set; use {@code prefix} (the matrix key).</li>
     *   <li>{@code ""}   — user wrote {@code title = ""}; omit the prefix segment entirely.</li>
     *   <li>non-empty   — use it verbatim.</li>
     * </ul>
     *
     * <p>Examples with {@code titleSep = ". "}, scenarios {@code [a, b]}, scenario titles {@code "Alpha"} / {@code "Beta"}:
     * <ul>
     *   <li>{@code matrixTitle = "Matrix"} → {@code "Matrix. Alpha. Beta"}</li>
     *   <li>{@code matrixTitle = null}, prefix {@code "matrix"} → {@code "matrix. Alpha. Beta"}</li>
     *   <li>{@code matrixTitle = ""}     → {@code "Alpha. Beta"}</li>
     * </ul>
     */
    private static String composeTitle(Config rootConfig, String matrixTitle, String prefix, List<String> tuple, String titleSep) {
        boolean anyTitle = matrixTitle != null;
        List<String> scenarioTitles = new ArrayList<>(tuple.size());
        for (String scenario : tuple) {
            Config component = rootConfig.getConfig(quote(scenario));
            if (component.hasPath(TITLE)) {
                anyTitle = true;
                scenarioTitles.add(component.getString(TITLE));
            } else {
                scenarioTitles.add(scenario);
            }
        }
        if (!anyTitle) {
            return null;
        }
        String prefixSegment = matrixTitle != null ? matrixTitle : prefix;
        StringBuilder titleBuilder = new StringBuilder();
        if (!prefixSegment.isEmpty()) {
            titleBuilder.append(prefixSegment);
        }
        for (String scenarioTitle : scenarioTitles) {
            if (titleBuilder.length() > 0) {
                titleBuilder.append(titleSep);
            }
            titleBuilder.append(scenarioTitle);
        }
        return titleBuilder.toString();
    }

    /**
     * Reads the {@code dimensions} list from a matrix entry, validating its shape and every scenario
     * reference. Returns the dimensions in declaration order.
     *
     * <p>Each dimension is an object with two required keys, for example:
     * <pre>
     * { name = daemon, scenarios = [cold, warm] }
     * </pre>
     * The {@code name} field is used in error messages and for the duplicate-dimension check; it does
     * not appear in synthesized scenario names or group names.
     */
    private static List<Dimension> parseDimensions(Config entry, String prefix, Config rootConfig, Set<String> matrixKeys, File scenarioFile) {
        ConfigValue dimensionsValue = entry.getValue(DIMENSIONS);
        if (dimensionsValue.valueType() != ConfigValueType.LIST) {
            throw fail(scenarioFile, "Matrix '" + prefix + "." + DIMENSIONS + "' must be a list of dimensions");
        }
        List<? extends ConfigObject> dimensionObjects;
        try {
            dimensionObjects = entry.getObjectList(DIMENSIONS);
        } catch (Exception ex) {
            throw fail(scenarioFile, "Matrix '" + prefix + "." + DIMENSIONS + "' must be a list of objects with 'name' and 'scenarios' keys");
        }
        if (dimensionObjects.isEmpty()) {
            throw fail(scenarioFile, "Matrix '" + prefix + "." + DIMENSIONS + "' must declare at least one dimension");
        }
        List<Dimension> dimensions = new ArrayList<>(dimensionObjects.size());
        Set<String> seenDimensionNames = new LinkedHashSet<>();
        for (int i = 0; i < dimensionObjects.size(); i++) {
            Config dim = dimensionObjects.get(i).toConfig();
            String dimLocation = prefix + "." + DIMENSIONS + "[" + i + "]";
            for (String key : dim.root().keySet()) {
                if (!DIMENSION_KEYS.contains(key)) {
                    throw fail(scenarioFile, "Unrecognized key '" + dimLocation + "." + key + "'. Allowed keys: " + String.join(", ", DIMENSION_KEYS));
                }
            }
            if (!dim.hasPath(NAME)) {
                throw fail(scenarioFile, "Dimension '" + dimLocation + "' must define a 'name'");
            }
            String dimName = dim.getString(NAME);
            if (!seenDimensionNames.add(dimName)) {
                throw fail(scenarioFile, "Matrix '" + prefix + "' declares dimension '" + dimName + "' more than once");
            }
            if (!dim.hasPath(SCENARIOS)) {
                throw fail(scenarioFile, "Dimension '" + prefix + "." + dimName + "' must define a 'scenarios' list");
            }
            List<String> dimensionScenarios;
            try {
                dimensionScenarios = dim.getStringList(SCENARIOS);
            } catch (Exception ex) {
                throw fail(scenarioFile, "Dimension '" + prefix + "." + dimName + ".scenarios' must be a list of scenario names");
            }
            if (dimensionScenarios.isEmpty()) {
                throw fail(scenarioFile, "Dimension '" + prefix + "." + dimName + ".scenarios' must list at least one scenario");
            }
            for (String scenario : dimensionScenarios) {
                validateScenarioRef(scenario, prefix, rootConfig, matrixKeys, scenarioFile);
            }
            dimensions.add(new Dimension(dimName, dimensionScenarios));
        }
        return dimensions;
    }

    private static void validateScenarioRef(String scenario, String prefix, Config rootConfig, Set<String> matrixKeys, File scenarioFile) {
        if (ScenarioLoader.RESERVED_TOP_LEVEL_KEYS.contains(scenario)) {
            throw fail(scenarioFile, "Matrix '" + prefix + "' references reserved key '" + scenario + "'");
        }
        if (matrixKeys.contains(scenario)) {
            throw fail(scenarioFile, "Matrix '" + prefix + "' references another matrix '" + scenario + "'; matrices referencing matrices is not supported");
        }
        if (!rootConfig.hasPath(quote(scenario))) {
            throw fail(scenarioFile, "Matrix '" + prefix + "' references unknown scenario '" + scenario + "'");
        }
    }

    /**
     * Cartesian product over a list of axes, preserving declaration order.
     *
     * <p>Example: {@code [[a1, a2], [b1, b2]]} → {@code [[a1, b1], [a1, b2], [a2, b1], [a2, b2]]}.
     *
     * <p>Used both for synthesizing tuples and (with {@code null} sentinels in the "varying" slot)
     * for enumerating fix-all-but-one group assignments.
     */
    private static List<List<String>> cartesianProduct(List<List<String>> dimensions) {
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (List<String> dim : dimensions) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> prev : result) {
                for (String value : dim) {
                    List<String> extended = new ArrayList<>(prev);
                    extended.add(value);
                    next.add(extended);
                }
            }
            result = next;
        }
        return result;
    }

    private static String quote(String key) {
        // Quote HOCON path segments so names containing dots or other special characters aren't treated as paths.
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static IllegalArgumentException fail(File scenarioFile, String message) {
        return new IllegalArgumentException(message + " in scenario file " + scenarioFile);
    }

    private record Dimension(String name, List<String> scenarios) {
    }
}
