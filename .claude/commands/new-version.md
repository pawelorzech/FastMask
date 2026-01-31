---
allowed-tools: Bash(git add:*), Bash(git status:*), Bash(git commit:*)
description: Do migration tests first. Then: update changelog, update readme, update version in gradle files, create a git commit and push it
---

## Context

- Current git status: !`git status`
- Current git diff (staged and unstaged changes): !`git diff HEAD`
- Current branch: !`git branch --show-current`
- Recent commits: !`git log --oneline -10`

## Your task

Do migration tests first. Then: update changelog, update readme, update version in gradle files, create a git commit and push it
