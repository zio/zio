#!/bin/bash

echo "=== Installing Scala CLI ==="

# Check if Scala CLI is already installed
if command -v scala-cli &> /dev/null; then
    echo "Scala CLI is already installed."
    scala-cli --version
    exit 0
fi

# Check if Coursier is installed
if command -v cs &> /dev/null; then
    echo "Using Coursier to install Scala CLI..."
    cs install scala-cli
    exit 0
fi

# Install Scala CLI using curl
echo "Installing Scala CLI using curl..."
curl -sSLf https://scala-cli.virtuslab.org/get | sh

# Add Scala CLI to PATH for the current session
export PATH="$PATH:$HOME/.local/share/coursier/bin"

# Verify installation
if command -v scala-cli &> /dev/null; then
    echo "Scala CLI installed successfully!"
    scala-cli --version
else
    echo "Failed to install Scala CLI. Please install it manually."
    echo "Visit: https://scala-cli.virtuslab.org/install"
    exit 1
fi