#!/bin/bash

# ZIO Codebase Explorer - Complete Overview
# This script provides a comprehensive view of the ZIO functional programming library

set -e

echo "================================================================"
echo "    ZIO CODEBASE COMPLETE EXPLORATION"
echo "================================================================"
echo "Date: $(date)"
echo "ZIO is a zero-dependency Scala library for asynchronous and"
echo "concurrent programming using functional programming principles."
echo ""

# Function to display file with line numbers and syntax highlighting
show_file_content() {
    local file_path="$1"
    local max_lines="${2:-100}"
    local description="$3"
    
    if [[ -f "$file_path" ]]; then
        echo ""
        echo "┌─────────────────────────────────────────────────────────────┐"
        echo "│ $description"
        echo "│ File: $file_path"
        echo "└─────────────────────────────────────────────────────────────┘"
        echo ""
        
        # Show file content with line numbers
        head -n "$max_lines" "$file_path" | nl -ba
        
        local total_lines=$(wc -l < "$file_path")
        if [[ $total_lines -gt $max_lines ]]; then
            echo ""
            echo "... (showing first $max_lines of $total_lines total lines)"
        fi
        echo ""
    else
        echo "File not found: $file_path"
    fi
}

# Function to display directory structure
show_directory_tree() {
    local dir_path="$1"
    local description="$2"
    local max_depth="${3:-3}"
    
    echo ""
    echo "┌─────────────────────────────────────────────────────────────┐"
    echo "│ $description"
    echo "│ Directory: $dir_path"
    echo "└─────────────────────────────────────────────────────────────┘"
    echo ""
    
    if command -v tree >/dev/null 2>&1; then
        tree -L "$max_depth" "$dir_path" 2>/dev/null || ls -la "$dir_path"
    else
        find "$dir_path" -maxdepth "$max_depth" -type f -name "*.scala" | head -20 | sort
    fi
    echo ""
}

# Navigate to ZIO root directory
cd /workspaces/zio

echo "================================================================"
echo "1. PROJECT OVERVIEW AND README"
echo "================================================================"

show_file_content "README.md" 50 "ZIO Project Overview and Introduction"

echo "================================================================"
echo "2. BUILD CONFIGURATION"
echo "================================================================"

show_file_content "build.sbt" 100 "SBT Build Configuration - Project Structure"

echo "================================================================"
echo "3. PROJECT STRUCTURE ANALYSIS"
echo "================================================================"

show_directory_tree "." "Complete ZIO Project Structure" 2

echo "================================================================"
echo "4. CORE ZIO LIBRARY - MAIN EFFECT TYPE"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/ZIO.scala" 200 "ZIO Effect Type - Core Abstraction"

echo "================================================================"
echo "5. ZIO RUNTIME SYSTEM"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/Runtime.scala" 150 "ZIO Runtime - Effect Execution Engine"

echo "================================================================"
echo "6. DEPENDENCY INJECTION - ZLAYER"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/ZLayer.scala" 150 "ZLayer - Dependency Injection System"

echo "================================================================"
echo "7. APPLICATION ENTRY POINT - ZIOAPP"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/ZIOApp.scala" 100 "ZIOApp - Application Main Entry Point"

echo "================================================================"
echo "8. FIBER-BASED CONCURRENCY"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/Fiber.scala" 100 "Fiber - Lightweight Threading Abstraction"

echo "================================================================"
echo "9. ERROR HANDLING - CAUSE"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/Cause.scala" 100 "Cause - Comprehensive Error Handling"

echo "================================================================"
echo "10. RESOURCE MANAGEMENT - SCOPE"
echo "================================================================"

show_file_content "core/shared/src/main/scala/zio/Scope.scala" 100 "Scope - Resource Lifecycle Management"

echo "================================================================"
echo "11. STREAMING - ZSTREAM"
echo "================================================================"

show_file_content "streams/shared/src/main/scala/zio/stream/ZStream.scala" 150 "ZStream - Functional Streaming"

echo "================================================================"
echo "12. TESTING FRAMEWORK"
echo "================================================================"

show_directory_tree "test/shared/src/main/scala/zio/test" "ZIO Test Framework Structure" 2

if [[ -f "test/shared/src/main/scala/zio/test/TestAspect.scala" ]]; then
    show_file_content "test/shared/src/main/scala/zio/test/TestAspect.scala" 80 "Test Aspects - Testing Utilities"
fi

echo "================================================================"
echo "13. INTERNAL IMPLEMENTATION"
echo "================================================================"

show_directory_tree "core/shared/src/main/scala/zio/internal" "Internal Implementation Details" 2

show_file_content "core/shared/src/main/scala/zio/internal/FiberRuntime.scala" 100 "Fiber Runtime - Core Execution Engine"

echo "================================================================"
echo "14. EXAMPLES AND USAGE PATTERNS"
echo "================================================================"

