mwu = require("mann-whitney-utest");

const Phase = {
    WARM_UP: "WARM_UP",
    MEASURE: "MEASURE"
};

Array.prototype.numericSort = function() {
    return this.slice().sort((a, b) => a - b);
}
Array.prototype.reverseNumericSort = function() {
    return this.slice().sort((a, b) => b - a);
}
Array.prototype.min = function() {
    return Math.min(...this);
}
Array.prototype.mean = function() {
    return this.reduce((sum, val) => sum + val) / this.length;
}
Array.prototype.max = function() {
    return Math.max(...this);
}
Array.prototype.quantile = function(q) {
    const sorted = this.numericSort();
    const pos = (sorted.length - 1) * q;
    const base = Math.floor(pos);
    const rest = pos - base;
    if (sorted[base + 1] !== undefined) {
        return sorted[base] + rest * (sorted[base + 1] - sorted[base]);
    } else {
        return sorted[base];
    }
}
Array.prototype.stddev = function() {
    const mean = this.mean();
    return Math.sqrt(this.map(x => Math.pow(x - mean, 2)).mean());
}
Array.prototype.unique = function() {
    return [...new Set(this)];
}
const dataFormat = new Intl.NumberFormat(navigator.language, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const dataDiffFormat = new Intl.NumberFormat(navigator.language, { minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: "exceptZero" });
const percentFormat = new Intl.NumberFormat(navigator.language, { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 });
const percentDiffFormat = new Intl.NumberFormat(navigator.language, { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2, signDisplay: "exceptZero"  });

function measuredIterations(scenario) {
    return scenario.iterations.filter(iteration => iteration.phase === Phase.MEASURE);
}

class Operation {
    constructor(name, title, calculator) {
        this.name = name;
        this.title = title;
        this.calculator = calculator;
    }

    apply(data) {
        return this.calculator(data);
    }
}

const OPERATIONS = module.exports.OPERATIONS = window.OPERATIONS = [
    new Operation("mean", "Mean", (data) => data.mean()),
    new Operation("min", "Min", (data) => data.min()),
    new Operation("p25", "P25", (data) => data.quantile(.25)),
    new Operation("median", "Median", (data) => data.quantile(.50)),
    new Operation("p75", "P75", (data) => data.quantile(.75)),
    new Operation("max", "Max", (data) => data.max()),
    new Operation("stddev", "Std.dev", (data) => data.stddev())
];

new Vue({
    el: '#app',
    data: {
        options: {
            sorted: false,
            showAll: true
        },
        benchmarkResult: benchmarkResult,
        operations: OPERATIONS,
        baseline: null
    },
    computed: {
        multipleBuildToolsPresent: function() {
            return this.benchmarkResult.scenarios.map(scenario => scenario.definition.buildTool).unique().length > 1;
        },
        chartData: function() {
            const sorted = this.options.sorted;
            const multipleBuildToolsPresent = this.multipleBuildToolsPresent;
            return this.benchmarkResult.scenarios
                .map(scenario => scenario.samples
                    .filter(sample => sample.selected)
                    .map(sample => {
                        let data = measuredIterations(scenario)
                            .map(iteration => iteration.values[sample.name]);
                        if (sorted) {
                            data = data.reverseNumericSort();
                        }
                        return {
                            label: multipleBuildToolsPresent
                                ? `${scenario.definition.title} ${scenario.definition.buildTool} (${sample.name})`
                                : `${scenario.definition.title} (${sample.name})`,
                            showLine: true,
                            stepped: "middle",
                            pointRadius: 0,
                            pointHitRadius: 10,
                            hoverRadius: 6,
                            fill: false,
                            borderWidth: sample.thickness,
                            backgroundColor: sample.color,
                            pointBackgroundColor: sample.color,
                            borderColor: sample.color,
                            data: data,
                            sample: sample
                        };
                    }))
                .flat();
        }
    },
    watch: {
        "options.sorted": function() {
            this.updateData();
        }
    },
    methods: {
        select: function(sample) {
            sample.selected = !sample.selected;
            this.updateData();
        },
        toggleAll: function(selected) {
            benchmarkResult.scenarios.forEach(scenario => scenario.samples.forEach(sample => sample.selected = selected));
            this.updateData();
        },
        rowCount: function(scenario) {
            return scenario.samples.filter(sample => this.options.showAll || sample.selected).length;
        },
        updateData: function() {
            this.chart.data.datasets = this.chartData;
            this.chart.update();
        },
        uTest: function(scenario, baseline, sample) {
            const samples = [
                measuredIterations(baseline).map(iteration => iteration.values[sample.name]),
                measuredIterations(scenario).map(iteration => iteration.values[sample.name])
            ];
            const u = mwu.test(samples);
            const z = mwu.criticalValue(u, samples);
            return 0.5 * (1 + math.erf(z / math.sqrt(2)));
        }
    },
    filters: {
        "numeric": (value) => dataFormat.format(value),
        "diff": (value) => dataDiffFormat.format(value),
        "percent": (value) => isNaN(value) ? "" : percentFormat.format(value),
        "percentDiff": (value) => isNaN(value) ? "" : percentDiffFormat.format(value),
        "date": (value) => value.toLocaleString(navigator.language, {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: 'numeric', minute: 'numeric', second: 'numeric',
           timeZoneName: 'short'
        })
    },
    beforeCreate: function() {
        // Initialize sample config
        benchmarkResult.scenarios.forEach((scenario, scenarioIndex, scenarios) => {
            scenario.showDetails = false;
            scenario.samples.forEach((sample, sampleIndex, samples) => {
                sample.color = `hsl(${scenarioIndex * 360 / scenarios.length}, ${100 - 80 * sampleIndex / samples.length}%, ${30 + 40 * sampleIndex / samples.length}%)`;
                sample.thickness = sampleIndex === 0 ? 3 : 2;
                sample.selected = sampleIndex === 0;
                sample.unit = "ms";
                const data = measuredIterations(scenario).map(iteration => iteration.values[sample.name]);
                OPERATIONS.forEach(operation => sample[operation.name] = operation.apply(data));
            });
        });
    },
    created: function() {
        // Set baseline for regression tests
        this.baseline = this.multipleBuildToolsPresent
            ? benchmarkResult.scenarios[0]
            : null;
    },
    mounted: function() {
        const ctx = document.getElementById('samples').getContext('2d');
        const maxMeasuredIterations = benchmarkResult.scenarios
            .map(scenario => measuredIterations(scenario).length)
            .max();
        const chart = this.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: Array.from(Array(maxMeasuredIterations), (_, i) => (i + 1).toString()),
                datasets: this.chartData
            },
            options: {
                font: {
                    family: "Roboto Mono"
                },
                plugins: {
                    crosshair: {
                        zoom: {
                            enabled: false
                        },
                        snap: {
                            enabled: false
                        }
                    }
                },
                responsive: false,
                animation: {
                    duration: 0
                },
                legend: {
                    display: false
                },
                tooltips: {
                    mode: "index",
                    position: "nearest",
                    itemSort: (a, b) => b.yLabel - a.yLabel,
                    callbacks: {
                        title: (tooltips, data) => `Build #${tooltips[0].label}`,
                        label: (context) => dataFormat.format(context.dataPoint.y) + " " + context.dataset.sample.unit + " – " + context.dataset.label
                    }
                },
                hover: {
                    intersect: false
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
        document.fonts.ready.then(() => chart.update());
    }
});
