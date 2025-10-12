# Flamegraph

A better flamegraph visualizer than what we had before.

## Developing

### Starting the development server

This build uses Vite to build the flamegraph application.

Run `./gradlew :flamegraph:serve`

This serves a live development version of the flamegraph app at http://localhost:5173/

### Managed node installation

This build manages (downloads) its own version of node and npm.

You can run arbitrary `npm` and `npx` commands on the managed node installation with the following tasks:

#### NPM

NPM commands: `./gradlew :flamegraph:npm --cmd "whatever you want"`

For example, to install a new package:

`./gradlew :flamegraph:npm --cmd "install some-package"`

### NPX

NPX commands: `./gradlew :flamegraph:npx --cmd "whatever you want"`

For example, running the dev server

`./gradlew :flamegraph:npx --cmd "vite"`
