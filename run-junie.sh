#!/bin/bash
set -e

ITERATIONS=${1:-10}

TASK_CONTENT=$(cat prompt.md)

for ((i=1; i<=ITERATIONS; i++)); do
    echo "========================================"
    echo "Running iteration $i of $ITERATIONS"
    echo "========================================"

    junie --task="$TASK_CONTENT" --guidelines-filename=AGENTS.md --brave

    echo ""
    echo "Completed iteration $i"
    echo ""
done

echo "All $ITERATIONS iterations completed successfully!"
