#!/usr/bin/env bash
set -euo pipefail

tag="build-${GITHUB_RUN_NUMBER}-${GITHUB_SHA:0:7}"
apk="jianpantong-${tag}-release.apk"
notes="${RUNNER_TEMP}/jianpantong-release-notes.md"

cp release-artifacts/app-release.apk "$apk"
sha256sum "$apk" > "${apk}.sha256"

cat > "$notes" <<EOF
由 main 分支通过测试的构建自动发布。

- 提交：${GITHUB_SHA}
- 构建与测试报告：https://github.com/${GH_REPO}/actions/runs/${GITHUB_RUN_ID}
- 下载下方 APK 可直接安装；另附 SHA-256 校验文件。
- 当前产物使用项目所有者提供的正式签名，可用于后续覆盖升级。
EOF

if gh release view "$tag" --json isDraft >/dev/null 2>&1; then
  if [[ "$(gh release view "$tag" --json isDraft --jq '.isDraft')" == "false" ]]; then
    echo "Release ${tag} is already published."
    exit 0
  fi
  gh release upload "$tag" "$apk" "${apk}.sha256" --clobber
else
  gh release create "$tag" "$apk" "${apk}.sha256" \
    --target "$GITHUB_SHA" \
    --draft \
    --title "监盘通 · 构建 ${GITHUB_RUN_NUMBER}" \
    --notes-file "$notes"
fi

main_sha="$(gh api "repos/${GH_REPO}/git/ref/heads/main" --jq '.object.sha')"
if [[ "$main_sha" == "$GITHUB_SHA" ]]; then
  gh release edit "$tag" --draft=false --latest
else
  gh release edit "$tag" --draft=false
fi

echo "Release: https://github.com/${GH_REPO}/releases/tag/${tag}" >> "$GITHUB_STEP_SUMMARY"
