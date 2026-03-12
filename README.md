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