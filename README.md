# Compliance checker

Java base-case tool for the G.E.R.S.O.N. publish pipeline. `tool.yaml` at the repo root is the only interface the portal reads. This repo uses the same file shape as the data comparator.

It checks a trade CSV. A row fails when the ticker is on the restricted list (RST, BAD, XYZ) or a buy is above 10,000 shares.

## tool.yaml

The portal builds the run screen from this file. It does not special-case the language.

| Field | What the portal does |
|---|---|
| `inputs[].type` | `file` or `text`. One control per input. |
| `inputs[].label` | The label on that control. |
| `inputs[].bind.path` | Where the container receives that input. |
| `outputs[].type` | `table`, `file`, or `text`. Picks the result view. |
| `outputs[].bind.path` | Where the container writes the result. |
| `outputs[].bind.format` | `json` is an object with `columns` and `rows`. |

## Run locally

Requires a JDK.

```bash
javac -d out/classes src/ComplianceChecker.java
java -cp out/classes ComplianceChecker --trades examples/trades.csv --out out/report.json
```

## Run the image

```bash
docker build -t compliance-checker .
docker run --rm \
  -v "$PWD/examples/trades.csv:/inputs/trades:ro" \
  -v "$PWD/out:/outputs" \
  compliance-checker
```

The sample file flags RST (restricted, and the buy is above 10,000 shares) and BAD (restricted).
