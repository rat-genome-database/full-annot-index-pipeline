# full-annot-index-pipeline

Updates the `FULL_ANNOT_INDEX` table, a large auxiliary table that speeds up annotation queries
used by RGD tools.

## Overview

For each annotation in `FULL_ANNOT`, this pipeline expands it to include rows for all child
ontology terms. This allows queries like "find all annotations for term X and its descendants"
to be answered with a simple index lookup instead of recursive ontology traversal.

## Usage

Specify one or more aspect codes on the command line:
- `D` — disease ontology
- `W` — pathway ontology
- `*` — all public ontologies
- `--fixRogueRows` — delete rows that violate integrity constraints

## Logic

For each aspect:
1. Build incoming index entries from `FULL_ANNOT` expanded with ontology child terms
2. Compare against existing `FULL_ANNOT_INDEX` rows
3. Insert new entries, delete obsolete ones, leave matching rows unchanged

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```
