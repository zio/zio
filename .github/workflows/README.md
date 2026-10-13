# Auto-Approve and Auto-Merge Workflows

This directory contains workflows that automatically approve and merge dependency update PRs from automation bots.

## Overview

- **`auto-approve.yml`**: Automatically approves PRs from dependency bots
- **`auto-merge.yml`**: Enables GitHub's auto-merge feature (squash strategy) on bot PRs
- **`scala-steward.yml`**: Runs Scala Steward to generate dependency update PRs

## Manual Backfill / Force Re-trigger

If PRs are not being auto-approved or auto-merged (e.g., due to a workflow bug or missed events), manually trigger the backfill logic:

### Method 1: GitHub Web UI
1. Go to **Actions** → **Auto-approve bot dependency PRs** (or **Auto-merge bot dependency PRs**)
2. Click **Run workflow** → **Run workflow** button
3. Select branch: `series/2.x` (or your target branch)
4. Click **Run workflow**

### Method 2: GitHub CLI

```bash
# Re-approve all open bot PRs on a branch
gh workflow run auto-approve.yml --repo zio/zio --ref series/2.x

# Re-enable auto-merge on all open bot PRs on a branch
gh workflow run auto-merge.yml --repo zio/zio --ref series/2.x
```

The `workflow_dispatch` triggers will iterate over all currently open PRs from both bots and apply the approvals/merges.

## How It Works

### Auto-Approve Workflow

1. **Trigger**: `pull_request_target` (new/updated/reopened/ready-for-review bot PRs) or `workflow_dispatch` (manual)
2. **Condition**: Checks if PR author is `renovate[bot]` or `zio-scala-steward[bot]`
3. **Action**: Runs `gh pr review --approve` to approve the PR

### Auto-Merge Workflow

1. **Trigger**: `pull_request_target` (new/updated/reopened/ready-for-review bot PRs) or `workflow_dispatch` (manual)
2. **Condition**: Checks if PR author is `renovate[bot]` or `zio-scala-steward[bot]`
3. **Action**: Runs `gh pr merge --auto --squash` to enable squash auto-merge

