# Eclipse ESLint Builder

A simple Eclipse plugin that runs **ESLint** as an automatic builder and shows lint results as **editor markers**.

## What it does

- runs ESLint during project build
- clears previous markers
- creates new markers per file and line
- reads ESLint JSON output and maps it into Eclipse problems

## Requirement

Your JavaScript project must provide a working `lint` script in `package.json`:

```json
{
  "scripts": {
    "lint": "eslint . --ext .js,.mjs,.cjs"
  }
}
```

## Add the project nature

The Eclipse project must be configured with the ESLint nature (`com.eclipse.eslint.builder.nature`) or the builder will not run.

## Build and export

To build this plugin from scratch in Eclipse, create these two projects:

1. a **Plug-in Project** for the Java/plugin code
2. a **Feature Project** for packaging/export

After that:

1. make sure the plug-in is added to the feature
2. right click the **Feature Project**
3. go to **Export**
4. choose **Plug-in Development**
5. choose **Deployable features**
6. select the feature
7. export to an output directory

That export generates the installable Eclipse repository.

## Run for testing

To test it while developing:

1. open the **Plug-in Project**
2. right click it
3. choose **Run As**
4. choose **Eclipse Application**

That starts a new Eclipse instance with the plugin loaded.

## Requirements

- JDK installed and configured in Eclipse
- the target JavaScript project must have `npm run lint`
- the target Eclipse project must include the ESLint nature

## Install

To install the exported plugin:

1. open Eclipse
2. go to **Help -> Install New Software...**
3. click **Add...**
4. choose the exported repository directory
5. **uncheck** `Group items by category`
6. **uncheck** `Contact all update sites during install to find required software`
7. select the feature
8. continue with the installation
9. restart Eclipse when prompted

If Eclipse keeps trying to install an older version of the plugin, clear the local p2 repository cache first:

```bash
rm -rf ~/.p2/org.eclipse.equinox.p2.repository/cache
```

If needed, start Eclipse with the -clean flag:

```bash
eclipse -clean
```