show_directory_tree "examples/shared/src/main/scala/zio/examples" "ZIO Example Applications" 2

if [[ -f "examples/shared/src/main/scala/zio/examples/ZLayerInjectExample.scala" ]]; then
    show_file_content "examples/shared/src/main/scala/zio/examples/ZLayerInjectExample.scala" 50 "ZLayer Dependency Injection Example"
fi

echo "================================================================"
echo "15. CONCURRENT PRIMITIVES"
echo "================================================================"

if [[ -f "concurrent/shared/src/main/scala/zio/concurrent/MVar.scala" ]]; then
    show_file_content "concurrent/shared/src/main/scala/zio/concurrent/MVar.scala" 80 "MVar - Concurrent Variable"
fi

if [[ -f "core/shared/src/main/scala/zio/Ref.scala" ]]; then
    show_file_content "core/shared/src/main/scala/zio/Ref.scala" 80 "Ref - Atomic Reference"
fi

echo "================================================================"
echo "16. METRICS AND OBSERVABILITY"
echo "================================================================"

show_directory_tree "core/shared/src/main/scala/zio/metrics" "Metrics and Observability" 2

if [[ -f "core/shared/src/main/scala/zio/metrics/Metric.scala" ]]; then
    show_file_content "core/shared/src/main/scala/zio/metrics/Metric.scala" 80 "Metrics System"
fi

echo "================================================================"
echo "17. SOFTWARE TRANSACTIONAL MEMORY (STM)"
echo "================================================================"

show_directory_tree "core/shared/src/main/scala/zio/stm" "STM - Software Transactional Memory" 2

if [[ -f "core/shared/src/main/scala/zio/stm/STM.scala" ]]; then
    show_file_content "core/shared/src/main/scala/zio/stm/STM.scala" 100 "STM - Transactional Memory"
fi

echo "================================================================"
echo "18. CONFIGURATION SYSTEM"
echo "================================================================"

if [[ -f "core/shared/src/main/scala/zio/Config.scala" ]]; then
    show_file_content "core/shared/src/main/scala/zio/Config.scala" 80 "Configuration System"
fi

echo "================================================================"
echo "19. SCHEDULE AND RETRY LOGIC"
echo "================================================================"

if [[ -f "core/shared/src/main/scala/zio/Schedule.scala" ]]; then
    show_file_content "core/shared/src/main/scala/zio/Schedule.scala" 100 "Schedule - Retry and Repetition Logic"
fi

echo "================================================================"
echo "20. BENCHMARKS AND PERFORMANCE"
echo "================================================================"

show_directory_tree "benchmarks" "Performance Benchmarks" 2

echo "================================================================"
echo "21. PROJECT STATISTICS"
echo "================================================================"

echo "Total Scala source files:"
find . -name "*.scala" -type f | wc -l

echo ""
echo "Lines of code by module:"
for module in core streams test concurrent managed; do
    if [[ -d "$module" ]]; then
        lines=$(find "$module" -name "*.scala" -type f -exec wc -l {} \; | awk '{sum += $1} END {print sum}')
        echo "  $module: $lines lines"
    fi
done

echo ""
echo "Key file sizes:"
ls -lh core/shared/src/main/scala/zio/ZIO.scala 2>/dev/null || echo "ZIO.scala not found"
ls -lh streams/shared/src/main/scala/zio/stream/ZStream.scala 2>/dev/null || echo "ZStream.scala not found"

echo ""
echo "================================================================"
echo "22. DOCUMENTATION AND GUIDES"
echo "================================================================"

if [[ -d "docs" ]]; then
    echo "Available documentation:"
    find docs -name "*.md" -type f | head -10
fi

echo ""
echo "================================================================"
echo "ZIO CODEBASE EXPLORATION COMPLETE"
echo "================================================================"
echo ""
echo "ZIO Architecture Summary:"
echo "========================"
echo "• Core Effect Type: ZIO[R, E, A] - Environment, Error, Success"
echo "• Fiber-based Concurrency: Lightweight green threads"
echo "• Type-safe Dependency Injection: ZLayer system" 
echo "• Resource Safety: Automatic resource management with Scope"
echo "• Streaming: ZStream for functional reactive programming"
echo "• Testing: Comprehensive testing framework with TestAspect"
echo "• STM: Software Transactional Memory for concurrent state"
echo "• Metrics: Built-in observability and monitoring"
echo "• Configuration: Type-safe configuration management"
echo "• Scheduling: Powerful retry and repetition logic"
echo ""
echo "Key Design Principles:"
echo "====================="
echo "• Zero Runtime Dependencies"
echo "• Purely Functional"
echo "• Type-safe at Compile Time"
echo "• Resource-safe (no leaks)"
echo "• High Performance"
echo "• Compositional Design"
echo "• Testable and Mockable"
echo ""
echo "This exploration script has shown you the complete ZIO codebase!"
echo "ZIO represents state-of-the-art functional programming for Scala."