#!/bin/bash
# Installation script for MP3 to M4B Generator

echo "========================================="
echo "MP3 to M4B Audiobook Generator - Setup"
echo "========================================="
echo ""

# Check Python
echo "Checking Python installation..."
if ! command -v python3 &> /dev/null; then
    if ! command -v python &> /dev/null; then
        echo "❌ Python not found. Please install Python 3.8 or later."
        exit 1
    else
        PYTHON_CMD="python"
    fi
else
    PYTHON_CMD="python3"
fi

PYTHON_VERSION=$($PYTHON_CMD --version 2>&1 | awk '{print $2}')
echo "✓ Found Python $PYTHON_VERSION"
echo ""

# Check FFmpeg
echo "Checking FFmpeg installation..."
if ! command -v ffmpeg &> /dev/null; then
    echo "❌ FFmpeg not found."
    echo ""
    echo "Please install FFmpeg:"
    echo "  macOS:   brew install ffmpeg"
    echo "  Ubuntu:  sudo apt install ffmpeg"
    echo "  CentOS:  sudo yum install ffmpeg"
    echo "  Windows: Download from https://ffmpeg.org/download.html"
    exit 1
fi

FFMPEG_VERSION=$(ffmpeg -version 2>&1 | head -n 1)
echo "✓ Found $FFMPEG_VERSION"
echo ""

# Install Python dependencies
echo "Installing Python dependencies..."
$PYTHON_CMD -m pip install --upgrade pip
$PYTHON_CMD -m pip install -r requirements.txt

if [ $? -ne 0 ]; then
    echo "❌ Failed to install Python dependencies"
    exit 1
fi

echo ""
echo "✓ Python dependencies installed successfully"
echo ""

# Create cache directories
echo "Creating cache directories..."
mkdir -p ~/.m4b_generator/cache
mkdir -p ~/.m4b_generator/covers
echo "✓ Cache directories created"
echo ""

echo "========================================="
echo "✓ Installation complete!"
echo "========================================="
echo ""
echo "To run the application:"
echo "  $PYTHON_CMD m4b_generator.py"
echo ""
echo "For help, see README.md"
