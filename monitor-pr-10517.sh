#!/bin/bash
# ZIO PR #10517 持久化监控脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/pr-10517-monitor.log"
STATE_FILE="$SCRIPT_DIR/.zio-monitor-state.json"
ALERT_FILE="$SCRIPT_DIR/.zio-alerts.log"

PR_NUMBER="10517"
REPO="zio/zio"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

alert() {
    echo -e "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ ALERT: $1" | tee -a "$ALERT_FILE"
}

check_pr_status() {
    log "=== ZIO PR #10517 状态检查 ==="
    
    local pr_state=$(gh pr view $PR_NUMBER --repo $REPO --json state 2>/dev/null | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
    local pr_mergeable=$(gh pr view $PR_NUMBER --repo $REPO --json mergeable 2>/dev/null | grep -o '"mergeable":"[^"]*"' | cut -d'"' -f4)
    local pr_updated=$(gh pr view $PR_NUMBER --repo $REPO --json updatedAt 2>/dev/null | grep -o '"updatedAt":"[^"]*"' | cut -d'"' -f4)
    local pr_title=$(gh pr view $PR_NUMBER --repo $REPO --json title 2>/dev/null | grep -o '"title":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$pr_state" ]; then
        log "${RED}❌ 无法获取 PR 信息${NC}"
        return 1
    fi
    
    log "PR 标题：$pr_title"
    log "状态：${GREEN}$pr_state${NC}"
    log "可合并：$pr_mergeable"
    log "最后更新：$pr_updated"
    
    # 获取上次状态
    local last_state=""
    if [ -f "$STATE_FILE" ]; then
        last_state=$(grep -o '"state":"[^"]*"' "$STATE_FILE" | cut -d'"' -f4)
    fi
    
    # 检测状态变化
    if [ "$last_state" != "$pr_state" ] && [ -n "$last_state" ]; then
        alert "PR 状态变化：$last_state → $pr_state"
        
        if [ "$pr_state" == "MERGED" ]; then
            log "${GREEN}✅ PR 已合并！准备确认 bounty 收款${NC}"
            alert "🎉 PR #10517 已合并！bounty \$300 可收款"
        elif [ "$pr_state" == "CLOSED" ]; then
            log "${RED}❌ PR 被关闭！需要检查原因${NC}"
            alert "⚠️ PR #10517 被关闭！需要立即检查"
        fi
    fi
    
    # 检查 CI 状态
    log "\n## CI 检查状态"
    local ci_output=$(gh pr checks $PR_NUMBER --repo $REPO 2>/dev/null || echo "无法获取 CI 状态")
    log "$ci_output"
    
    if echo "$ci_output" | grep -q "fail"; then
        alert "⚠️ CI 检查失败！需要修复"
    fi
    
    # 检查最新评论
    log "\n## 最新评论"
    gh pr view $PR_NUMBER --repo $REPO --comments 2>/dev/null | grep -E "^author:|createdAt:" | head -10 | while read line; do
        log "$line"
    done
    
    # 保存状态
    echo "{\"state\":\"$pr_state\",\"updatedAt\":\"$pr_updated\",\"checkedAt\":\"$(date -Iseconds)\"}" > "$STATE_FILE"
    
    log "\n=== 检查完成 ===\n"
    return 0
}

# 主函数
cd "$SCRIPT_DIR"

if ! command -v gh &> /dev/null; then
    log "${RED}❌ gh CLI 未安装${NC}"
    exit 1
fi

check_pr_status
exit $?
