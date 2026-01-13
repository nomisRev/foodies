#!/bin/bash

# Script to run june command 5 times with prompt from prompt.md

set -e  # Exit on error

TASK_CONTENT=$(cat prompt.md)

for i in {1..5}; do
    echo "========================================"
    echo "Running iteration $i of 5"
    echo "========================================"

    junie --task="$TASK_CONTENT"

    echo ""
    echo "Completed iteration $i"
    echo ""
done

echo "All 5 iterations completed successfully!"
