#!/bin/bash

# The prompt to use for junie
PROMPT="Take the single smallest most important task from TODO.md, and start working on it.
When the task is done make sure ./gradlew build passes.
Once it passes review the TODO.md and update it with the next single smallest most important task from specs/WEBAPP_SPECIFICATION.md
Finally before finishing commit all your changes with a nice and brief summary message."

# Check if junie is available
if ! command -v junie &> /dev/null; then
  echo "Error: junie command not found. Please ensure it's installed and in your PATH."
  exit 1
fi

echo "Starting junie loop..."
echo "----------------------------------------"

# Main loop
while true; do
  echo ""
  echo "=== Running Junie ==="
  echo ""

  # Run junie with the prompt
  junie --task="$PROMPT"

  # Capture exit code
  EXIT_CODE=$?

  # Send macOS notification with sound
  if [ $EXIT_CODE -eq 0 ]; then
    osascript -e 'display notification "Junie has completed a task. Ready for next task?" with title "Junie Task Complete" sound name "Glass"'
    echo ""
    echo "✅ Task completed successfully"
  else
    osascript -e 'display notification "Junie failed with exit code '"$EXIT_CODE"'. Check the output." with title "Junie Task Failed" sound name "Basso"'
    echo ""
    echo "❌ junie exited with code $EXIT_CODE"
  fi

  # Ask user if they want to continue
  echo ""
  read -p "Do you want to continue to the next task? (y/n): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Exiting at user request."
    exit 0
  fi

  # Small delay between iterations
  sleep 1
done
